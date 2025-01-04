package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.Thread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class DAPSuspendContext extends XSuspendContext  {

    private final @NotNull DAPClient client;
    private final List<DAPExecutionStack> myExecutionStacks = new LinkedList<>();
    private DAPExecutionStack myActiveStack;

    public DAPSuspendContext(@NotNull DAPClient client) {
        this.client = client;
    }

    public void addToExecutionStack(Thread thread, StackFrame[] stackFrames) {
        List<DAPStackFrame> dapStackFrames = Arrays.stream(stackFrames)
                .map(stackFrame -> new DAPStackFrame(client, stackFrame))
                .toList();
        DAPExecutionStack stack = new DAPExecutionStack(client, this, thread,
                dapStackFrames);
        myExecutionStacks.add(stack);
        myActiveStack = stack;
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
    public XExecutionStack[] getExecutionStacks() {
        return myExecutionStacks.toArray(new DAPExecutionStack[0]);
    }

    public @NotNull DAPClient getClient() {
        return client;
    }

    public Integer getThreadId() {
        return myActiveStack != null ? myActiveStack.getThreadId() :  null;
    }
}