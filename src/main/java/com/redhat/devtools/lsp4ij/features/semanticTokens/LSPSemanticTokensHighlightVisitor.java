/*******************************************************************************
 * Copyright (c) 2024-2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * FalsePattern - Performance improvements for huge files
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens;

import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;

/**
 * LSP 'textDocument/semanticTokens' support by implementing IntelliJ {@link HighlightVisitor}.
 *
 * <p>
 * Implementing {@link HighlightVisitor} gives the capability to have an existing highlighter
 * (custom highlighter, TextMate highlighter) and add semantic coloration.
 * </p>
 *
 * <p>
 * <b>Threading note:</b> {@link #analyze} is invoked inside a read action (via
 * {@code GeneralHighlightingPass} → {@code DumbService.runReadActionInSmartMode}).
 * We must not hold the read lock while blocking on the LSP future, otherwise any
 * pending write action (e.g. opening the Git Push dialog) will be blocked on the
 * EDT, causing a visible UI freeze.
 * <br>
 * We use {@link ProgressIndicatorUtils#awaitWithCheckCanceled} instead of the
 * previous {@code CompletableFutures.waitUntilDone}, because it cooperates with
 * IntelliJ's read/write lock protocol: it yields to write actions by throwing
 * {@link ProcessCanceledException} when the current progress indicator is
 * cancelled, allowing the framework to release the read lock and reschedule
 * the highlighting pass once the write action completes.
 * </p>
 */
