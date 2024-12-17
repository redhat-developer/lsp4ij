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
package com.redhat.devtools.lsp4ij.features.highlight;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP highlight support which loads and caches highlight by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/documentHighlight' requests</li>
 * </ul>
 */
public class LSPHighlightSupport extends AbstractLSPDocumentFeatureSupport<DocumentHighlightParams, List<DocumentHighlight>> {

    private Integer previousOffset;

    public LSPHighlightSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<DocumentHighlight>> getHighlights(LSPDocumentHighlightParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<DocumentHighlight>> doLoad(DocumentHighlightParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getHighlights(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<DocumentHighlight>> getHighlights(@NotNull PsiFile file,
                                                                                     @NotNull DocumentHighlightParams params,
                                                                                     @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getDocumentHighlightFeature().isEnabled(file),
                f -> f.getDocumentHighlightFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have documentHighlights capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/highlights future for each language servers
                    List<CompletableFuture<List<? extends org.eclipse.lsp4j.DocumentHighlight>>> highlightsPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getHighlightsFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/highlights future in one future which return the list of highlights
                    return mergeInOneFuture(highlightsPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<? extends org.eclipse.lsp4j.DocumentHighlight>> getHighlightsFor(@NotNull DocumentHighlightParams params,
                                                                                                           @NotNull PsiFile file,
                                                                                                           @NotNull LanguageServerItem languageServer,
                                                                                                           @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .documentHighlight(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_HIGHLIGHT)
                .thenApplyAsync(highlights -> {
                    if (highlights == null) {
                        // textDocument/highlight may return null
                        return Collections.emptyList();
                    }
                    return highlights.stream()
                            .filter(Objects::nonNull)
                            .toList();
                });
    }

    /**
     * Merge the given futures List<CompletableFuture<List<DocumentHighlight>>> in one future CompletableFuture<List<DocumentHighlight>.
     *
     * @param futures             the list of futures which return a List<DocumentHighlight>.
     * @param cancellationSupport the cancellation support.
     * @return
     */
    private static @NotNull CompletableFuture<List<DocumentHighlight>> mergeInOneFuture(List<CompletableFuture<List<? extends DocumentHighlight>>> futures,
                                                                                        @NotNull CancellationSupport cancellationSupport) {
        CompletableFuture<Void> allFutures = cancellationSupport
                .execute(CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])));
        return allFutures.thenApply(Void -> {
            List<DocumentHighlight> mergedDataList = new ArrayList<>(futures.size());
            for (CompletableFuture<List<? extends DocumentHighlight>> dataListFuture : futures) {
                List<? extends DocumentHighlight> data = dataListFuture.join();
                if (data != null) {
                    mergedDataList.addAll(data);
                }
            }
            return mergedDataList;
        });
    }

}
