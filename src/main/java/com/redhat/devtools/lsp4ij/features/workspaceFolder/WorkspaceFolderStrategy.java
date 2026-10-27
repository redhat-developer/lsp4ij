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
package com.redhat.devtools.lsp4ij.features.workspaceFolder;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Strategy for determining workspace folders for a language server.
 *
 * <p>Implementations of this interface define how workspace folders are discovered
 * and reported to the language server. Different strategies can be used for different
 * language servers or project setups.</p>
 *
 * <p>Implementations can be registered via the extension point:
 * {@code com.redhat.devtools.lsp4ij.workspaceFolderStrategy}</p>
 */
@ApiStatus.Experimental
public interface WorkspaceFolderStrategy {

    ExtensionPointName<WorkspaceFolderStrategyProvider> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.workspaceFolderStrategy");

    /**
     * Returns the default workspace folder strategy (project-based).
     *
     * @return the default workspace folder strategy
     */
    @NotNull
    static WorkspaceFolderStrategy getDefault() {
        return new ProjectWorkspaceFolderStrategy();
    }

    /**
     * Returns true if this strategy sends all workspace folders during initialization,
     * false if folders are sent dynamically as files are opened.
     *
     * @return true if all folders are sent at initialization
     */
    boolean sendAllFoldersOnInitialization();

    /**
     * Returns the list of workspace folders for the given project.
     * This represents all potential workspace folders, regardless of lazy loading.
     * Used for UI display.
     *
     * @param project the project
     * @param fileUriSupport file URI support for converting files to URIs
     * @return the list of workspace folders
     */
    @NotNull
    List<WorkspaceFolder> getWorkspaceFolders(@NotNull Project project,
                                              @NotNull FileUriSupport fileUriSupport);

    /**
     * Returns the list of workspace folders to send during initialization.
     * In lazy mode, this returns an empty list.
     *
     * @param project the project
     * @param fileUriSupport file URI support for converting files to URIs
     * @return the list of workspace folders for initialization
     */
    @NotNull
    default List<WorkspaceFolder> getInitialWorkspaceFolders(@NotNull Project project,
                                                             @NotNull FileUriSupport fileUriSupport) {
        if (!sendAllFoldersOnInitialization()) {
            return Collections.emptyList();
        }
        return getWorkspaceFolders(project, fileUriSupport);
    }

    /**
     * Returns the workspace folder for the given file, or null if the file
     * is not part of any workspace folder.
     *
     * @param file the file
     * @param project the project
     * @param fileUriSupport file URI support for converting files to URIs
     * @return the workspace folder containing the file, or null
     */
    @Nullable
    WorkspaceFolder getWorkspaceFolderForFile(@NotNull VirtualFile file,
                                              @NotNull Project project,
                                              @NotNull FileUriSupport fileUriSupport);
}
