/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.definitions.userdefined;

import com.intellij.openapi.progress.ProgressIndicator;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.installation.DeclarativeDebugAdapterServerInstaller;
import com.redhat.devtools.lsp4ij.dap.settings.ui.UICommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter server installer specifically for user-defined DAP servers.
 * <p>
 * This implementation extends {@link DeclarativeDebugAdapterServerInstaller}. The settings of a user-defined DAP
 * server don't store whether its installation was done, so its installer checks the installation on the first debug
 * run of each IDE session, and installs it only if the check fails.
 * </p>
 *
 * <p>
 * It also configures the {@link InstallerContext} to include a {@link com.redhat.devtools.lsp4ij.dap.settings.ui.UICommandLineUpdater}
 * based on the user-defined DAP server settings.
 * </p>
 */
public class UserDefinedDebugAdapterServerInstaller extends DeclarativeDebugAdapterServerInstaller {

    /**
     * Constructs a new installer for the specified user-defined DAP server definition.
     *
     * @param serverDefinition the user-defined server definition
     */
    public UserDefinedDebugAdapterServerInstaller(@Nullable DebugAdapterServerDefinition serverDefinition) {
        super(serverDefinition);
    }

    /**
     * Returns the installer descriptor for the current user-defined DAP server.
     *
     * @return the installer descriptor, or {@code null} if not available
     */
    @Override
    protected @Nullable ServerInstallerDescriptor getServerInstallerDescriptor() {
        var serverDefinition = getUserDefinedLanguageServerDefinition();
        return serverDefinition != null ? serverDefinition.getServerInstallerDescriptor() : null;
    }

    /**
     * Creates an installer context and attaches a UI-based command-line updater.
     *
     * @param action    the installer action (CHECK or RUN)
     * @param indicator the progress indicator
     * @return the customized installer context
     */
    @Override
    protected @NotNull InstallerContext createInstallerContext(InstallerContext.@NotNull InstallerAction action,
                                                               @NotNull ProgressIndicator indicator) {
        var context = super.createInstallerContext(action, indicator);
        var serverDefinition = getUserDefinedLanguageServerDefinition();
        if (serverDefinition != null) {
            context.setCommandLineUpdater(new UICommandLineUpdater(serverDefinition));
        }
        return context;
    }

    /**
     * Gets the user-defined DAP server definition, if applicable.
     *
     * @return the {@link UserDefinedDebugAdapterServerDefinition}, or {@code null} if not applicable
     */
    private @Nullable UserDefinedDebugAdapterServerDefinition getUserDefinedLanguageServerDefinition() {
        var serverDefinition = getServerDefinition();
        if (serverDefinition instanceof UserDefinedDebugAdapterServerDefinition ls) {
            return ls;
        }
        return null;
    }

    /**
     * Returns true: the installer runs even if the descriptor says not to, as its settings don't store whether the
     * installation was done.
     *
     * @param serverInstallerDescriptor the installer descriptor
     * @return {@code true}
     */
    @Override
    protected boolean canExecute(@NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        return true;
    }
}
