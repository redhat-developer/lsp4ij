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
    private final int stopNumber;
    private final List<DAPExecutionStack> myExecutionStacks = new LinkedList<>();
    private @Nullable DAPExecutionStack myActiveStack;

    public DAPSuspendContext(@NotNull DAPClient client) {
        this(client, client.getLastStopNumber());
    }

    /**
     * @param client     the client of the debug adapter.
     * @param stopNumber the number of the stop of the program which this context describes.
     */
    public DAPSuspendContext(@NotNull DAPClient client, int stopNumber) {
        this.client = client;
        this.stopNumber = stopNumber;
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
     * Returns the number of the stop of the program which this context describes.
     *
     * @return the number of the stop of the program which this context describes.
     */
    public int getStopNumber() {
        return stopNumber;
    }

    public Integer getThreadId() {
        return myActiveStack != null ? myActiveStack.getThreadId() : null;
    }
}