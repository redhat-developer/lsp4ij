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

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.Thread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * Debug Adapter Protocol (DAP) suspend context.
 */
public class DAPSuspendContext extends XSuspendContext {

    private final @NotNull DAPClient client;
    private final int executionEpoch;
    // The DAP thread which stopped, which the selection of another thread in the Frames view doesn't change
    private final @Nullable Integer stoppedThreadId;
    // The version of the run states of the threads when the program stopped: a thread which left the stop since then
    // has no frames of the stop to show
    private final int threadRunStatesVersion;
    private final List<DAPExecutionStack> myExecutionStacks = new LinkedList<>();
    private @Nullable DAPExecutionStack myActiveStack;

    public DAPSuspendContext(@NotNull DAPClient client) {
        this(client, client.getExecutionEpoch());
    }

    /**
     * @param client         the client of the debug adapter.
     * @param executionEpoch the execution epoch of the stop of the program which this context describes.
     */
    public DAPSuspendContext(@NotNull DAPClient client, int executionEpoch) {
        this(client, executionEpoch, null, client.getThreadRunStates().getVersion());
    }

    /**
     * @param client                 the client of the debug adapter.
     * @param executionEpoch         the execution epoch of the stop of the program which this context describes.
     * @param stoppedThreadId        the DAP thread which stopped, or null if it isn't known.
     * @param threadRunStatesVersion the version of the run states of the threads of the client when the program
     *                               stopped: a thread which left the stop since then shows no frames.
     */
    DAPSuspendContext(@NotNull DAPClient client,
                      int executionEpoch,
                      @Nullable Integer stoppedThreadId,
                      int threadRunStatesVersion) {
        this.client = client;
        this.executionEpoch = executionEpoch;
        this.stoppedThreadId = stoppedThreadId;
        this.threadRunStatesVersion = threadRunStatesVersion;
    }

    public void addToExecutionStack(@NotNull Thread thread, @Nullable StackFrame[] stackFrames) {
        // Convert DAP stack frames to IJ stack frames
        DAPExecutionStack stack = new DAPExecutionStack(client, this, thread, stackFrames);
        myExecutionStacks.add(stack);
        if (stackFrames != null) {
            setActiveExecutionStack(stack);
        }
    }

    @Nullable
    @Override
    public DAPExecutionStack getActiveExecutionStack() {
        return myActiveStack;
    }

    void setActiveExecutionStack(DAPExecutionStack stack) {
        myActiveStack = stack;
    }

    @NotNull
    @Override
    public XExecutionStack @NotNull [] getExecutionStacks() {
        return myExecutionStacks.toArray(new DAPExecutionStack[0]);
    }

    public @NotNull DAPClient getClient() {
        return client;
    }

    /**
     * Returns the execution epoch of the stop of the program which this context describes.
     *
     * @return the execution epoch of the stop of the program which this context describes.
     */
    public int getExecutionEpoch() {
        return executionEpoch;
    }

    public Integer getThreadId() {
        return myActiveStack != null ? myActiveStack.getThreadId() : null;
    }

    /**
     * Returns the DAP thread which stopped at this suspend context. Unlike {@link #getThreadId()}, the thread of the
     * active stack, it doesn't change when the Frames view selects another thread.
     *
     * @return the DAP thread which stopped at this suspend context, or null if it isn't known.
     */
    @Nullable Integer getStoppedThreadId() {
        return stoppedThreadId;
    }

    /**
     * Returns the version of the run states of the threads of the client when the program stopped, at this suspend
     * context: a thread which left the stop since then, as when it continued alone, shows no frames.
     *
     * @return the version of the run states of the threads when the program stopped.
     * @see DAPThreadRunStates#leftStopSince(int, int)
     */
    int getThreadRunStatesVersion() {
        return threadRunStatesVersion;
    }
}
