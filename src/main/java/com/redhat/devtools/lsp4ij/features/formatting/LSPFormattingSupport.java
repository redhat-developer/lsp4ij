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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Ref;
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
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.LSPIJUtils.applyEdits;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP formatting and range formatting support.
 */
public class LSPFormattingSupport extends AbstractLSPDocumentFeatureSupport<LSPFormattingParams, List<? extends TextEdit>> {

    public LSPFormattingSupport(@NotNull PsiFile file) {
        super(file);
    }

    public void format(@NotNull Document document,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @Nullable TextRange textRange,
                       @NotNull AsyncFormattingRequest formattingRequest) {
        LanguageServerItem formattingServer = findFormattingServer(file, textRange);
        if (formattingServer == null)  {
            return;
        }
        // We are currently in a ReadAction; this call must occur here because it may access PSI or CodeStyleSettings.
        var formattingOptions = formattingServer.getClientFeatures().getFormattingFeature().getFormattingOptions(file, editor);
        LSPFormattingParams params = new LSPFormattingParams(textRange, document, formattingServer, formattingOptions);
        CompletableFuture<List<? extends TextEdit>> formatFuture = this.getFeatureData(params);
        try {
            waitUntilDone(formatFuture, getFile());
        } catch (
                ProcessCanceledException e) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            //handleError(formattingRequest, e);
            throw e;
        } catch (CancellationException e) {
            // cancel the LSP requests textDocument/formatting / textDocument/rangeFormatting
            handleError(formattingRequest, e);
            return;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            handleError(formattingRequest, cause != null ? cause : e);
            return;
        }
        try {
            List<? extends TextEdit> edits = formatFuture != null ? formatFuture.getNow(null) : null;
            String formatted = edits != null ? applyEdits(editor.getDocument(), edits) : formattingRequest.getDocumentText();
            formattingRequest.onTextReady(formatted);
        } catch (Exception e) {
            handleError(formattingRequest, e);
        }
    }

    private static void handleError(@NotNull AsyncFormattingRequest formattingRequest,
                                    @NotNull Throwable error) {
        if (error instanceof ProcessCanceledException || error instanceof CancellationException) {
            // Ignore the error
            formattingRequest.onTextReady(formattingRequest.getDocumentText());
        } else {
            formattingRequest.onError("LSP formatting error", error.getMessage() != null ? error.getMessage() : error.getClass().getName());
        }
    }

    @Override
    protected CompletableFuture<List<? extends TextEdit>> doLoad(LSPFormattingParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
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
     * The method iterates over all language servers registered for the file's project and returns the
     * first server that declares formatting support through its {@link LSPClientFeatures}.
     * If no suitable server is found, {@code null} is returned.
     *
     * @param file      the PSI file for which formatting is requested.
     * @param textRange the range to format, or {@code null} to format the entire document.
     * @return a {@link LanguageServerItem} supporting formatting, or {@code null} if none is available.
     */
    private static @Nullable LanguageServerItem findFormattingServer(@NotNull PsiFile file,
                                                                     @Nullable TextRange textRange) {
        // Get the first language server which supports range formatting (if it requires) or formatting
        boolean isRangeFormatting = textRange != null;
        Ref<LanguageServerItem> server = Ref.create(null);
        LanguageServiceAccessor.getInstance(file.getProject()).processLanguageServers(file, ls -> {
            var formattingFeature = ls.getClientFeatures().getFormattingFeature();
            if (formattingFeature.isEnabled(file)) {
                boolean overridable = formattingFeature.isExistingFormatterOverrideable(file);
                if (!isRangeFormatting) {
                    if ((overridable || server.isNull()) && formattingFeature.isFormattingSupported(file)) {
                        server.set(new LanguageServerItem(ls.getLanguageServer(), ls));
                    }
                } else {
                    if (formattingFeature.isFormattingSupported(file) ||
                            formattingFeature.isRangeFormattingSupported(file)) {
                        if (overridable || server.isNull() || !server.get().isDocumentRangeFormattingSupported()) {
                            server.set(new LanguageServerItem(ls.getLanguageServer(), ls));
                        }
                    }
                }
            }
        });
        return server.isNull() ? null : server.get();
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
