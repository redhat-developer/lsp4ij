/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Debug Adapter Protocol (DAP) execution stack.
 */
public class DAPExecutionStack extends XExecutionStack {

    private final @NotNull DAPClient client;
    private final @NotNull DAPSuspendContext suspendContext;
    private final int threadId;
    private @Nullable List<DAPStackFrame> stackFrames;
    // How many times the debug adapter said the frames are out of date: frames asked for before are not kept
    private int invalidations;

    public DAPExecutionStack(@NotNull DAPClient client,
                             @NotNull DAPSuspendContext suspendContext,
                             @NotNull Thread thread,
                             @Nullable StackFrame[] stackFrames) {
        super(getThreadName(thread));
        this.threadId = thread.getId();
        this.client = client;
        this.suspendContext = suspendContext;
        this.stackFrames = stackFrames != null ? toDAPStackFrames(stackFrames) : null;
    }

    private static @NlsContexts.ListItem String getThreadName(@NotNull Thread thread) {
        String name = thread.getName();
        return !StringUtils.isEmpty(name) ? name : "Thread #" + thread.getId();
    }

    @Nullable
    @Override
    public XStackFrame getTopFrame() {
        return ContainerUtil.getFirstItem(stackFrames);
    }

    @Override
    public void computeStackFrames(int firstFrameIndex,
                                   @NotNull XStackFrameContainer container) {
        if (firstFrameIndex == 0) {
            if (stackFrames != null) {
                // The DAP stack frames was previously loaded
                container.addStackFrames(stackFrames, true);
                suspendContext.setActiveExecutionStack(this);
            } else {
                // The DAP stack frames is not loaded, load it
                int invalidations = getInvalidations();
                StackTraceArguments stackTraceArgs = new StackTraceArguments();
                stackTraceArgs.setThreadId(getThreadId());
                client.getDebugProtocolServer()
                        .stackTrace(stackTraceArgs)
                        .thenAcceptAsync(stackTraceResponse -> {
                            StackFrame[] stackFrames = stackTraceResponse.getStackFrames();
                            List<DAPStackFrame> frames = stackFrames != null ? toDAPStackFrames(stackFrames) : Collections.emptyList();
                            setStackFrames(frames, invalidations);
                            container.addStackFrames(frames, true);
                        });
            }
        } else {
            container.addStackFrames(new LinkedList<>(), true);
        }
    }

    public int getThreadId() {
        return threadId;
    }

    /**
     * Loads this stack's frames again, as when the debug adapter says that they are out of date. Frames not loaded
     * yet, still loading, or that fail to load again, are loaded when they are shown.
     *
     * @return a future that completes once the frames are loaded
     */
    CompletableFuture<Void> reloadStackFrames() {
        var server = client.getDebugProtocolServer();
        int invalidations;
        synchronized (this) {
            invalidations = ++this.invalidations;
            if (stackFrames == null || server == null) {
                return CompletableFuture.completedFuture(null);
            }
        }
        StackTraceArguments stackTraceArgs = new StackTraceArguments();
        stackTraceArgs.setThreadId(getThreadId());
        return server.stackTrace(stackTraceArgs)
                .thenAccept(stackTraceResponse -> {
                    StackFrame[] frames = stackTraceResponse.getStackFrames();
                    setStackFrames(frames != null ? toDAPStackFrames(frames) : Collections.emptyList(), invalidations);
                })
                .whenComplete((result, error) -> {
                    if (error != null) {
                        // the frames are out of date: drop them rather than show them again
                        setStackFrames(null, invalidations);
                    }
                });
    }

    private synchronized int getInvalidations() {
        return invalidations;
    }

    /**
     * Sets the frames, or none for them to be loaded when they are shown, unless the debug adapter said that the frames
     * are out of date since they were asked for.
     */
    private synchronized void setStackFrames(@Nullable List<DAPStackFrame> frames, int invalidations) {
        if (this.invalidations == invalidations) {
            this.stackFrames = frames;
        }
    }
    
    private @NotNull List<DAPStackFrame> toDAPStackFrames(@NotNull StackFrame[] stackFrames) {
        return Arrays.stream(stackFrames)
                .map(stackFrame -> new DAPStackFrame(client, stackFrame))
                .toList();
    }
}
