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
package com.redhat.devtools.lsp4ij.dap.client.variables;

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
 * IntelliJ action of the Variables and Watches views, "Show Values as Hexadecimal", which asks the DAP server of the
 * debug session for numbers in hexadecimal. It is shown only when the DAP server supports value formatting.
 */
public class DAPHexadecimalValuesAction extends ToggleAction implements DumbAware {

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
        e.getPresentation().setEnabledAndVisible(debugProcess != null && debugProcess.isSupportsValueFormattingOptions());
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        DAPDebugProcess debugProcess = getDebugProcess(e);
        return debugProcess != null && debugProcess.getValueFormat().isHexadecimal();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        DAPDebugProcess debugProcess = getDebugProcess(e);
        if (debugProcess != null) {
            debugProcess.getValueFormat().setHexadecimal(state);
            // Ask for the values again, in the new format
            debugProcess.getSession().rebuildViews();
        }
    }
}
