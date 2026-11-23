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
import org.jetbrains.annotations.NotNull;

/**
 * IntelliJ action of the context menu of the "Threads" panel, "Terminate Thread", which terminates the selected threads
 * with the DAP 'terminateThreads' request, as VS Code's "Terminate Thread" of the Call Stack view does. It is shown only
 * when the DAP server of the threads' session supports the request (supportsTerminateThreadsRequest).
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
        boolean supported = selected != null && selected.client().isSupportsTerminateThreadsRequest();
        e.getPresentation().setVisible(supported);
        e.getPresentation().setEnabled(supported && !selected.threadIds().isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ThreadsPanel.SelectedThreads selected = e.getData(ThreadsPanel.SELECTED_THREADS);
        if (selected != null) {
            // Sent off the EDT, and the DAP server reports the end of each thread with a 'thread' event
            selected.client().terminateThreads(selected.threadIds());
        }
    }
}
