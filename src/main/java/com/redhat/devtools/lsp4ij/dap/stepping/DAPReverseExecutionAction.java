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
package com.redhat.devtools.lsp4ij.dap.stepping;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IntelliJ action which runs the program backwards, as Step Back and Reverse Continue do, in a DAP debug session whose
 * debug adapter supports it (supportsStepBack), such as CodeLLDB and Delve on a recording of rr. IntelliJ has no
 * action of its own for it. The action is in the toolbar of the debug session, after the steps, and in the
 * Run | Debugging Actions menu: it is shown when the debug adapter supports it, which it may announce only once the
 * program is launched, and enabled while the session is suspended at a stop of such a debug adapter.
 */
public abstract class DAPReverseExecutionAction extends AnAction implements DumbAware {

    /**
     * Returns the event's debug session, or the project's current one, and null when there is none.
     */
    private static @Nullable XDebugSession getSession(@NotNull AnActionEvent e) {
        XDebugSession session = e.getDataContext().getData(XDebugSession.DATA_KEY);
        if (session != null) {
            return session;
        }
        Project project = e.getProject();
        return project != null ? XDebuggerManager.getInstance(project).getCurrentSession() : null;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        XDebugSession session = getSession(e);
        boolean visible = session != null
                && session.getDebugProcess() instanceof DAPDebugProcess debugProcess
                && debugProcess.isSupportsStepBack();
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(visible && DAPDebugProcess.canRunBackwards(session.getSuspendContext()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        XDebugSession session = getSession(e);
        if (session != null && session.getDebugProcess() instanceof DAPDebugProcess debugProcess) {
            runBackwards(debugProcess, session.getSuspendContext());
        }
    }

    /**
     * Runs the program backwards from the given suspend context.
     *
     * @param debugProcess the DAP debug process of the session.
     * @param context      the suspend context of the session, or null when it is not suspended.
     */
    protected abstract void runBackwards(@NotNull DAPDebugProcess debugProcess, @Nullable XSuspendContext context);
}
