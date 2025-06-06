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
package com.redhat.devtools.lsp4ij.installation;

import com.intellij.openapi.progress.ProgressIndicator;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for declarative language server installers in IntelliJ.
 * <p>
 * This class provides a framework for installing language servers based on
 * a {@code serverInstallerDescriptor}, typically declared in a {@code installer.json}
 * file associated with a {@link LanguageServerDefinition}.
 * </p>
 *
 * <p>
 * It supports both checking and executing installation steps via a {@link ServerInstallerManager}
 * using an {@link InstallerContext}. Subclasses must provide a concrete implementation of
 * {@link #getServerInstallerDescriptor()} to specify how the installer descriptor is resolved.
 * </p>
 *
 * <p>
 * If no installer descriptor is available, or if the descriptor is not meant to be executed,
 * the language server is considered already installed.
 * </p>
 *
 * @see LanguageServerInstallerBase
 * @see ServerInstallerDescriptor
 * @see ServerInstallerManager
 */
public abstract class DeclarativeLanguageServerInstaller extends LanguageServerInstallerBase {

    /**
     * Constructs a new declarative language server installer with no initial definition.
     */
    public DeclarativeLanguageServerInstaller() {
        this(null);
    }

    /**
     * Constructs a new declarative language server installer for the given definition.
     *
     * @param serverDefinition the language server definition
     */
    public DeclarativeLanguageServerInstaller(@Nullable LanguageServerDefinition serverDefinition) {
        super(serverDefinition);
    }

    /**
     * Checks whether the language server installation is needed or already completed.
     * <p>
     * If a {@link ServerInstallerDescriptor} is provided and marked as executable,
     * defers to {@link #execute(CompletableFuture)} to proceed with installation checks.
     * </p>
     *
     * @return a {@link CompletableFuture} indicating the installation status
     */
    @Override
    public @NotNull CompletableFuture<ServerInstallationStatus> checkInstallation() {
        ServerInstallerDescriptor serverInstallerDescriptor = getServerInstallerDescriptor();
        if (serverInstallerDescriptor == null) {
            // The user defined language server doesn't define an installer.json
            // we consider that installation is done
            return INSTALLED_FUTURE;
        }
        if (!canExecute(serverInstallerDescriptor)) {
            // The installer cannot be executed
            // we consider that installation is done
            return INSTALLED_FUTURE;
        }
        return execute(super.checkInstallation());
    }

    /**
     * Hook method to optionally wrap or modify the given future for installation checking.
     * Subclasses may override to add additional logic (e.g., notifications, logging).
     *
     * @param checkInstallationFuture the future returned by the superclass
     * @return the modified or original future
     */
    protected @NotNull CompletableFuture<ServerInstallationStatus> execute(@NotNull CompletableFuture<ServerInstallationStatus> checkInstallationFuture) {
        return checkInstallationFuture;
    }

    /**
     * Executes a declarative check for whether the language server is already installed.
     * <p>
     * This uses the {@link ServerInstallerManager} in CHECK mode and disables user notifications.
     * </p>
     *
     * @param indicator the IntelliJ progress indicator
     * @return {@code true} if the server is already installed; {@code false} otherwise
     * @throws Exception if an error occurs during the check
     */
    @Override
    protected boolean checkServerInstalled(@NotNull ProgressIndicator indicator) throws Exception {
        ServerInstallerDescriptor serverInstallerDescriptor = getServerInstallerDescriptor();
        if (serverInstallerDescriptor == null) {
            return true;
        }
        var context = createInstallerContext(InstallerContext.InstallerAction.CHECK, indicator);
        context.setShowNotification(false);
        return ServerInstallerManager.getInstance().install(serverInstallerDescriptor, context);
    }

    /**
     * Determines whether the installer should be executed on language server startup.
     *
     * @param serverInstallerDescriptor the installer descriptor to inspect
     * @return {@code true} if executable; {@code false} otherwise
     */
    protected boolean canExecute(@NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        return serverInstallerDescriptor.isExecuteOnStartServer();
    }

    /**
     * Runs the declarative installation process using the {@link ServerInstallerManager}
     * in RUN mode.
     *
     * @param indicator the IntelliJ progress indicator
     * @throws Exception if the installation fails
     */
    @Override
    protected void install(@NotNull ProgressIndicator indicator) throws Exception {
        ServerInstallerDescriptor serverInstallerDescriptor = getServerInstallerDescriptor();
        if (serverInstallerDescriptor == null) {
            return;
        }
        var context = createInstallerContext(InstallerContext.InstallerAction.RUN, indicator);
        ServerInstallerManager.getInstance().install(serverInstallerDescriptor, context);
    }

    /**
     * Creates an {@link InstallerContext} for the given action and attaches
     * the provided {@link ProgressIndicator}.
     *
     * @param action    the action to be performed (CHECK or RUN)
     * @param indicator the progress indicator to report progress
     * @return a fully initialized {@link InstallerContext}
     */
    protected @NotNull InstallerContext createInstallerContext(@NotNull InstallerContext.InstallerAction action,
                                                               @NotNull ProgressIndicator indicator) {
        var context = new InstallerContext(getProject(), action);
        context.setProgressIndicator(indicator);
        return context;
    }

    /**
     * Returns the installer descriptor used to drive the declarative installation process.
     * <p>
     * Subclasses must provide this to define how the {@code installer.json} is resolved.
     * </p>
     *
     * @return the installer descriptor, or {@code null} if none is available
     */
    protected abstract @Nullable ServerInstallerDescriptor getServerInstallerDescriptor();
}
