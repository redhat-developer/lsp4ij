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
import com.redhat.devtools.lsp4ij.installation.ServerInstallationStatus;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Debug Adapter server installer specifically for user-defined DAP servers.
 * <p>
 * This implementation extends {@link DeclarativeDebugAdapterServerInstaller}
 * and provides additional logic for remembering whether installation
 * has already been performed, in order to prevent re-installation
 * on subsequent startups.
 * </p>
 *
 * <p>
 * It also configures the {@link InstallerContext} to include a {@link com.redhat.devtools.lsp4ij.dap.settings.ui.UICommandLineUpdater}
 * based on the user-defined DAP server settings.
 * </p>
 */
public class UserDefinedDebugAdapterServerInstaller extends DeclarativeDebugAdapterServerInstaller {

    private boolean initialized;

    /**
     * Constructs a new installer for the specified user-defined DAP server definition.
     *
     * @param serverDefinition the user-defined server definition
     */
    public UserDefinedDebugAdapterServerInstaller(@Nullable DebugAdapterServerDefinition serverDefinition) {
        super(serverDefinition);
        this.initialized = false;
    }

    /**
     * Executes the installation check and ensures settings are updated once.
     *
     * @param checkInstallationFuture future representing the status of the check
     * @return the same future passed as input
     */
    @Override
    protected @NotNull CompletableFuture<ServerInstallationStatus> execute(@NotNull CompletableFuture<ServerInstallationStatus> checkInstallationFuture) {
        if (!initialized) {
            initialize(checkInstallationFuture);
        }
        return checkInstallationFuture;
    }

    /**
     * Registers a one-time completion handler that sets {@code installAlreadyDone = true}
     * in the user's configuration once the installation check completes.
     *
     * @param checkInstallationFuture the future to attach the handler to
     */
    private synchronized void initialize(@NotNull CompletableFuture<ServerInstallationStatus> checkInstallationFuture) {
        if (initialized) {
            return;
        }
        checkInstallationFuture.handle((result, error) -> {
            var settings = getSettings();
            if (settings != null) {
                settings.setInstallAlreadyDone(true);
            }
            return result;
        });
        initialized = true;
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
     * @return the {@link UserDefinedLanguageServerDefinition}, or {@code null} if not applicable
     */
    private @Nullable UserDefinedDebugAdapterServerDefinition getUserDefinedLanguageServerDefinition() {
        var serverDefinition = getServerDefinition();
        if (serverDefinition instanceof UserDefinedDebugAdapterServerDefinition ls) {
            return ls;
        }
        return null;
    }

    /**
     * Checks if installation should be executed, even if the descriptor says not to.
     * <p>
     * If {@link UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings#isInstallAlreadyDone()} is false,
     * installation will proceed regardless of the descriptor's settings.
     * </p>
     *
     * @param serverInstallerDescriptor the installer descriptor
     * @return {@code true} if installation should proceed
     */
    @Override
    protected boolean canExecute(@NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        if (super.canExecute(serverInstallerDescriptor)) {
            return true;
        }
        return true;
    }

    /**
     * Retrieves the user's stored settings for the current user-defined DAP server.
     *
     * @return the item settings or {@code null} if not found
     */
    private UserDefinedLanguageServerSettings.@Nullable UserDefinedLanguageServerItemSettings getSettings() {
        var serverDefinition = getUserDefinedLanguageServerDefinition();
        if (serverDefinition == null) {
            return null;
        }
        String languageServerId = serverDefinition.getId();
        return UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings(languageServerId);
    }
}
