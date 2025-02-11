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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static com.redhat.devtools.lsp4ij.LSPIJUtils.applyEdits;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Abstract class for LSP formatting and range formatting.
 */
public class LSPFormattingSupport extends AbstractLSPDocumentFeatureSupport<LSPFormattingParams, List<? extends TextEdit>> {

    public LSPFormattingSupport(@NotNull PsiFile file) {
        super(file);
    }

    public void format(@NotNull Document document,
                       @Nullable Editor editor,
                       @Nullable TextRange textRange,
                       @NotNull AsyncFormattingRequest formattingRequest) {
        Integer tabSize = editor != null ? LSPIJUtils.getTabSize(editor) : null;
        Boolean insertSpaces = editor != null ? LSPIJUtils.isInsertSpaces(editor) : null;
        LSPFormattingParams params = new LSPFormattingParams(tabSize, insertSpaces, textRange, document);
        CompletableFuture<List<? extends TextEdit>> formatFuture = this.getFeatureData(params);
        try {
            waitUntilDone(formatFuture, getFile());
        } catch (
                ProcessCanceledException e) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            handleError(formattingRequest, e);
            return;
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
            formattingRequest.onError("LSP formatting error", error.getMessage() != null ? error.getMessage() :  error.getClass().getName());
        }
    }

    @Override
    protected CompletableFuture<List<? extends TextEdit>> doLoad(LSPFormattingParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getFormatting(file, params, cancellationSupport);
    }

    protected @NotNull CompletableFuture<List<? extends TextEdit>> getFormatting(@NotNull PsiFile file,
                                                                                 @NotNull LSPFormattingParams params,
                                                                                 @NotNull CancellationSupport cancellationSupport) {
        boolean isRangeFormatting = params.textRange() != null;
        Predicate<LSPClientFeatures> filter = !isRangeFormatting ?
                f -> f.getFormattingFeature().isFormattingSupported(file) :
                f -> f.getFormattingFeature().isFormattingSupported(file) ||
                        f.getFormattingFeature().isRangeFormattingSupported(file);
        return getLanguageServers(file,
                f -> f.getFormattingFeature().isEnabled(file),
                filter)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have formatting capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Get the first language server which supports range formatting (if it requires) or formatting
                    LanguageServerItem languageServer = getFormattingLanguageServer(languageServers, isRangeFormatting);

                    cancellationSupport.checkCanceled();

                    if (isRangeFormatting && languageServer.isDocumentRangeFormattingSupported()) {
                        // Range formatting
                        DocumentRangeFormattingParams lspParams = createDocumentRangeFormattingParams(params.tabSize(), params.insertSpaces(), params.textRange(), params.document(), languageServer);
                        return cancellationSupport.execute(languageServer
                                .getTextDocumentService()
                                .rangeFormatting(lspParams), languageServer, LSPRequestConstants.TEXT_DOCUMENT_RANGE_FORMATTING);
                    }

                    // Full document formatting
                    DocumentFormattingParams lspParams = createDocumentFormattingParams(params.tabSize(), params.insertSpaces(), languageServer);
                    return cancellationSupport.execute(languageServer
                            .getTextDocumentService()
                            .formatting(lspParams), languageServer, LSPRequestConstants.TEXT_DOCUMENT_FORMATTING);
                });
    }

    private static LanguageServerItem getFormattingLanguageServer(List<LanguageServerItem> languageServers, boolean isRangeFormatting) {
        if (isRangeFormatting) {
            // Range formatting, try to get the first language server which have the range formatting capability
            Optional<LanguageServerItem> result = languageServers
                    .stream()
                    .filter(LanguageServerItem::isDocumentRangeFormattingSupported)
                    .findFirst();
            if (result.isPresent()) {
                return result.get();
            }
        }
        // Get the first language server
        return languageServers.get(0);
    }

    private @NotNull DocumentFormattingParams createDocumentFormattingParams(@Nullable Integer tabSize,
                                                                             @Nullable Boolean insertSpaces,
                                                                             @NotNull LanguageServerItem languageServer) {
        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(FileUriSupport.getFileUri(getFile().getVirtualFile(), languageServer.getClientFeatures()).toASCIIString()));
        FormattingOptions options = new FormattingOptions();
        if (tabSize != null) {
            options.setTabSize(tabSize);
        }
        if (insertSpaces != null) {
            options.setInsertSpaces(insertSpaces);
        }
        params.setOptions(options);
        return params;
    }

    private @NotNull DocumentRangeFormattingParams createDocumentRangeFormattingParams(@Nullable Integer tabSize,
                                                                                       @Nullable Boolean insertSpaces,
                                                                                       @NotNull TextRange textRange,
                                                                                       @NotNull Document document, LanguageServerItem languageServer) {
        DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(FileUriSupport.getFileUri(getFile().getVirtualFile(), languageServer.getClientFeatures()).toASCIIString()));
        FormattingOptions options = new FormattingOptions();
        if (tabSize != null) {
            options.setTabSize(tabSize);
        }
        if (insertSpaces != null) {
            options.setInsertSpaces(insertSpaces);
        }
        params.setOptions(options);
        if (document != null) {
            Range range = LSPIJUtils.toRange(textRange, document);
            params.setRange(range);
        }
        return params;
    }
}
