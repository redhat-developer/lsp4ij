/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static com.redhat.devtools.lsp4ij.LSPIJUtils.applyEdits;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.awaitWithCheckCanceled;

/**
 * LSP formatting and range formatting support.
 */
public class LSPFormattingSupport extends AbstractLSPDocumentFeatureSupport<LSPFormattingParams, List<? extends TextEdit>> {

    private final AtomicReference<CancellationSupport> formattingCancellation = new AtomicReference<>();

    public LSPFormattingSupport(@NotNull PsiFile file) {
        super(file);
    }

    public void format(@NotNull Document document,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @Nullable TextRange textRange,
                       @NotNull AsyncFormattingRequest formattingRequest) {
        String original = formattingRequest.getDocumentText();
        long modificationStamp = document.getModificationStamp();
        var cancellation = new CancellationSupport();
        var previous = formattingCancellation.getAndSet(cancellation);
        if (previous != null) {
            previous.cancel();
        }
        try (var connections = new DocumentConnectionScope()) {
            if (!isCurrent(document, file, original, modificationStamp)) {
                formattingRequest.onTextReady(null);
                return;
            }
            LanguageServerItem formattingServer = findFormattingServer(file, textRange, cancellation, connections);
            if (formattingServer == null || formattingCancellation.get() != cancellation
                    || !isCurrent(document, file, original, modificationStamp)) {
                formattingRequest.onTextReady(null);
                return;
            }
            // We are currently in a ReadAction; this call must occur here because it may access PSI or CodeStyleSettings.
            var formattingOptions = formattingServer.getClientFeatures().getFormattingFeature().getFormattingOptions(file, editor);
            LSPFormattingParams params = new LSPFormattingParams(textRange, document, formattingServer, formattingOptions);
            CompletableFuture<List<? extends TextEdit>> formatFuture = trackFormattingFuture(
                    getFormatting(params, cancellation), cancellation);
            awaitWithCheckCanceled(formatFuture);
            var wrapper = formattingServer.getServerWrapper();
            if (formattingCancellation.get() != cancellation
                    || !isCurrent(document, file, original, modificationStamp)
                    || !wrapper.isActive() || wrapper.isDisposed()
                    || wrapper.getLanguageServer() != formattingServer.getServer()) {
                formattingRequest.onTextReady(null);
                return;
            }
            List<? extends TextEdit> edits = formatFuture.getNow(null);
            // LSP ranges belong to the request's text. Never use a newer buffer, or require
            // an editor which may have closed while the request was running.
            String formatted = edits != null
                    ? applyEdits(EditorFactory.getInstance().createDocument(original), edits)
                    : null;
            formattingRequest.onTextReady(formatted);
        } catch (ProcessCanceledException e) {
            cancellation.cancel();
            throw e;
        } catch (CancellationException e) {
            handleError(formattingRequest, e);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            handleError(formattingRequest, cause != null ? cause : e);
        } finally {
            formattingCancellation.compareAndSet(cancellation, null);
        }
    }

    @Override
    public void cancel() {
        var cancellation = formattingCancellation.getAndSet(null);
        if (cancellation != null) {
            cancellation.cancel();
        }
        super.cancel();
    }

    private <T> CompletableFuture<T> trackFormattingFuture(@NotNull CompletableFuture<T> future,
                                                         @NotNull CancellationSupport cancellation) {
        cancellation.execute(future);
        // Cancellation may race registering the new future. The atomic owner check closes
        // that gap for both reconnect and formatting, without waiting under a lock.
        if (formattingCancellation.get() != cancellation) {
            CancellationSupport.cancel(future);
            throw new CancellationException();
        }
        return future;
    }

    private static boolean isCurrent(@NotNull Document document, @NotNull PsiFile file,
                                     @NotNull String original, long modificationStamp) {
        return !file.getProject().isDisposed() && file.isValid()
                && document.getModificationStamp() == modificationStamp && original.equals(document.getText());
    }

    private static void handleError(@NotNull AsyncFormattingRequest formattingRequest,
                                    @NotNull Throwable error) {
        if (error instanceof CancellationException) {
            // Ignore the error without replacing any newer text.
            formattingRequest.onTextReady(null);
        } else {
            formattingRequest.onError("LSP formatting error", error.getMessage() != null ? error.getMessage() : error.getClass().getName());
        }
    }

    @Override
    protected CompletableFuture<List<? extends TextEdit>> doLoad(LSPFormattingParams params, CancellationSupport cancellationSupport) {
        return getFormatting(params, cancellationSupport);
    }

