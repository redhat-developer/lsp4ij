package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import org.eclipse.lsp4j.debug.Thread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public class DAPExecutionStack extends XExecutionStack {

    private final @NotNull DAPClient client;
    private final @NotNull DAPSuspendContext suspendContext;
    private final @NotNull List<DAPStackFrame> stackFrames;
    private final int threadId;

    public DAPExecutionStack(@NotNull DAPClient client,
                             @NotNull  DAPSuspendContext suspendContext,
                             @NotNull Thread thread,
                             @NotNull List<DAPStackFrame> stackFrames) {
        super(thread.getName());
        this.threadId = thread.getId();
        this.client = client;
        this.suspendContext = suspendContext;
        this.stackFrames = stackFrames;
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
