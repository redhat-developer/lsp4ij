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
package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP selectionRange support which loads and caches selection ranges by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/selectionRange' requests</li>
 * </ul>
 */
public class LSPSelectionRangeSupport extends AbstractLSPDocumentFeatureSupport<LSPSelectionRangeParams, List<SelectionRange>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPSelectionRangeSupport.class);

    private Integer previousOffset;

    public LSPSelectionRangeSupport(@NotNull PsiFile file) {
        super(file);
    }

    @NotNull
    @ApiStatus.Internal
    public static List<SelectionRange> getSelectionRanges(@NotNull PsiFile file,
                                                          @NotNull Document document,
                                                          int offset,
                                                          @Nullable Integer timeout) {
        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            return Collections.emptyList();
        }

        // Consume LSP 'textDocument/selectionRanges' request
        LSPSelectionRangeSupport selectionRangeSupport = LSPFileSupport.getSupport(file).getSelectionRangeSupport();
        TextDocumentIdentifier textDocumentIdentifier = LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile());
        Position position = LSPIJUtils.toPosition(offset, document);
        var params = new LSPSelectionRangeParams(textDocumentIdentifier, Collections.singletonList(position), offset);
        CompletableFuture<List<SelectionRange>> selectionRangesFuture = selectionRangeSupport.getSelectionRanges(params);
        try {
            waitUntilDone(selectionRangesFuture, file, timeout);
        } catch (ProcessCanceledException e) {
            //Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            selectionRangeSupport.cancel();
            return Collections.emptyList();
        } catch (CancellationException e) {
            // cancel the LSP requests textDocument/selectionRanges
            selectionRangeSupport.cancel();
            return Collections.emptyList();
        } catch (TimeoutException e) {
            // Ignore timeout error
            return Collections.emptyList();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/selectionRanges' request", e);
            return Collections.emptyList();
        }

        if (!isDoneNormally(selectionRangesFuture)) {
            return Collections.emptyList();
        }

        // textDocument/selectionRanges has been collected correctly, create list of IJ SelectionDescriptor from LSP SelectionRange list
        return selectionRangesFuture.getNow(Collections.emptyList());
    }

    /**
     * Converts the LSP selection range into the IDE text range.
     *
     * @param selectionRange the LSP selection range
     * @param document       the document for for which the selection range applies
     * @return the corresponding text range
     */
    @NotNull
    @ApiStatus.Internal
    public static TextRange getTextRange(@NotNull SelectionRange selectionRange,
                                         @NotNull Document document) {
        Range range = selectionRange.getRange();
        Position rangeStart = range.getStart();
        Position rangeEnd = range.getEnd();
        int startOffset = LSPIJUtils.toOffset(rangeStart, document);
        int endOffset = LSPIJUtils.toOffset(rangeEnd, document);
        return TextRange.create(startOffset, endOffset);
    }

    /**
     * Returns true if there is a started language server which supports LSP selectionRange and false otherwise.
     *
     * @param file the Psi file
     * @return true if there is a started language server which supports LSP selectionRange and false otherwise.
     */
    public static boolean isSelectionRangesAvailable(@NotNull PsiFile file) {
        return (LanguageServiceAccessor.getInstance(file.getProject()).hasAny(file, ls -> {
            var selectionRangeFeature = ls.getClientFeatures().getSelectionRangeFeature();
            return selectionRangeFeature.isEnabled(file) && selectionRangeFeature.isSupported(file);
        }));
    }

    @NotNull
    @ApiStatus.Internal
    public static List<TextRange> getSelectionTextRanges(@NotNull PsiFile file,
                                                         @NotNull Editor editor,
                                                         int offset) {
        Document document = editor.getDocument();
        List<SelectionRange> selectionRanges = getSelectionRanges(file, document, offset, 500);
        if (ContainerUtil.isEmpty(selectionRanges)) {
            return Collections.emptyList();
        }

        // Convert the selection ranges into text ranges
        Set<TextRange> textRanges = new LinkedHashSet<>(selectionRanges.size());
        for (SelectionRange selectionRange : selectionRanges) {
            textRanges.add(getTextRange(selectionRange, document));
            for (SelectionRange parentSelectionRange = selectionRange.getParent();
                 parentSelectionRange != null;
                 parentSelectionRange = parentSelectionRange.getParent()) {
                textRanges.add(getTextRange(parentSelectionRange, document));
            }
        }
        return new ArrayList<>(textRanges);
    }

    public CompletableFuture<List<SelectionRange>> getSelectionRanges(LSPSelectionRangeParams params) {
        int offset = params.getOffset();
        if ((previousOffset != null) && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<SelectionRange>> doLoad(LSPSelectionRangeParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getSelectionRanges(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<SelectionRange>> getSelectionRanges(@NotNull PsiFile file,
                                                                                       @NotNull LSPSelectionRangeParams params,
                                                                                       @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getSelectionRangeFeature().isEnabled(file),
                f -> f.getSelectionRangeFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have selection range capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedStage(Collections.emptyList());
                    }

                    // Collect list of textDocument/selectionRange future for each language servers
                    List<CompletableFuture<List<SelectionRange>>> selectionRangesPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getSelectionRangesFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/selectionRange future in one future which return the list of selection ranges
                    return CompletableFutures.mergeInOneFuture(selectionRangesPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<SelectionRange>> getSelectionRangesFor(@NotNull LSPSelectionRangeParams params,
                                                                                 @NotNull PsiFile file,
                                                                                 @NotNull LanguageServerItem languageServer,
                                                                                 @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .selectionRange(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_SELECTION_RANGE)
                .thenApplyAsync(selectionRanges -> {
                    if (selectionRanges == null) {
                        // textDocument/selectionRange may return null
                        return Collections.emptyList();
                    }
                    return selectionRanges.stream()
                            .filter(Objects::nonNull)
                            .filter(selectionRange -> selectionRange.getRange() != null)
                            .toList();
                });
    }

}