@ApiStatus.Internal
public class LSPSemanticTokensHighlightVisitor implements HighlightVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPSemanticTokensHighlightVisitor.class);

    private @Nullable SemanticTokensData semanticTokens;

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
        return LanguageServersRegistry.getInstance().isFileSupported(file);
    }

    private HighlightInfoHolder holder;
    private LazyHighlightInfo[] lazyInfos;

    @Override
    public boolean analyze(@NotNull PsiFile file,
                           boolean updateWholeFile,
                           @NotNull HighlightInfoHolder holder,
                           @NotNull Runnable action) {
        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            return true;
        }
        try {
            this.lazyInfos = null;
            this.holder = null;
            this.semanticTokens = getSemanticTokens(file);
            if (semanticTokens != null) {
                if (!semanticTokens.shouldVisitPsiElement(file)) {
                    // The PsiFile must highlight without using HighlightVisitor#visit(PsiElement)
                    // e.g. TextMate or PlainText files.
                    highlightSemanticTokens(file, semanticTokens, holder);
                    this.lazyInfos = null;
                    this.holder = null;
                } else {
                    // The PsiFile is a custom PsiFile with proper tokenization of PsiElements;
                    // highlighting is driven by HighlightVisitor#visit(PsiElement).
                    this.lazyInfos = highlightSemanticTokens(file, semanticTokens, null);
                    this.holder = holder;
                }
            }
            action.run();
        } finally {
            this.holder = null;
            this.lazyInfos = null;
        }
        return true;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
        if (semanticTokens == null || lazyInfos == null || !semanticTokens.isEligibleForSemanticHighlighting(element)) {
            // The current PsiElement must not be highlighted with semantic tokens.
            return;
        }
        int start = element.getTextOffset();
        if (start < 0) {
            return;
        }
        int end = start + element.getTextLength();
        for (int i = start; i < end && i < lazyInfos.length; i++) {
            var info = lazyInfos[i];
            if (info != null) {
                holder.add(info.resolve(i));
                lazyInfos[i] = null;
            }
        }
    }

    /**
     * Fetches semantic tokens from the LSP server for the given file.
     *
     * <p>
     * <b>Why {@link ProgressIndicatorUtils#awaitWithCheckCanceled} and not
     * {@code CompletableFutures.waitUntilDone}?</b>
     * <br>
     * This method is called from {@link #analyze}, which runs inside a read action.
     * The old {@code waitUntilDone} polled {@code future.get(25ms)} in a tight loop
     * while holding the read lock for the entire duration. If the LSP server was slow
     * to respond, any write action (e.g. opening the Git Push dialog via
     * {@code EditorTextField.setText}) would be stuck waiting for the read lock on
     * the EDT, causing a UI freeze (see issue #1437).
     * <br>
     * {@code ProgressIndicatorUtils.awaitWithCheckCanceled} integrates with
     * IntelliJ's concurrency model: when a write action is requested, the current
     * progress indicator is cancelled, which causes this method to throw
     * {@link ProcessCanceledException}. The read lock is then released immediately,
     * the write action proceeds, and IntelliJ automatically reschedules the
     * highlighting pass once the write action is done.
     * </p>
     *
     * <p>
     * <b>File modification stamp:</b> The original {@code waitUntilDone(future, file)}
     * checked {@code file.getModificationStamp()} on each polling iteration and threw
     * {@link com.redhat.devtools.lsp4ij.internal.PsiFileChangedException} if the file
     * was edited while waiting. We replicate this check by capturing the stamp before
     * waiting and comparing it after — if it changed, the LSP response is stale and
     * we discard it. A {@link ProcessCanceledException} is thrown by
     * {@code awaitWithCheckCanceled} anyway when the user types (triggering a write
     * action), so in practice the stamp check is a belt-and-suspenders safety net.
     * </p>
     *
     * @param file the PSI file for which semantic tokens are requested
     * @return the semantic tokens data, or {@code null} if unavailable or stale
     * @throws ProcessCanceledException if the progress indicator is cancelled
     *                                  (e.g. a write action is pending); the caller
     *                                  (IntelliJ's highlighting framework) handles the
     *                                  reschedule automatically
     */
    private static @Nullable SemanticTokensData getSemanticTokens(@NotNull PsiFile file) {
        LSPSemanticTokensSupport semanticTokensSupport = LSPFileSupport.getSupport(file).getSemanticTokensSupport();
        var params = new SemanticTokensParams(new TextDocumentIdentifier());
        CompletableFuture<SemanticTokensData> semanticTokensFuture = semanticTokensSupport.getSemanticTokens(params);

        // Capture the modification stamp before waiting so we can detect file edits
        // that occurred while the LSP server was responding (see Javadoc above).
        final long modificationStampBefore = file.getModificationStamp();

        try {
            // Wait for the LSP future while cooperating with IntelliJ's lock model.
            //
            // Unlike the previous CompletableFutures.waitUntilDone(), this call does NOT
            // hold the read lock for the full duration of the wait. If a write action is
            // requested (e.g. user types, or the Git Push dialog tries to setText()),
            // the progress indicator is cancelled → ProcessCanceledException is thrown →
            // the read lock is released → the write action can proceed immediately.
            //
            // IntelliJ will reschedule the GeneralHighlightingPass (and therefore this
            // visitor) once the write action completes, so semantic tokens will still be
            // applied — just after a short delay.
            ProgressIndicatorUtils.awaitWithCheckCanceled(semanticTokensFuture);

        } catch (ProcessCanceledException e) {
            // A write action requested priority, or the pass was cancelled for another
            // reason (e.g. the editor was closed). Do NOT cancel the LSP future here:
            // the server response may arrive before the next rescheduled pass, in which
            // case it will be served from the support's internal cache at no extra cost.
            throw e; // propagate so the framework handles rescheduling

        } catch (CancellationException e) {
            // The CompletableFuture itself was cancelled (e.g. the file was closed,
            // or the language server shut down). Nothing to do — propagate as-is.
            throw e;

        }

        // Belt-and-suspenders: if the file was modified while we were waiting (e.g. a
        // paste that completed before the write action triggered cancellation), the
        // tokens are stale. Discard them; the next highlighting pass will request fresh
        // ones with the updated document content.
        if (file.getModificationStamp() != modificationStampBefore) {
            semanticTokensSupport.cancel();
            return null;
        }

        if (isDoneNormally(semanticTokensFuture)) {
            return semanticTokensFuture.getNow(null);
        }
        return null;
    }

    private static LazyHighlightInfo[] highlightSemanticTokens(@NotNull PsiFile file,
                                                               @NotNull SemanticTokensData semanticTokens,
                                                               @Nullable HighlightInfoHolder holder) {
        // textDocument/semanticTokens/full has been collected correctly;
        // create IntelliJ HighlightInfo entries from the LSP SemanticTokens data.
        var document = LSPIJUtils.getDocument(file.getVirtualFile());
        if (document == null) {
            return null;
        }
        if (holder != null) {
            semanticTokens.highlight(file, document, (start, end, colorKey) ->
                    holder.add(LazyHighlightInfo.resolve(start, end, colorKey)));
            return null;
        } else {
            var infos = new LazyHighlightInfo[document.getTextLength()];
            semanticTokens.highlight(file, document, (start, end, colorKey) ->
                    infos[start] = new LazyHighlightInfo(end, colorKey));
            return infos;
        }
    }

    @Override
    public @NotNull HighlightVisitor clone() {
        return new LSPSemanticTokensHighlightVisitor();
    }
}