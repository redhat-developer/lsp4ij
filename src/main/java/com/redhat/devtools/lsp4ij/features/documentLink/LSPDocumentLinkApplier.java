/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentLink;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Service that applies LSP document links when they become available.
 *
 * <p>Called by {@link LSPDocumentLinkPass} when document links took too long (>500ms)
 * and are not ready yet. This applier will apply them when the future completes.</p>
 */
@Service(Service.Level.PROJECT)
public final class LSPDocumentLinkApplier implements Disposable {

    public static volatile int GROUP_ID = -1;

    private final Project project;

    public LSPDocumentLinkApplier(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public static LSPDocumentLinkApplier getInstance(@NotNull Project project) {
        return project.getService(LSPDocumentLinkApplier.class);
    }

    /**
     * Schedule a refresh of document links for the given file.
     * Called when document links future completes after the pass timed out.
     */
    public void scheduleRefresh(@NotNull PsiFile file,
                                @NotNull Document document) {
        if (project.isDisposed() || GROUP_ID == -1) {
            return;
        }

        // Capture document modification stamp as early as possible
        Long modificationStamp = document.getModificationStamp();
        ReadAction
                .nonBlocking(() -> collectDocumentLinks(file, document, modificationStamp))
                .coalesceBy(this, file)
                .finishOnUiThread(ModalityState.defaultModalityState(), highlights -> {
                    if (highlights != null && !project.isDisposed()) {
                        applyHighlights(highlights);
                    }
                })
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private static class HighlightsToApply {
        final Document document;
        final List<HighlightInfo> highlights;
        final int groupId;
        final long modificationStamp;

        HighlightsToApply(@NotNull Document document,
                          @NotNull List<HighlightInfo> highlights,
                          int groupId,
                          long modificationStamp) {
            this.document = document;
            this.highlights = highlights;
            this.groupId = groupId;
            this.modificationStamp = modificationStamp;
        }
    }

    @Nullable
    private HighlightsToApply collectDocumentLinks(@NotNull PsiFile psiFile,
                                                   @NotNull Document document,
                                                   long modificationStamp) {
        if (project.isDisposed() || GROUP_ID == -1) {
            return null;
        }

        // Collect document links
        LSPDocumentLinkCollector collector = new LSPDocumentLinkCollector(psiFile, document);
        List<HighlightInfo> highlights = collector.collect();
        if (highlights == null) {
            highlights = List.of();
        }

        return new HighlightsToApply(document, highlights, GROUP_ID, modificationStamp);
    }

    @SuppressWarnings("deprecation")
    private void applyHighlights(@NotNull HighlightsToApply data) {
        // Check if document was modified since we collected document links
        // If modified, skip applying stale document links to avoid flicker
        if (data.document.getModificationStamp() != data.modificationStamp) {
            return;
        }

        UpdateHighlightersUtil.setHighlightersToEditor(
                project,
                data.document,
                0,
                data.document.getTextLength(),
                data.highlights,
                null,
                data.groupId
        );
    }

    @Nullable
    private Editor getActiveEditorForFile(@NotNull VirtualFile file) {
        FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
        for (FileEditor fileEditor : editors) {
            if (fileEditor instanceof TextEditor textEditor) {
                return textEditor.getEditor();
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }
}
