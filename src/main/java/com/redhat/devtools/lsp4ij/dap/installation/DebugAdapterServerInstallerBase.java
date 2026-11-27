/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.installation;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.installation.ServerInstallerBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * Base class to install a DAP server.
 */
public abstract class DebugAdapterServerInstallerBase extends ServerInstallerBase  {

    private final @Nullable DebugAdapterServerDefinition serverDefinition;
    // The project of the run configuration which last started this installer, kept weakly: the installer belongs to
    // the debug adapter, for all projects, and must not keep a closed project
    private volatile @NotNull WeakReference<Project> project = new WeakReference<>(null);

    public DebugAdapterServerInstallerBase() {
        this(null);
    }

    public DebugAdapterServerInstallerBase(@Nullable DebugAdapterServerDefinition serverDefinition) {
        this.serverDefinition = serverDefinition;
    }

    protected @Nullable DebugAdapterServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    /**
     * Gets the title of the installation task.
     *
     * @return the title for the installation task.
     */
    protected String getInstallationTaskTitle() {
        return LanguageServerBundle.message("server.installer.lsp.task.installing", getServerName());
    }

    /**
     * Displays progress while checking if the server is installed.
     *
     * @param indicator the progress indicator to update with progress.
     */
    protected void progressCheckingServerInstalled(@NotNull ProgressIndicator indicator) {
        progress(LanguageServerBundle.message("server.installer.lsp.progress.check.installed", getServerName()), 0.1d, indicator);
    }

    /**
     * Displays progress during the installation process.
     *
     * @param indicator the progress indicator to update with progress.
     */
    protected void progressInstallingServer(@NotNull ProgressIndicator indicator) {
        progress(LanguageServerBundle.message("server.installer.lsp.progress.installing", getServerName()), 0.2d, indicator);
    }

    /**
     * Returns the language server name.
     *
     * @return the language server name.
     */
    protected @NotNull String getServerName() {
        var serverDefinition = getServerDefinition();
        return serverDefinition != null ? serverDefinition.getDisplayName() : "";
    }

    /**
     * Sets the project of the run configuration or debug session for which this installer checks or installs the debug
     * adapter: the installation then runs for that project, and the commands of its {@code exec} tasks without a
     * {@code workingDir} run in the directory of the project, as those of the installer of a language server, so that
     * they get the tools which the project selects, as with mise, asdf or nvm. The installer keeps the project weakly.
     *
     * @param project the project of the run configuration or debug session, or null for none.
     */
    public void setProject(@Nullable Project project) {
        this.project = new WeakReference<>(project);
    }

    /**
     * Returns the project which {@link #setProject(Project)} set last, unless it is closed, or null.
     *
     * @return the project which {@link #setProject(Project)} set last, unless it is closed, or null.
     */
    @Override
    protected @Nullable Project getProject() {
        Project project = this.project.get();
        return project != null && !project.isDisposed() ? project : null;
    }
}
