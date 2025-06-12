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
import com.intellij.openapi.project.ProjectManager;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatureAware;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationStatus;
import com.redhat.devtools.lsp4ij.installation.ServerInstallerBase;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class to install a DAP server.
 */
public abstract class DebugAdapterServerInstallerBase extends ServerInstallerBase  {

    private final @Nullable DebugAdapterServerDefinition serverDefinition;

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

    @Override
    protected @Nullable Project getProject() {
        return null;
    }
}
