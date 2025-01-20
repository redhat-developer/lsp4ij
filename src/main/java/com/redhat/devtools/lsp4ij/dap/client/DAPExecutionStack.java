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
import org.eclipse.lsp4j.debug.Thread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * Debug Adapter Protocol (DAP) execution stack.
 */
public class DAPExecutionStack extends XExecutionStack {

    private final @NotNull DAPClient client;
    private final @NotNull DAPSuspendContext suspendContext;
    private final @NotNull List<DAPStackFrame> stackFrames;
    private final int threadId;

    public DAPExecutionStack(@NotNull DAPClient client,
                             @NotNull  DAPSuspendContext suspendContext,
                             @NotNull Thread thread,
                             @NotNull List<DAPStackFrame> stackFrames) {
        super(getThreadName(thread));
        this.threadId = thread.getId();
        this.client = client;
        this.suspendContext = suspendContext;
        this.stackFrames = stackFrames;
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
    public void computeStackFrames(int firstFrameIndex, @NotNull XStackFrameContainer container) {
        if (firstFrameIndex == 0) {
            container.addStackFrames(stackFrames, true);
        } else {
            container.addStackFrames(new LinkedList<>(), true);
        }
        suspendContext.setActiveExecutionStack(this);
    }

    public int getThreadId() {
        return threadId;
    }
}
