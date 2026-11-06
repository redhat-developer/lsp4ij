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
     * Returns true if the files of a project are spread over several language server instances, and false if one
     * instance serves all of them.
     *
     * <p>{@link #getServerInstanceRoot} then decides which instance serves a file, and a file without a root gets
     * none. The workspace folders work for an instance as they work for a language server without instances, with
     * the folders that {@linkplain #belongsToServerInstance belong to it}. If none of them does and the folders are
     * sent at initialization, the instance gets its root.</p>
     *
     * <p>LSP4IJ asks the strategy before there is an instance, so it takes the strategy from client features that
     * have no language server. A strategy with instances must not need the language server: if getting it fails,
     * one language server serves the project, and the log says why.</p>
     *
     * @return true if the files of a project are spread over several language server instances
     */
    default boolean hasServerInstances() {
        return false;
    }

    /**
     * Returns the root of the language server instance that serves the given file, or null if no instance serves
     * it. Files with the same root share an instance. The root is the rootUri of the instance and gives it its name
     * in the Language Servers view.
     *
     * <p>By default, this is the workspace folder of the file, so each workspace folder gets an instance of its
     * own.</p>
     *
     * <p>This method is also asked about the directories of workspace folders, to tell which instance they belong
     * to. So a directory that is a root has to give itself, and not the root of its parent. With instances, the same
     * goes for {@link #getWorkspaceFolderForFile}, which is what this method asks by default.</p>
     *
     * @param file the file or directory
     * @param project the project
     * @param fileUriSupport file URI support for converting files to URIs
     * @return the root of the language server instance that serves the file, or null
     */
    @Nullable
    default WorkspaceFolder getServerInstanceRoot(@NotNull VirtualFile file,
                                                  @NotNull Project project,
                                                  @NotNull FileUriSupport fileUriSupport) {
        return getWorkspaceFolderForFile(file, project, fileUriSupport);
    }

    /**
     * Returns true if the given workspace folder belongs to the language server instance with the given root, which
     * means that the directory of the folder has that root. So a folder that contains several instances, or the
     * folder of a nested instance, doesn't belong to it.
     *
     * @param folder the workspace folder
     * @param instanceRoot the root of the language server instance
     * @param project the project
     * @param fileUriSupport file URI support for converting files to URIs
     * @return true if the workspace folder belongs to the language server instance
     */
    default boolean belongsToServerInstance(@NotNull WorkspaceFolder folder,
                                            @NotNull WorkspaceFolder instanceRoot,
                                            @NotNull Project project,
                                            @NotNull FileUriSupport fileUriSupport) {
        VirtualFile directory = FileUriSupport.findFileByUri(folder.getUri(), fileUriSupport);
        WorkspaceFolder root = directory != null ? getServerInstanceRoot(directory, project, fileUriSupport) : null;
        // One directory can be written as several URIs
        return root != null && (instanceRoot.getUri().equals(root.getUri())
                || FileUriSupport.normalize(instanceRoot.getUri(), fileUriSupport).equals(FileUriSupport.normalize(root.getUri(), fileUriSupport)));
    }

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
     * @param file the file, and with language server instances also a directory
     * @param project the project
     * @param fileUriSupport file URI support for converting files to URIs
     * @return the workspace folder containing the file, or null
     */
    @Nullable
    WorkspaceFolder getWorkspaceFolderForFile(@NotNull VirtualFile file,
                                              @NotNull Project project,
                                              @NotNull FileUriSupport fileUriSupport);
}
