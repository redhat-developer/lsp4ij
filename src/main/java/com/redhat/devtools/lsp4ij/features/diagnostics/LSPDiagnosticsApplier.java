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
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Service that applies LSP diagnostics reactively when the server sends publishDiagnostics.
 *
 * <p>This is the ONLY way diagnostics are applied in normal editor mode (not batch).
 * This avoids flicker when typing because diagnostics are always up-to-date.</p>
 */
@Service(Service.Level.PROJECT)
public final class LSPDiagnosticsApplier implements Disposable {

    public static volatile int GROUP_ID = -1;

    private final Project project;

    public LSPDiagnosticsApplier(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public static LSPDiagnosticsApplier getInstance(@NotNull Project project) {
        return project.getService(LSPDiagnosticsApplier.class);
    }

    public void scheduleRefresh(@NotNull VirtualFile file,
                                @Nullable Document document) {
        scheduleRefresh(file, null, document);
    }

    public void scheduleRefresh(@NotNull PsiFile file,
                                @NotNull Document document) {
        scheduleRefresh(null, file, document);
    }

    /**
     * Schedule a reactive refresh of diagnostics for the given file.
     * Called when the server sends publishDiagnostics notification.
     */
    private void scheduleRefresh(@Nullable VirtualFile file,
                                 @Nullable PsiFile psiFile,
                                 @Nullable Document document) {
        if (project.isDisposed() || GROUP_ID == -1) {
            return;
        }

        // Capture Document modification stamp as early as possible to detect changes
        Long modificationStamp = document != null ? document.getModificationStamp() : null;
        ReadAction
                .nonBlocking(() -> collectDiagnostics(file, psiFile, document, modificationStamp))
                .coalesceBy(this, file != null ? file : psiFile)
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
    private HighlightsToApply collectDiagnostics(@Nullable VirtualFile file,
                                                 @Nullable PsiFile psiFile,
                                                 @Nullable Document document,
                                                 @Nullable Long modificationStamp) {
        if (project.isDisposed() || GROUP_ID == -1) {
            return null;
        }

        if (file != null) {
            psiFile = LSPIJUtils.getPsiFile(file, project);
            if (psiFile == null) {
                return null;
            }
            document = LSPIJUtils.getDocument(psiFile);
            if (document == null) {
                return null;
            }
            // Capture stamp here since it wasn't provided
            if (modificationStamp == null) {
                modificationStamp = document.getModificationStamp();
            }
        }

        // Collect diagnostics
        LSPDiagnosticsCollector collector = new LSPDiagnosticsCollector(psiFile);
        List<HighlightInfo> highlights = collector.collect();
        if (highlights == null) {
            highlights = List.of();
        }

        return new HighlightsToApply(document, highlights, GROUP_ID, modificationStamp);
    }

    @SuppressWarnings("deprecation")
    private void applyHighlights(@NotNull HighlightsToApply data) {
        // Check if document was modified since we collected diagnostics
        // If modified, skip applying stale diagnostics to avoid flicker
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

    @Override
    public void dispose() {
        // Nothing to clean up
    }
}