    protected @NotNull CompletableFuture<List<? extends TextEdit>> getFormatting(@NotNull LSPFormattingParams params,
                                                                                 @NotNull CancellationSupport cancellationSupport) {
        boolean isRangeFormatting = params.textRange() != null;
        var formattingServer = params.formattingServer();
        if (isRangeFormatting && formattingServer.isDocumentRangeFormattingSupported()) {
            // Range formatting
            DocumentRangeFormattingParams lspParams = createDocumentRangeFormattingParams(params.formattingOptions(), params.textRange(), params.document(), formattingServer);
            return cancellationSupport.execute(formattingServer
                    .getTextDocumentService()
                    .rangeFormatting(lspParams), formattingServer, LSPRequestConstants.TEXT_DOCUMENT_RANGE_FORMATTING);
        }

        // Full document formatting
        DocumentFormattingParams lspParams = createDocumentFormattingParams(params.formattingOptions(), formattingServer);
        return cancellationSupport.execute(formattingServer
                .getTextDocumentService()
                .formatting(lspParams), formattingServer, LSPRequestConstants.TEXT_DOCUMENT_FORMATTING);
    }


    /**
     * Finds a {@link LanguageServerItem} that supports document formatting for the given PSI file.
     * <p>
     * Depending on whether a {@link TextRange} is provided, this method looks for a language server
     * that supports either:
     * <ul>
     *   <li>{@code textDocument/formatting} – when {@code textRange} is {@code null},</li>
     *   <li>{@code textDocument/rangeFormatting} or {@code textDocument/formatting} – when a range is provided.</li>
     * </ul>
     * <p>
     * The method connects the current document to matching servers before selecting one through
     * its {@link LSPClientFeatures}, preserving the formatter override and range preferences.
     * This also sends {@code textDocument/didOpen} for a closed dirty buffer before formatting it.
     * If no suitable server is found, {@code null} is returned.
     *
     * @param file      the PSI file for which formatting is requested.
     * @param textRange the range to format, or {@code null} to format the entire document.
     * @param cancellation cancellation for both the server connection and formatting request.
     * @param connections temporary connection ownership for this formatting operation.
     * @return a {@link LanguageServerItem} supporting formatting, or {@code null} if none is available.
     */
    private @Nullable LanguageServerItem findFormattingServer(@NotNull PsiFile file,
                                                              @Nullable TextRange textRange,
                                                              @NotNull CancellationSupport cancellation,
                                                              @NotNull DocumentConnectionScope connections) {
        boolean isRangeFormatting = textRange != null;
        var servers = trackFormattingFuture(findFormattingServers(file, textRange, connections), cancellation);
        awaitWithCheckCanceled(servers);
        LanguageServerItem selected = null;
        for (var server : servers.join()) {
            var formattingFeature = server.getClientFeatures().getFormattingFeature();
            if (selected == null || formattingFeature.isExistingFormatterOverrideable(file)
                    || (isRangeFormatting && !selected.isDocumentRangeFormattingSupported())) {
                selected = server;
            }
        }
        return selected;
    }

    /** Connects formatting candidates before applying formatter preferences. */
    CompletableFuture<List<LanguageServerItem>> findFormattingServers(@NotNull PsiFile file,
                                                                     @Nullable TextRange textRange,
                                                                     @NotNull DocumentConnectionScope connections) {
        return LanguageServiceAccessor.getInstance(file.getProject()).getLanguageServersForScope(file,
                features -> features.getFormattingFeature().isEnabled(file),
                features -> features.getFormattingFeature().isFormattingSupported(file)
                        || (textRange != null && features.getFormattingFeature().isRangeFormattingSupported(file)), connections);
    }

    private @NotNull DocumentFormattingParams createDocumentFormattingParams(@NotNull FormattingOptions options,
                                                                             @NotNull LanguageServerItem languageServer) {
        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(FileUriSupport.toString(getFile().getVirtualFile(), languageServer.getClientFeatures())));
        params.setOptions(options);
        return params;
    }

    private @NotNull DocumentRangeFormattingParams createDocumentRangeFormattingParams(@NotNull FormattingOptions options,
                                                                                       @NotNull TextRange textRange,
                                                                                       @NotNull Document document,
                                                                                       @NotNull LanguageServerItem languageServer) {
        DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(FileUriSupport.toString(getFile().getVirtualFile(), languageServer.getClientFeatures())));
        params.setOptions(options);
        Range range = LSPIJUtils.toRange(textRange, document);
        params.setRange(range);
        return params;
    }
}
