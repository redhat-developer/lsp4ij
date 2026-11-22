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
package com.redhat.devtools.lsp4ij.dap.settings.ui;

import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Command line updater of the "Installer" tab of a debug session of a user defined debug adapter.
 * <p>
 * {@code ${server.command}} is the command which the debug session starts the debug adapter with: the one of its run
 * configuration, else the one of the debug adapter. {@code configureServer} updates the command of the debug adapter,
 * as the installer which runs when a debug session starts does, and the one of the run configuration, which that
 * installation copies from the debug adapter.
 */
public class DebugSessionCommandLineUpdater extends UICommandLineUpdater {

    private final @NotNull DAPCommandLineState dapState;

    public DebugSessionCommandLineUpdater(@NotNull UserDefinedDebugAdapterServerDefinition server,
                                          @NotNull DAPCommandLineState dapState) {
        super(server);
        this.dapState = dapState;
    }

    @Override
    public String getCommandLine() {
        String commandLine = dapState.getCommandLine();
        return StringUtils.isNotBlank(commandLine) ? commandLine : super.getCommandLine();
    }

    @Override
    public void setCommandLine(String commandLine) {
        super.setCommandLine(commandLine);
        dapState.setCommandLine(commandLine);
    }
}
