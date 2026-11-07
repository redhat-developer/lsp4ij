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

import java.util.ArrayList;
import java.util.Comparator;
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

    private static final Comparator<Position> POSITION_ORDER = Comparator.comparingInt(Position::getLine)
            .thenComparingInt(Position::getCharacter);

    private final AtomicReference<CancellationSupport> formattingCancellation = new AtomicReference<>();

    public LSPFormattingSupport(@NotNull PsiFile file) {
        super(file);
    }

    public void format(@NotNull Document document,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull List<TextRange> textRanges,
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
            LanguageServerItem formattingServer = findFormattingServer(file, textRanges, cancellation, connections);
            if (formattingServer == null || formattingCancellation.get() != cancellation
                    || !isCurrent(document, file, original, modificationStamp)) {
                formattingRequest.onTextReady(null);
                return;
            }
            // We are currently in a ReadAction; this call must occur here because it may access PSI or CodeStyleSettings.
            var formattingOptions = formattingServer.getClientFeatures().getFormattingFeature().getFormattingOptions(file, editor);
            LSPFormattingParams params = new LSPFormattingParams(textRanges, document, formattingServer, formattingOptions);
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
        boolean isRangeFormatting = !params.textRanges().isEmpty();
        var formattingServer = params.formattingServer();
        if (isRangeFormatting && formattingServer.isDocumentRangeFormattingSupported()) {
            List<Range> ranges = params.textRanges().stream()
                    .map(textRange -> LSPIJUtils.toRange(textRange, params.document()))
                    .toList();
            if (ranges.size() == 1) {
                // Range formatting
                DocumentRangeFormattingParams lspParams = createDocumentRangeFormattingParams(params.formattingOptions(), ranges.get(0), formattingServer);
                return getRangeFormatting(lspParams, formattingServer, cancellationSupport);
            }
            if (formattingServer.isDocumentRangesFormattingSupported()) {
                // Ranges formatting
                DocumentRangesFormattingParams lspParams = createDocumentRangesFormattingParams(params.formattingOptions(), ranges, formattingServer);
                return cancellationSupport.execute(formattingServer
                        .getTextDocumentService()
                        .rangesFormatting(lspParams), formattingServer, LSPRequestConstants.TEXT_DOCUMENT_RANGES_FORMATTING);
            }
            // Range formatting of each range
            List<DocumentRangeFormattingParams> lspParams = ranges.stream()
                    .map(range -> createDocumentRangeFormattingParams(params.formattingOptions(), range, formattingServer))
                    .toList();
            return formatEachRange(lspParams, List.of(), formattingServer, cancellationSupport);
        }

        // Full document formatting
        DocumentFormattingParams lspParams = createDocumentFormattingParams(params.formattingOptions(), formattingServer);
        return cancellationSupport.execute(formattingServer
                .getTextDocumentService()
                .formatting(lspParams), formattingServer, LSPRequestConstants.TEXT_DOCUMENT_FORMATTING);
    }

    private @NotNull CompletableFuture<List<? extends TextEdit>> getRangeFormatting(@NotNull DocumentRangeFormattingParams lspParams,
                                                                                   @NotNull LanguageServerItem formattingServer,
                                                                                   @NotNull CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(formattingServer
                .getTextDocumentService()
                .rangeFormatting(lspParams), formattingServer, LSPRequestConstants.TEXT_DOCUMENT_RANGE_FORMATTING);
    }

    /**
     * Formats several ranges for a language server which can't format them at once: one
     * {@code textDocument/rangeFormatting} after the other, as VS Code does. All the edits are for the
     * same text, so they are applied together, unless the edits of two ranges overlap: then those two
     * ranges are formatted again as one range which covers both.
     *
     * @param requests  the requests of the ranges still to format.
     * @param formatted the ranges formatted so far, with their edits.
     */
    private @NotNull CompletableFuture<List<? extends TextEdit>> formatEachRange(@NotNull List<DocumentRangeFormattingParams> requests,
                                                                                @NotNull List<FormattedRange> formatted,
                                                                                @NotNull LanguageServerItem formattingServer,
                                                                                @NotNull CancellationSupport cancellationSupport) {
        if (!requests.isEmpty()) {
            DocumentRangeFormattingParams request = requests.get(0);
            return getRangeFormatting(request, formattingServer, cancellationSupport)
                    .thenCompose(edits -> {
                        // Ask for nothing more once the formatting is cancelled.
                        cancellationSupport.checkCanceled();
                        var formattedNow = new ArrayList<>(formatted);
                        formattedNow.add(new FormattedRange(request, edits != null ? edits : List.of()));
                        return formatEachRange(requests.subList(1, requests.size()), formattedNow, formattingServer, cancellationSupport);
                    });
        }
        for (int i = 0; i < formatted.size(); i++) {
            for (int j = i + 1; j < formatted.size(); j++) {
                FormattedRange first = formatted.get(i);
                FormattedRange second = formatted.get(j);
                if (first.overlaps(second)) {
                    var others = new ArrayList<>(formatted);
                    others.remove(j);
                    others.remove(i);
                    return formatEachRange(List.of(first.cover(second)), others, formattingServer, cancellationSupport);
                }
            }
        }
        return CompletableFuture.completedFuture(formatted.stream()
                .<TextEdit>flatMap(formattedRange -> formattedRange.edits().stream())
                .toList());
    }

    private record FormattedRange(@NotNull DocumentRangeFormattingParams request, @NotNull List<? extends TextEdit> edits) {

        boolean overlaps(@NotNull FormattedRange other) {
            // Edits which only touch overlap too: two insertions at one offset have no defined order.
            return edits.stream().anyMatch(edit -> other.edits.stream().anyMatch(otherEdit ->
                    POSITION_ORDER.compare(edit.getRange().getStart(), otherEdit.getRange().getEnd()) <= 0
                            && POSITION_ORDER.compare(otherEdit.getRange().getStart(), edit.getRange().getEnd()) <= 0));
        }

        /** Returns the request to format this range and the other one as one range. */
        @NotNull DocumentRangeFormattingParams cover(@NotNull FormattedRange other) {
            Range range = request.getRange();
            Range otherRange = other.request.getRange();
            Position start = POSITION_ORDER.compare(range.getStart(), otherRange.getStart()) <= 0 ? range.getStart() : otherRange.getStart();
            Position end = POSITION_ORDER.compare(range.getEnd(), otherRange.getEnd()) >= 0 ? range.getEnd() : otherRange.getEnd();
            return new DocumentRangeFormattingParams(request.getTextDocument(), request.getOptions(), new Range(start, end));
        }
    }


    /**
     * Finds a {@link LanguageServerItem} that supports document formatting for the given PSI file.
     * <p>
     * Depending on whether text ranges are provided, this method looks for a language server
     * that supports either:
     * <ul>
     *   <li>{@code textDocument/formatting} – when {@code textRanges} is empty,</li>
     *   <li>{@code textDocument/rangeFormatting} or {@code textDocument/formatting} – when ranges are provided.</li>
     * </ul>
     * <p>
     * The method connects the current document to matching servers before selecting one through
     * its {@link LSPClientFeatures}, preserving the formatter override and range preferences.
     * This also sends {@code textDocument/didOpen} for a closed dirty buffer before formatting it.
     * If no suitable server is found, {@code null} is returned.
     *
     * @param file      the PSI file for which formatting is requested.
     * @param textRanges the ranges to format, or an empty list to format the entire document.
     * @param cancellation cancellation for both the server connection and formatting request.
     * @param connections temporary connection ownership for this formatting operation.
     * @return a {@link LanguageServerItem} supporting formatting, or {@code null} if none is available.
     */
    private @Nullable LanguageServerItem findFormattingServer(@NotNull PsiFile file,
                                                              @NotNull List<TextRange> textRanges,
                                                              @NotNull CancellationSupport cancellation,
                                                              @NotNull DocumentConnectionScope connections) {
        boolean isRangeFormatting = !textRanges.isEmpty();
        var servers = trackFormattingFuture(findFormattingServers(file, textRanges, connections), cancellation);
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
                                                                     @NotNull List<TextRange> textRanges,
                                                                     @NotNull DocumentConnectionScope connections) {
        return LanguageServiceAccessor.getInstance(file.getProject()).getLanguageServersForScope(file,
                features -> features.getFormattingFeature().isEnabled(file),
                features -> features.getFormattingFeature().isFormattingSupported(file)
                        || (!textRanges.isEmpty() && features.getFormattingFeature().isRangeFormattingSupported(file)), connections);
    }

    private @NotNull DocumentFormattingParams createDocumentFormattingParams(@NotNull FormattingOptions options,
                                                                             @NotNull LanguageServerItem languageServer) {
        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(FileUriSupport.toString(getFile().getVirtualFile(), languageServer.getClientFeatures())));
        params.setOptions(options);
        return params;
    }

    private @NotNull DocumentRangeFormattingParams createDocumentRangeFormattingParams(@NotNull FormattingOptions options,
                                                                                       @NotNull Range range,
                                                                                       @NotNull LanguageServerItem languageServer) {
        DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(FileUriSupport.toString(getFile().getVirtualFile(), languageServer.getClientFeatures())));
        params.setOptions(options);
        params.setRange(range);
        return params;
    }

    private @NotNull DocumentRangesFormattingParams createDocumentRangesFormattingParams(@NotNull FormattingOptions options,
                                                                                         @NotNull List<Range> ranges,
                                                                                         @NotNull LanguageServerItem languageServer) {
        DocumentRangesFormattingParams params = new DocumentRangesFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(FileUriSupport.toString(getFile().getVirtualFile(), languageServer.getClientFeatures())));
        params.setOptions(options);
        params.setRanges(ranges);
        return params;
    }
}
