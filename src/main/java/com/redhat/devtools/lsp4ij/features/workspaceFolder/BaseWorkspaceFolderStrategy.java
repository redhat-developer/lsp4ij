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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Base workspace folder strategy with configurable root type, lazy loading, and markers.
 */
public abstract class BaseWorkspaceFolderStrategy implements WorkspaceFolderStrategy {

    protected RootType rootType = RootType.PROJECT_BASE;
    protected boolean lazy = false;
    protected List<String> markers = null;

    protected void setLazy(boolean lazy) {
        this.lazy = lazy;
    }

    protected void setRootType(@NotNull RootType rootType) {
        this.rootType = rootType;
    }

    protected void setMarkers(@Nullable List<String> markers) {
        this.markers = markers;
    }

    public boolean isLazy() {
        return lazy;
    }

    @Nullable
    public List<String> getMarkers() {
        return markers;
    }

    @NotNull
    public RootType getRootType() {
        return rootType;
    }

    /**
     * Returns the roots based on the configured root type.
     * Can be overridden for custom root discovery logic.
     *
     * @param project the project
     * @return the list of root directories
     */
    @NotNull
    public List<VirtualFile> getRoots(@NotNull Project project) {
        return switch (rootType) {
            case SOURCE_ROOTS -> getModuleSourceRoots(project);
            case PROJECT_BASE -> new ArrayList<>(BaseProjectDirectories.Companion.getBaseDirectories(project));
            default -> Collections.emptyList();
        };
    }

    /**
     * Returns all module source roots in the project.
     *
     * @param project the project
     * @return the list of source roots
     */
    @NotNull
    public List<VirtualFile> getModuleSourceRoots(@NotNull Project project) {
        List<VirtualFile> roots = new ArrayList<>();
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (Module module : moduleManager.getModules()) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            roots.addAll(Arrays.asList(rootManager.getSourceRoots()));
        }
        return roots;
    }

    @Override
    public final boolean sendAllFoldersOnInitialization() {
        return !isLazy() && (getMarkers() == null || getMarkers().isEmpty());
    }

    @NotNull
    @Override
    public List<WorkspaceFolder> getWorkspaceFolders(@NotNull Project project,
                                                     @NotNull FileUriSupport fileUriSupport) {
        List<VirtualFile> roots = getRoots(project);
        return toWorkspaceFolders(roots, fileUriSupport);
    }

    @NotNull
    @Override
    public List<WorkspaceFolder> getInitialWorkspaceFolders(@NotNull Project project,
                                                            @NotNull FileUriSupport fileUriSupport) {
        if (!sendAllFoldersOnInitialization()) {
            return Collections.emptyList();
        }
        return getWorkspaceFolders(project, fileUriSupport);
    }

    @Nullable
    @Override
    public WorkspaceFolder getWorkspaceFolderForFile(@NotNull VirtualFile file,
                                                     @NotNull Project project,
                                                     @NotNull FileUriSupport fileUriSupport) {
        // Only use NIO Path optimization for local files
        boolean isLocal = file.isInLocalFileSystem();
        List<String> markers = getMarkers();

        if (markers != null && !markers.isEmpty()) {
            if (isLocal) {
                // Marker-based discovery: walk up the directory tree looking for marker files
                // Use NIO Path for better performance instead of VirtualFile.findChild()
                Path filePath = Paths.get(file.getPath());
                Path parentPath = filePath.getParent();

                while (parentPath != null) {
                    for (String marker : markers) {
                        Path markerPath = parentPath.resolve(marker);
                        if (Files.exists(markerPath)) {
                            // Found a marker, this directory is a workspace folder
                            // Convert back to VirtualFile for creating workspace folder
                            VirtualFile parentVFile = LocalFileSystem.getInstance()
                                    .findFileByPath(parentPath.toString());
                            if (parentVFile != null) {
                                return createWorkspaceFolder(parentVFile, fileUriSupport);
                            }
                        }
                    }
                    parentPath = parentPath.getParent();
                }
            } else {
                // Fallback to VirtualFile API for non-local files
                VirtualFile parent = file.getParent();
                while (parent != null) {
                    for (String marker : markers) {
                        VirtualFile markerFile = parent.findChild(marker);
                        if (markerFile != null) {
                            return createWorkspaceFolder(parent, fileUriSupport);
                        }
                    }
                    parent = parent.getParent();
                }
            }
        } else {
            // Find the closest root containing this file
            List<VirtualFile> rootsList = getRoots(project);

            if (isLocal) {
                // Use NIO Path for better performance with startsWith() instead of walking up
                Path filePath = Paths.get(file.getPath()).normalize();

                // Find the deepest (most specific) root that contains this file
                VirtualFile bestMatch = null;
                int maxDepth = -1;

                for (VirtualFile root : rootsList) {
                    if (root.isInLocalFileSystem()) {
                        Path rootPath = Paths.get(root.getPath()).normalize();
                        if (filePath.startsWith(rootPath)) {
                            int depth = rootPath.getNameCount();
                            if (depth > maxDepth) {
                                maxDepth = depth;
                                bestMatch = root;
                            }
                        }
                    }
                }

                if (bestMatch != null) {
                    return createWorkspaceFolder(bestMatch, fileUriSupport);
                }
            } else {
                // Fallback to VirtualFile API for non-local files
                // Convert to Set for O(1) lookup
                Set<VirtualFile> roots = new HashSet<>(rootsList);
                VirtualFile parent = file.getParent();
                while (parent != null) {
                    if (roots.contains(parent)) {
                        return createWorkspaceFolder(parent, fileUriSupport);
                    }
                    parent = parent.getParent();
                }
            }
        }

        return null;
    }

    @Nullable
    protected WorkspaceFolder createWorkspaceFolder(@NotNull VirtualFile file,
                                                    @NotNull FileUriSupport fileUriSupport) {
        String workspaceUri = FileUriSupport.toString(file, fileUriSupport);
        if (workspaceUri != null) {
            WorkspaceFolder folder = new WorkspaceFolder();
            folder.setUri(workspaceUri);
            folder.setName(file.getName());
            return folder;
        }
        return null;
    }

    @NotNull
    protected List<WorkspaceFolder> toWorkspaceFolders(@NotNull List<VirtualFile> roots,
                                                       @NotNull FileUriSupport fileUriSupport) {
        List<WorkspaceFolder> workspaceFolders = new ArrayList<>(roots.size());
        for (VirtualFile root : roots) {
            WorkspaceFolder folder = createWorkspaceFolder(root, fileUriSupport);
            if (folder != null) {
                workspaceFolders.add(folder);
            }
        }
        return workspaceFolders;
    }
}
