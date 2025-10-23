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

import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterServerListener;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import org.jetbrains.annotations.NotNull;

/**
 * UI command line updater.
 */
public class UICommandLineUpdater implements CommandLineUpdater {

    private final @NotNull UserDefinedDebugAdapterServerDefinition createdServer;

    public UICommandLineUpdater(@NotNull UserDefinedDebugAdapterServerDefinition createdServer) {
        this.createdServer = createdServer;
    }

    @Override
    public String getCommandLine() {
        return createdServer.getCommandLine();
    }

    @Override
    public void setCommandLine(String commandLine) {
        createdServer.setCommandLine(commandLine);

        // Update command line settings
        String serverId = createdServer.getId();
        var settings = UserDefinedDebugAdapterServerSettings.getInstance().getSettings(serverId);
        if (settings != null) {
            settings.setCommandLine(commandLine);
            UserDefinedDebugAdapterServerSettings.getInstance().setSettings(serverId, settings);
        }

        // Notifications
        DebugAdapterServerListener.ChangedEvent event = new DebugAdapterServerListener.ChangedEvent(
                createdServer,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
        DebugAdapterManager.getInstance().handleChangeEvent(event);
    }
}
