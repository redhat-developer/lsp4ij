/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.indexing;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

/**
 * Base class strategy to track project indexing state (start/end of dumb, files indexing).
 */
@ApiStatus.Internal
public abstract class ProjectIndexingStrategyBase {


    /**
     * On started scanning files.
     *
     * @param project the project.
     */
    protected final void onStartedScanning(@Nullable Project project) {
        if (project == null) {
            return;
        }
        ProjectIndexingManager.getInstance(project).scanning = true;
    }

    /**
     * On finished scanning files.
     * <p>
     * If all projects have finished their dumb and scanning files,
     * the language servers associated with the opened files of the project are started
     * and the opened editors are refreshed to display some LSP features like CodeLens, InlayHint, Folding.
     * </p>
     *
     * @param project the project.
     */
    protected final void onFinishedScanning(@Nullable Project project) {
        if (project == null) {
            return;
        }
        ProjectIndexingManager manager = ProjectIndexingManager.getInstance(project);
        manager.scanning = false;
        refreshEditorsFeaturesIfNeeded(manager);
    }

    /**
     * On started dumb indexing.
     *
     * @param project the project.
     */
    protected final void onStartedDumbIndexing(@Nullable Project project) {
        if (project == null) {
            return;
        }
        ProjectIndexingManager.getInstance(project).dumbIndexing = true;
    }

    /**
     * On finished dumb indexing.
     *
     * <p>
     * If all projects have finished their dumb and scanning files,
     * the language servers associated with the opened files of the project are started
     * and the opened editors are refreshed to display some LSP features like CodeLens, InlayHint, Folding.
     * </p>
     *
     * @param project the project.
     */
    protected final void onFinishedDumbIndexing(@Nullable Project project) {
        if (project == null) {
            return;
        }
        ProjectIndexingManager manager = ProjectIndexingManager.getInstance(project);
        manager.dumbIndexing = false;
        refreshEditorsFeaturesIfNeeded(manager);
    }

    private void refreshEditorsFeaturesIfNeeded(ProjectIndexingManager manager) {
        if (!manager.isIndexingAll()) {
            // All opened project are indexed,
            // refresh all editors which edit the files to refresh
            while (!manager.filesToRefresh.isEmpty()) {
                var files = new HashSet<>(manager.filesToRefresh);
                for (var file : files) {
                    var psiFile = LSPIJUtils.getPsiFile(file, manager.project);
                    // Try to send a textDocument/didOpen notification if the file is associated to the language server definition
                    LanguageServiceAccessor.getInstance(manager.project)
                            .getLanguageServers(psiFile, null, null)
                            .thenAccept(servers -> {
                                // textDocument/didOpen notification has been sent
                                if (servers.isEmpty()) {
                                    return;
                                }

                                // Refresh all features (code vision, inlay hints, folding,etc)
                                // of editors which edit the current file.
                                EditorFeatureManager.getInstance(manager.project)
                                        .refreshEditorFeature(psiFile, EditorFeatureType.ALL, true);

                            });
                }
                manager.filesToRefresh.removeAll(files);
            }
        }
    }
}
