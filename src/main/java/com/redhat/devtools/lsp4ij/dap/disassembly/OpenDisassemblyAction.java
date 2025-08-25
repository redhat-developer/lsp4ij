/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Angelo Zerr - implementation of DAP disassembly support
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.disassembly;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IntelliJ action that adds the menu entry <b>"Open Disassembly View"</b> to the debugger UI.
 * <p>
 * This action appears in the context menu when a user selects a stack frame and the connected
 * DAP (Debug Adapter Protocol) server supports disassembly instructions.
 * When executed, it opens the disassembly view in a read-only editor tab, allowing
 * the user to inspect machine-level instructions for the selected frame.
 */
public class OpenDisassemblyAction extends AnAction {

    /**
     * Retrieves the {@link DisassemblyFile} associated with the current debug session, if available.
     * <p>
     * This method checks whether the active debug session is based on a {@link DAPDebugProcess}
     * that provides a disassembly file for the current stack frame.
     *
     * @param e the action event containing the current data context
     * @return the disassembly file if supported by the current DAP server, otherwise {@code null}
     */
    private static @Nullable DisassemblyFile getDisassemblyFile(AnActionEvent e) {
        var session = e.getDataContext().getData(XDebugSession.DATA_KEY);
        if (session != null && session.getDebugProcess() instanceof DAPDebugProcess dapDebugProcess) {
            return dapDebugProcess.getDisassemblyFile();
        }
        return null;
    }

    /**
     * Updates the visibility and enabled state of this action.
     * <p>
     * The action will only be visible and enabled when:
     * <ul>
     *     <li>The user has selected a stack frame in the debugger UI.</li>
     *     <li>The current debug process is based on a DAP server that supports disassembly.</li>
     * </ul>
     *
     * @param e the action event containing the current data context
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        // Show the "Open Disassembly View" menu only if a valid disassembly file is available.
        e.getPresentation().setEnabledAndVisible(getDisassemblyFile(e) != null);
    }

    /**
     * Invoked when the action is triggered by the user.
     * <p>
     * Opens the disassembly file in a standard IntelliJ editor tab in read-only mode, allowing
     * the user to inspect low-level instructions for the currently selected stack frame.
     *
     * @param e the action event containing the current project and debug session context
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        DisassemblyFile file = getDisassemblyFile(e);
        if (file == null) {
            return;
        }
        // The selected frame corresponds to a DAP frame from a server that supports disassembly.
        // Open the disassembly file in a standard editor in read-only mode
        // to display the disassembly instructions.
        FileEditorManager.getInstance(project).openFile(file, true);
    }

    /**
     * Specifies that this action's update and execution logic should run on the Event Dispatch Thread (EDT),
     * as it interacts directly with the IntelliJ UI components.
     *
     * @return {@link ActionUpdateThread#EDT}
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
