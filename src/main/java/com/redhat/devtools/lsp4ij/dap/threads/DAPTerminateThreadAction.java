/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.threads;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * IntelliJ action "Terminate Thread", which terminates threads with the DAP 'terminateThreads' request, as VS Code's
 * "Terminate Thread" of the Call Stack view does. It is shown only when the DAP server of the threads' session supports
 * the request (supportsTerminateThreadsRequest). It terminates:
 * <ul>
 *     <li>in the context menu of the "Threads" panel, the selected threads;</li>
 *     <li>in the context menu of IntelliJ's Frames view, the thread of the selected frame.</li>
 * </ul>
 */
public class DAPTerminateThreadAction extends AnAction implements DumbAware {

    public static final @NotNull String ID = "DAP.TerminateThread";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        ThreadsPanel.SelectedThreads selected = e.getData(ThreadsPanel.SELECTED_THREADS);
        if (selected == null) {
            DAPStackFrame frame = getSelectedFrame(e);
            e.getPresentation().setEnabledAndVisible(frame != null
                    && frame.getThreadId() != null
                    && frame.getClient().isSupportsTerminateThreadsRequest());
            return;
        }
        boolean supported = selected.client().isSupportsTerminateThreadsRequest();
        e.getPresentation().setVisible(supported);
        e.getPresentation().setEnabled(supported && !selected.threadIds().isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // Sent off the EDT, to the session of the threads, and the DAP server reports the end of each thread with a
        // 'thread' event
        ThreadsPanel.SelectedThreads selected = e.getData(ThreadsPanel.SELECTED_THREADS);
        if (selected != null) {
            selected.client().terminateThreads(selected.threadIds());
            return;
        }
        DAPStackFrame frame = getSelectedFrame(e);
        Integer threadId = frame != null ? frame.getThreadId() : null;
        if (threadId != null) {
            frame.getClient().terminateThreads(List.of(threadId));
        }
    }

    /**
     * Returns the frame selected in IntelliJ's Frames view, when it is a DAP frame: the current frame of the event's
     * debug session, or of the project's current one. The Frames view gives its actions no data of the frame. A
     * right-click selects the frame under the mouse, and the Frames view makes the selected frame the session's current
     * frame, which the IntelliJ actions of its context menu, such as "Reset Frame", act on too. The split debugger
     * (2026.x) gives a backend action no session, but tells the backend session the frame that its Frames view selects,
     * so the project's current session is used then.
     *
     * @param e the action event.
     * @return the current frame of the debug session if it is a DAP frame, and null otherwise.
     */
    private static @Nullable DAPStackFrame getSelectedFrame(@NotNull AnActionEvent e) {
        XDebugSession session = e.getDataContext().getData(XDebugSession.DATA_KEY);
        if (session == null) {
            Project project = e.getProject();
            session = project != null ? XDebuggerManager.getInstance(project).getCurrentSession() : null;
        }
        return session != null && session.getCurrentStackFrame() instanceof DAPStackFrame frame ? frame : null;
    }
}
