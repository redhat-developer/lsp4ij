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
package com.redhat.devtools.lsp4ij.features.foldingRange;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP foldingRange support which loads and caches folding ranges by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/foldingRange' requests</li>
 * </ul>
 */
public class LSPFoldingRangeSupport extends AbstractLSPDocumentFeatureSupport<FoldingRangeRequestParams, List<FoldingRange>> {

    public LSPFoldingRangeSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<FoldingRange>> getFoldingRanges(FoldingRangeRequestParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<FoldingRange>> doLoad(FoldingRangeRequestParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getFoldingRanges(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<FoldingRange>> getFoldingRanges(@NotNull PsiFile file,
                                                                                   @NotNull FoldingRangeRequestParams params,
                                                                                   @NotNull CancellationSupport cancellationSupport) {

        return getLanguageServers(file,
                f -> f.getFoldingRangeFeature().isEnabled(file),
                f -> f.getFoldingRangeFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have folding range capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedStage(Collections.emptyList());
                    }

                    // Collect list of textDocument/foldingRange future for each language servers
                    List<CompletableFuture<List<FoldingRange>>> foldingRangesPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getFoldingRangesFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/foldingRange future in one future which return the list of folding ranges
                    return CompletableFutures.mergeInOneFuture(foldingRangesPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<FoldingRange>> getFoldingRangesFor(@NotNull FoldingRangeRequestParams params,
                                                                             @NotNull PsiFile file,
                                                                             @NotNull LanguageServerItem languageServer,
                                                                             @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .foldingRange(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_FOLDING_RANGE)
                .thenApplyAsync(foldingRanges -> {
                    if (foldingRanges == null) {
                        // textDocument/foldingRange may return null
                        return Collections.emptyList();
                    }
                    return foldingRanges.stream()
                            .filter(Objects::nonNull)
                            .toList();
                });
    }

}
