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
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.completion.LSPCompletionContributor;
import com.redhat.devtools.lsp4ij.internal.PsiFileChangedException;
import com.redhat.devtools.lsp4ij.internal.SafetyTimeoutException;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Collects LSP document links and converts them to IntelliJ {@link HighlightInfo}.
 *
 * Strategy:
 * - If document links are already cached (fast path): use immediately
 * - If not cached: wait up to waitTimeMs
 * - If still not ready: return null and optionally register callback via whenReady()
 */
public class LSPDocumentLinkCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDocumentLinkCollector.class);


    private final PsiFile psiFile;
    private final Document document;
    private CompletableFuture<List<DocumentLinkData>> pendingFuture;

    public LSPDocumentLinkCollector(@Nullable PsiFile psiFile,
                                     @Nullable Document document) {
        this.psiFile = psiFile;
        this.document = document;
    }

    @Nullable
    public List<HighlightInfo> collect() {
        // Check if we can execute LSP feature now
        if (ProjectIndexingManager.canExecuteLSPFeature(psiFile) != ExecuteLSPFeatureStatus.NOW) {
            return null;
        }

        // Consume LSP 'textDocument/documentLink' request
        LSPDocumentLinkSupport documentLinkSupport = LSPFileSupport.getSupport(psiFile).getDocumentLinkSupport();
        var params = new DocumentLinkParams(new TextDocumentIdentifier());
        CompletableFuture<List<DocumentLinkData>> documentLinkFuture = documentLinkSupport.getDocumentLinks(params);

        try {
            // Wait until the future is finished and stop the wait if there are some ProcessCanceledException.
            waitUntilDone(documentLinkFuture, psiFile);
        } catch (PsiFileChangedException e) {
            // The file content has changed, cancel the LSP textDocument/completion requests.
            documentLinkSupport.cancel();
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (SafetyTimeoutException e) {
            // Safety timeout triggered - save the future for async fallback via whenReady() callback
            this.pendingFuture = documentLinkFuture;
            return null;
        } catch (CancellationException ignore) {
            return null;
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/completion' request", e);
            return null;
        }

        // Fast path: if already completed (from cache), use immediately
        if (isDoneNormally(documentLinkFuture)) {
            List<DocumentLinkData> documentLinks = documentLinkFuture.getNow(null);
            return convertToHighlights(documentLinks);
        }

        return null;
    }

    /**
     * Register a callback to be called when the document links future completes.
     * Only works if collect() returned null due to timeout.
     */
    public void whenReady(@NotNull Runnable callback) {
        if (pendingFuture != null) {
            pendingFuture.whenComplete((result, error) -> {
                if (error == null && result != null) {
                    callback.run();
                }
            });
        }
    }

    @Nullable
    private List<HighlightInfo> convertToHighlights(@Nullable List<DocumentLinkData> documentLinks) {
        if (documentLinks == null || documentLinks.isEmpty() || document == null) {
            return null;
        }

        List<HighlightInfo> highlights = new ArrayList<>();

        for (var documentLink : documentLinks) {
            TextRange range = LSPIJUtils.toTextRange(documentLink.documentLink().getRange(), document);
            if (range != null) {
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
                        .range(range)
                        .textAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES)
                        .create();
                if (info != null) {
                    highlights.add(info);
                }
            }
        }

        return highlights.isEmpty() ? null : highlights;
    }
}
