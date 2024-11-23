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
package com.redhat.devtools.lsp4ij.client;

import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistory;
import com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener;
import com.intellij.util.indexing.diagnostic.ProjectScanningHistory;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * {@link ProjectIndexingActivityHistoryListener} implementation to track project indexing process (dum, scannings files, etc).
 */
@ApiStatus.Internal
public class ProjectIndexingListener implements ProjectIndexingActivityHistoryListener {

    @Override
    public void onStartedDumbIndexing(@NotNull ProjectDumbIndexingHistory history) {
        ProjectIndexingManager.getInstance(history.getProject()).dumbIndexing = true;
    }

    @Override
    public void onFinishedDumbIndexing(@NotNull ProjectDumbIndexingHistory history) {
        ProjectIndexingManager manager = ProjectIndexingManager.getInstance(history.getProject());
        manager.dumbIndexing = false;
        refreshEditorsFeaturesIfNeeded(manager);
    }

    @Override
    public void onStartedScanning(@NotNull ProjectScanningHistory history) {
        ProjectIndexingManager.getInstance(history.getProject()).scanning = true;
    }

    @Override
    public void onFinishedScanning(@NotNull ProjectScanningHistory history) {
        ProjectIndexingManager manager = ProjectIndexingManager.getInstance(history.getProject());
        manager.scanning = false;
        refreshEditorsFeaturesIfNeeded(manager);
    }

    private void refreshEditorsFeaturesIfNeeded(ProjectIndexingManager manager) {
        if (!manager.isIndexingAll()) {
            // All opened project are indexed,
            // refresh all editors which edit the files to refresh
            while (!manager.filesToRefresh.isEmpty()) {
                var files = new HashSet<>(manager.filesToRefresh);
                for (var file : files) {

                    // Try to send a textDocument/didOpen notification if the file is associated to the language server definition
                    LanguageServiceAccessor.getInstance(manager.project)
                            .getLanguageServers(file, null, null)
                            .thenAccept(servers -> {
                                // textDocument/didOpen notification has been sent
                                if (servers.isEmpty()) {
                                    return;
                                }

                                // Refresh all features (code vision, inlay hints, folding,etc)
                                // of editors which edit the current file.
                                EditorFeatureManager.getInstance(manager.project)
                                        .refreshEditorFeature(file, EditorFeatureType.ALL, true);

                            });
                }
                manager.filesToRefresh.removeAll(files);
            }
        }
    }
}
