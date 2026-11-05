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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IntelliJ action of the debug session's settings, "Step Only the Current Thread", which makes Step Over, Step Into,
 * Smart Step Into and Step Out resume only the thread that steps, while the other threads stay stopped, as the Java
 * debugger's "Resume only the current thread" does. The steps send DAP's 'singleThread', so the action is shown only
 * when the DAP server supports single thread execution requests.
 */
public class DAPStepOnlyCurrentThreadAction extends ToggleAction implements DumbAware {

    public static final @NotNull String ID = "DAP.StepOnlyCurrentThread";

    private static @Nullable DAPDebugProcess getDebugProcess(@NotNull AnActionEvent e) {
        XDebugSession session = e.getDataContext().getData(XDebugSession.DATA_KEY);
        if (session == null) {
            Project project = e.getProject();
            session = project != null ? XDebuggerManager.getInstance(project).getCurrentSession() : null;
        }
        return session != null && session.getDebugProcess() instanceof DAPDebugProcess dapDebugProcess ? dapDebugProcess : null;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        DAPDebugProcess debugProcess = getDebugProcess(e);
        e.getPresentation().setEnabledAndVisible(debugProcess != null && debugProcess.isSupportsSingleThreadExecutionRequests());
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        DAPDebugProcess debugProcess = getDebugProcess(e);
        return debugProcess != null && debugProcess.isStepOnlyCurrentThread();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        DAPDebugProcess debugProcess = getDebugProcess(e);
        if (debugProcess != null) {
            debugProcess.setStepOnlyCurrentThread(state);
        }
    }
}
