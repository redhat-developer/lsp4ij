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
package com.redhat.devtools.lsp4ij.dap.settings.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import org.jetbrains.annotations.NotNull;

/**
 * DAP command line updater.
 */
public class DebugCommandLineUpdater implements CommandLineUpdater {

    private final @NotNull DebugAdapterServerPanel debugAdapterServerPanel;

    public DebugCommandLineUpdater(@NotNull DebugAdapterServerPanel debugAdapterServerPanel) {
        this.debugAdapterServerPanel = debugAdapterServerPanel;;
    }

    @Override
    public String getCommandLine() {
        return debugAdapterServerPanel.getCommandLine();
    }

    @Override
    public void setCommandLine(String commandLine) {
        ApplicationManager.getApplication().invokeLater(() -> debugAdapterServerPanel.getCommandLineWidget().setText(commandLine));
    }
}
