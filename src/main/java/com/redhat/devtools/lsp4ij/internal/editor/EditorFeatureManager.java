/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * FalsePattern - added refreshEditorFeatureWhenAllDone
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Editor feature manager used to refresh IntelliJ visible feature of editors from a given project:
 *
 * <ul>
 *     <li>refresh 'Code Visions' from a given editor.</li>
 *     <li>refresh 'Inlay Hints'  from a given editor.</li>
 *     <li>refresh 'Folding' from a given editor.</li>
 * </ul>
 * <p>
 * As IntelliJ doesn't provide some API to refresh those visible feature, we need to use Java reflection.
 */
@ApiStatus.Internal
public class EditorFeatureManager implements Disposable {

    record RefreshEditorFeatureContext(@NotNull PsiFile file, @NotNull List<Runnable> runnables) {}

    private final @NotNull Project project;

    private final Map<EditorFeatureType, EditorFeature> editorFeatures;

    private EditorFeatureManager(@NotNull Project project) {
        this.project = project;
        this.editorFeatures = new LinkedHashMap<>();
        addEditorFeature(new CodeVisionEditorFeature());
        addEditorFeature(new FoldingEditorFeature());
        addEditorFeature(new InlayHintsEditorFeature());
        addEditorFeature(new DeclarativeInlayHintsEditorFeature());
    }

    private void addEditorFeature(EditorFeature editorFeature) {
        editorFeatures.put(editorFeature.getFeatureType(), editorFeature);
    }

    public static EditorFeatureManager getInstance(@NotNull Project project) {
        return project.getService(EditorFeatureManager.class);
    }

    /**
     * Refresh IntelliJ editor feature (code visions, inlay hints, folding, etc).
     *
     * @param file          the file opened in one or several editors.
     * @param featureType   the feature type to refresh (code visions, inlay hints, folding, etc, or all)
     * @param clearLSPCache true if LSP feature data cache (ex : LSP CodeLens) must be evicted and false otherwise.
     */
    public void refreshEditorFeature(@NotNull VirtualFile file,
                                     @NotNull EditorFeatureType featureType,
                                     boolean clearLSPCache) {
        ReadAction.nonBlocking((Callable<RefreshEditorFeatureContext>) () -> {
                    // Get the Psi file.
                    PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
                    if (psiFile == null) {
                        return null;
                    }

                    // Get opened editors used to display the content of the file.
                    Editor[] editors = LSPIJUtils.editorsForFile(file, project);
                    if (editors.length == 0) {
                        return null;
                    }

                    if (clearLSPCache) {
                        // Clear LSP cache
                        clearLSPCache(psiFile, featureType);
                    }

                    final List<Runnable> runnables = new ArrayList<>();
                    for (Editor editor : editors) {
                        // Clear editor cache according the feature type
                        // (IntelliJ stores generally the modification time stamp of the Psi file to avoid refreshing the feature if Psi file doesn't change)
                        clearEditorCache(editor, project, featureType);
                        // Collect runnable which must be executed on UI step.
                        collectUiRunnables(psiFile, editor, featureType, runnables);
                    }
                    return new RefreshEditorFeatureContext(psiFile, runnables);
                })
                .coalesceBy(file, featureType, clearLSPCache)
                .finishOnUiThread(ModalityState.any(), context -> {
                    if (context == null) {
                        // No opened editors associated from any language servers.
                        return;
                    }
                    DaemonCodeAnalyzer.getInstance(project).restart(context.file());
                    for (var runnable : context.runnables()) {
                        runnable.run();
                    }
                }).submit(AppExecutorUtil.getAppExecutorService());
    }

    /**
     * Waits for all futures in the provided list to finish, then refreshes the given editor feature if the file has not
     *  been modified since.
     * @param pendingFutures    a list of futures to wait for
     * @param modificationStamp the initial file modification timestamp
     * @param psiFile           the file to check the timestamp of
     * @param feature           the editor feature to refresh
     */
    public void refreshEditorFeatureWhenAllDone(@NotNull List<CompletableFuture<?>> pendingFutures,
                                                       long modificationStamp,
                                                       @NotNull PsiFile psiFile,
                                                       @NotNull EditorFeatureType feature) {
        CompletableFuture.allOf(pendingFutures.toArray(new CompletableFuture[0]))
                .thenApplyAsync(_unused -> {
                    // Check if PsiFile was not modified
                    if (modificationStamp == psiFile.getModificationStamp()) {
                        // All pending futures are finished, refresh the feature
                        refreshEditorFeature(psiFile.getVirtualFile(), feature, false);
                    }
                    return null;
                });
    }

    private void clearEditorCache(@NotNull Editor editor, @NotNull Project project, @NotNull EditorFeatureType featureType) {
        if (featureType == EditorFeatureType.ALL) {
            editorFeatures.values().forEach(feature -> feature.clearEditorCache(editor, project));
        } else {
            getEditorFeature(featureType).clearEditorCache(editor, project);
        }
    }

    private void clearLSPCache(PsiFile psiFile, @NotNull EditorFeatureType featureType) {
        if (featureType == EditorFeatureType.ALL) {
            editorFeatures.values().forEach(feature -> feature.clearLSPCache(psiFile));
        } else {
            getEditorFeature(featureType).clearLSPCache(psiFile);
        }
    }


    private void collectUiRunnables(@NotNull PsiFile psiFile,
                                    @NotNull Editor editor,
                                    @NotNull EditorFeatureType featureType,
                                    @NotNull List<Runnable> runnables) {
        if (featureType == EditorFeatureType.ALL) {
            editorFeatures.values().forEach(feature -> feature.collectUiRunnable(editor, psiFile, runnables));
        } else {
            getEditorFeature(featureType).collectUiRunnable(editor, psiFile, runnables);
        }
    }

    private EditorFeature getEditorFeature(@NotNull EditorFeatureType featureType) {
        return editorFeatures.get(featureType);
    }

    @Override
    public void dispose() {
        // Do nothing
    }
}
