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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Project indexing manager is used to track the indexing project process and start language servers
 * only when this indexing process is finished to take care of good performances when IntelliJ is started
 * with previous opened editors.
 */
public class ProjectIndexingManager implements Disposable {


    private record RefreshFeatures(Set<String> features, CompletableFuture<?> indexingFinished) {
    }

    final @NotNull Project project;
    boolean dumbIndexing;
    boolean scanning;
    final Set<VirtualFile> filesToRefresh;

    private static CompletableFuture<Void> waitForIndexingAllFuture;

    ProjectIndexingManager(@NotNull Project project) {
        this.project = project;
        this.filesToRefresh = new CopyOnWriteArraySet<>();
    }

    public static ProjectIndexingManager getInstance(@NotNull Project project) {
        return project.getService(ProjectIndexingManager.class);
    }

    public static boolean isIndexing(Project project) {
        return getInstance(project).isIndexing();
    }

    public boolean isIndexing() {
        return scanning || dumbIndexing || DumbService.isDumb(project);
    }

    /**
     * Returns true if all project are indexing and false otherwise.
     *
     * @return true if all project are indexing and false otherwise.
     */
    public static boolean isIndexingAll() {
        for (var project : ProjectManager.getInstance().getOpenProjects()) {
            if (isIndexing(project)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void dispose() {
        if (waitForIndexingAllFuture != null) {
            waitForIndexingAllFuture.cancel(true);
        }
    }

    public static CompletableFuture<Void> waitForIndexingAll() {
        if (shouldInitialize()) {
            return initialize();
        }
        return waitForIndexingAllFuture;
    }

    private static boolean shouldInitialize() {
        return waitForIndexingAllFuture == null || waitForIndexingAllFuture.isDone();
    }

    private synchronized static CompletableFuture<Void> initialize() {
        if (!shouldInitialize()) {
            return waitForIndexingAllFuture;
        }
        waitForIndexingAllFuture = CompletableFutures.computeAsync(cancelChecker -> {
            while (isIndexingAll()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                cancelChecker.checkCanceled();
            }
            return null;
        });
        return waitForIndexingAllFuture;
    }

    /**
     * Returns the execute LSP feature status for the given Psi file.
     *
     * @param file the Psi file.
     * @return the execute LSP feature status for the given Psi file.
     */
    public static ExecuteLSPFeatureStatus canExecuteLSPFeature(@Nullable PsiFile file) {
        if (file == null || !LanguageServersRegistry.getInstance().isFileSupported(file)) {
            // The file is not associated to a language server, don't execute the LSP feature.
            return ExecuteLSPFeatureStatus.NOT;
        }
        return doCanExecuteLSPFeature(file.getVirtualFile(), file.getProject());
    }

    /**
     * Returns the execute LSP feature status for the given virtual file and project.
     *
     * @param file    the virtual file.
     * @param project the project.
     * @return the execute LSP feature status for the given virtual file and project.
     */
    public static ExecuteLSPFeatureStatus canExecuteLSPFeature(@Nullable VirtualFile file,
                                                               @NotNull Project project) {
        if (!LanguageServersRegistry.getInstance().isFileSupported(file, project)) {
            // The file is not associated to a language server, don't execute the LSP feature.
            return ExecuteLSPFeatureStatus.NOT;
        }
        return doCanExecuteLSPFeature(file, project);
    }

    private static @NotNull ExecuteLSPFeatureStatus doCanExecuteLSPFeature(@Nullable VirtualFile file,
                                                                           @NotNull Project project) {
        ProjectIndexingManager manager = getInstance(project);
        if (manager.isIndexingAll()) {
            // The file is associated to a language server, but the project is indexing
            // Execute the LSP feature when the project indexing process is finished.
            manager.filesToRefresh.add(file);
            return ExecuteLSPFeatureStatus.AFTER_INDEXING;
        }
        // The file is associated to a language server, execute the LSP feature now.
        return ExecuteLSPFeatureStatus.NOW;
    }
}
