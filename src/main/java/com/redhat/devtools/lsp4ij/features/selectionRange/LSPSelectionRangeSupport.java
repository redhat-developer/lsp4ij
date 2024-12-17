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

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.SelectionRange;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP selectionRange support which loads and caches selection ranges by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/selectionRange' requests</li>
 * </ul>
 */
public class LSPSelectionRangeSupport extends AbstractLSPDocumentFeatureSupport<LSPSelectionRangeParams, List<SelectionRange>> {

    private Integer previousOffset;

    public LSPSelectionRangeSupport(@NotNull PsiFile file) {
        super(file);
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
                            .toList();
                });
    }

}
