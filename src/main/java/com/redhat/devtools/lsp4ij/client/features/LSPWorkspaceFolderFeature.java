/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ConfigurableWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ProjectWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workspace folder feature for LSP.
 *
 * <p>This feature manages how workspace folders are discovered and sent to the language server.
 * By default, it uses the project base directories. You can customize this by providing a JSON
 * configuration to use content roots and/or marker-based detection.</p>
 */
@ApiStatus.Experimental
public class LSPWorkspaceFolderFeature {

    private LSPClientFeatures clientFeatures;

    protected WorkspaceFolderStrategy strategy;

    // Thread-safe set for tracking notified workspace folders
    private final Set<String> notifiedWorkspaceFolders = ConcurrentHashMap.newKeySet();

    /**
     * Sets the containing client features.
     *
     * @param clientFeatures the client features to associate with this workspace folder feature
     */
    void setClientFeatures(@NotNull LSPClientFeatures clientFeatures) {
        this.clientFeatures = clientFeatures;
    }

    /**
     * Returns the containing client features.
     *
     * @return the associated {@link LSPClientFeatures}
     */
    @NotNull
    protected LSPClientFeatures getClientFeatures() {
        return clientFeatures;
    }

    /**
     * Returns the workspace folder strategy.
     *
     * @return the workspace folder strategy
     */
    @NotNull
    public WorkspaceFolderStrategy getStrategy() {
        if (strategy == null) {
            strategy = createStrategy();
        }
        return strategy;
    }

    /**
     * Creates the default workspace folder strategy.
     * Override this method to provide a different default strategy.
     *
     * @return the default workspace folder strategy
     */
    @NotNull
    protected WorkspaceFolderStrategy createStrategy() {
        return new ProjectWorkspaceFolderStrategy();
    }

    /**
     * Returns the initial workspace folders to send during initialization.
     *
     * @param project the project
     * @return the list of workspace folders
     */
    @NotNull
    public List<WorkspaceFolder> getInitialWorkspaceFolders(@NotNull Project project) {
        List<WorkspaceFolder> folders = getStrategy().getInitialWorkspaceFolders(project, getClientFeatures());

        // Track folders that were sent during initialization
        for (WorkspaceFolder folder : folders) {
            notifiedWorkspaceFolders.add(folder.getUri());
        }

        return folders;
    }

    /**
     * Computes the workspace folder to notify for the given file without marking it as notified.
     * This method performs I/O operations and should be called outside synchronized blocks.
     *
     * @param file the file being opened
     * @return the workspace folder to notify, or null if not needed
     */
    @Nullable
    public WorkspaceFolder computeWorkspaceFolderToNotify(@NotNull VirtualFile file) {
        if (getStrategy().sendAllFoldersOnInitialization()) {
            // Strategy sends all folders at initialization, no dynamic notification needed
            return null;
        }

        Project project = getClientFeatures().getProject();
        WorkspaceFolder folder = getStrategy().getWorkspaceFolderForFile(file, project, getClientFeatures());

        if (folder != null && !notifiedWorkspaceFolders.contains(folder.getUri())) {
            return folder;
        }

        return null;
    }

    /**
     * Marks a workspace folder as notified.
     * This is a fast operation that should be called inside synchronized blocks.
     *
     * @param folder the workspace folder to mark as notified
     * @return true if the folder was newly added, false if it was already notified
     */
    public boolean markFolderAsNotified(@NotNull WorkspaceFolder folder) {
        return notifiedWorkspaceFolders.add(folder.getUri());
    }

    /**
     * Clears the tracking of notified workspace folders.
     * This should be called when the language server is restarted.
     */
    public void reset() {
        notifiedWorkspaceFolders.clear();
    }
}
