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
 *
 * <p>
 * <b>Stale token protection — two layers:</b>
 * <ol>
 *   <li><b>Stamp check (primary):</b> {@link #getSemanticTokens} captures the file's
 *       modification stamp before awaiting the LSP future and returns {@code null} if
 *       the stamp changed while waiting. The stamp is then threaded through to
 *       {@link #highlightSemanticTokens}, which re-checks it immediately before
 *       applying any highlights. If the document changed in the narrow window between
 *       those two points the entire highlighting pass is cancelled — no stale token
 *       is ever applied.</li>
 *   <li><b>Defensive guards (safety net):</b> {@link SemanticTokensData#highlight} and
 *       {@link LazyHighlightInfo#resolve} both validate that computed offsets are
 *       within the current document bounds before creating a
 *       {@link com.intellij.codeInsight.daemon.impl.HighlightInfo}.
 *       This covers the rare race where a write action lands in the narrow window
 *       between the two stamp checks (e.g. between the document-length snapshot in
 *       {@code SemanticTokensData} and the array allocation in
 *       {@code highlightSemanticTokens}).</li>
 * </ol>
 * In both cases IntelliJ will reschedule the highlighting pass and the next pass
 * will produce correct results.
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
            this.semanticTokens = null;

            SemanticTokensResult result = getSemanticTokens(file);
            if (result != null) {
                this.semanticTokens = result.data();
                if (!result.data().shouldVisitPsiElement(file)) {
                    // The PsiFile must highlight without using HighlightVisitor#visit(PsiElement)
                    // e.g. TextMate or PlainText files.
                    highlightSemanticTokens(file, result, holder);
                    this.lazyInfos = null;
                    this.holder = null;
                } else {
                    // The PsiFile is a custom PsiFile with proper tokenization of PsiElements;
                    // highlighting is driven by HighlightVisitor#visit(PsiElement).
                    this.lazyInfos = highlightSemanticTokens(file, result, null);
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
                // resolve() returns null when the stored end offset is invalid (defensive
                // guard for the rare race described in the class Javadoc). Skip silently.
                var highlight = info.resolve(i);
                if (highlight != null) {
                    holder.add(highlight);
                }
                lazyInfos[i] = null;
            }
        }
    }

    /**
     * Pairs {@link SemanticTokensData} with the file modification stamp captured at
     * fetch time. Threading the stamp through to {@link #highlightSemanticTokens}
     * allows a second staleness check right before highlights are applied, without
     * an extra {@code getModificationStamp()} call.
     */
    private record SemanticTokensResult(@NotNull SemanticTokensData data, long modificationStamp) {}

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
     * progress indicator is cancelled → ProcessCanceledException is thrown →
     * the read lock is released → the write action can proceed immediately.
     * <br>
     * IntelliJ will reschedule the GeneralHighlightingPass (and therefore this
     * visitor) once the write action completes, so semantic tokens will still be
     * applied — just after a short delay.
     * </p>
     *
     * @param file the PSI file for which semantic tokens are requested
     * @return the semantic tokens data paired with the modification stamp at fetch
     *         time, or {@code null} if unavailable or stale
     * @throws ProcessCanceledException if the progress indicator is cancelled
     *                                  (e.g. a write action is pending); the caller
     *                                  (IntelliJ's highlighting framework) handles
     *                                  the reschedule automatically
     */
    private static @Nullable SemanticTokensResult getSemanticTokens(@NotNull PsiFile file) {
        LSPSemanticTokensSupport semanticTokensSupport = LSPFileSupport.getSupport(file).getSemanticTokensSupport();
        var params = new SemanticTokensParams(new TextDocumentIdentifier());
        CompletableFuture<SemanticTokensData> semanticTokensFuture = semanticTokensSupport.getSemanticTokens(params);

        // Capture the modification stamp before waiting so we can detect file edits
        // that occurred while the LSP server was responding.
        final long modificationStampBefore = file.getModificationStamp();

        try {
            // Wait for the LSP future while cooperating with IntelliJ's lock model.
            // See class Javadoc for a detailed explanation of why this is safe.
            ProgressIndicatorUtils.awaitWithCheckCanceled(semanticTokensFuture);

        } catch (ProcessCanceledException e) {
            // A write action requested priority, or the pass was cancelled for another
            // reason (e.g. the editor was closed). Do NOT cancel the LSP future here:
            // the server response may arrive before the next rescheduled pass, in which
            // case it will be served from the support's internal cache at no extra cost.
            throw e;

        } catch (CancellationException e) {
            // The CompletableFuture itself was cancelled (e.g. the file was closed,
            // or the language server shut down). Nothing to do — propagate as-is.
            throw e;
        }

        // Primary staleness check: if the file was modified while we were waiting,
        // the tokens no longer match the document. Discard them and let the next
        // highlighting pass request fresh ones.
        if (file.getModificationStamp() != modificationStampBefore) {
            semanticTokensSupport.cancel();
            return null;
        }

        if (isDoneNormally(semanticTokensFuture)) {
            SemanticTokensData data = semanticTokensFuture.getNow(null);
            if (data != null) {
                // Return the data together with the stamp so highlightSemanticTokens()
                // can perform its own staleness check before applying any highlights.
                return new SemanticTokensResult(data, modificationStampBefore);
            }
        }
        return null;
    }

    /**
     * Applies semantic token highlights to the given file.
     *
     * <p>The modification stamp carried by {@code result} is re-checked immediately
     * before any highlight is created. If the file was edited in the narrow window
     * between {@link #getSemanticTokens} returning and this method being called, the
     * tokens are already stale and the entire pass is cancelled — no partial or
     * incorrect highlights are applied. The defensive guards in
     * {@link SemanticTokensData#highlight} and {@link LazyHighlightInfo#resolve} cover
     * the even narrower race where a write action arrives mid-highlight.</p>
     *
     * @param file   the PSI file
     * @param result the tokens + stamp returned by {@link #getSemanticTokens}
     * @param holder the highlight holder, or {@code null} for the lazy (PSI-driven) path
     * @return a {@link LazyHighlightInfo} array indexed by start offset (lazy path),
     *         or {@code null} (direct path or stale tokens)
     */
    private static @Nullable LazyHighlightInfo[] highlightSemanticTokens(@NotNull PsiFile file,
                                                                         @NotNull SemanticTokensResult result,
                                                                         @Nullable HighlightInfoHolder holder) {
        var document = LSPIJUtils.getDocument(file.getVirtualFile());
        if (document == null) {
            return null;
        }

        // Secondary staleness check: cancel the entire pass if the document changed
        // between getSemanticTokens() and now. Applying tokens from a previous document
        // version would produce inverted or out-of-bounds offsets and cause
        // IllegalArgumentException inside HighlightInfo (see issue #XXX).
        if (file.getModificationStamp() != result.modificationStamp()) {
            return null;
        }

        if (holder != null) {
            // Direct highlight path (TextMate / PlainText files).
            // LazyHighlightInfo.resolve() returns null for invalid ranges (defensive
            // guard for the rare race described in the class Javadoc); skip those.
            result.data().highlight(file, document, (start, end, colorKey) -> {
                var info = LazyHighlightInfo.resolve(start, end, colorKey);
                if (info != null) {
                    holder.add(info);
                }
            });
            return null;
        } else {
            // Lazy highlight path (custom PSI files): one LazyHighlightInfo per start
            // offset; visit() resolves them per PsiElement.
            //
            // The array size is snapshotted here. A write action arriving after this
            // line could shrink the document; the guard in SemanticTokensData.highlight()
            // rejects out-of-bounds tokens, and the bounds check below covers the
            // remaining window between that snapshot and the array allocation.
            var infos = new LazyHighlightInfo[document.getTextLength()];
            result.data().highlight(file, document, (start, end, colorKey) -> {
                // SemanticTokensData validated start/end against its own docLength
                // snapshot. This guard covers the window between that snapshot and the
                // infos[] allocation above.
                if (start < infos.length && end <= infos.length) {
                    infos[start] = new LazyHighlightInfo(end, colorKey);
                }
            });
            return infos;
        }
    }

    @Override
    public @NotNull HighlightVisitor clone() {
        return new LSPSemanticTokensHighlightVisitor();
    }
}