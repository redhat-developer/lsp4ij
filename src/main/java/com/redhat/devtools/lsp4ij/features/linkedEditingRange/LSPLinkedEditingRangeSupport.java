/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.linkedEditingRange;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.mergeInOneFuture;

/**
 * LSP linkedEditingRange support which loads the linked editing ranges at an offset by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/linkedEditingRange' requests</li>
 * </ul>
 * <p>
 * The result has the linked editing ranges of each language server which gives some. Each editor has its own support,
 * so that the request of one editor of a file doesn't cancel the request of another.
 */
public class LSPLinkedEditingRangeSupport extends AbstractLSPDocumentFeatureSupport<LSPLinkedEditingRangeParams, List<LinkedEditingRanges>> {

    private Integer previousOffset;

    public LSPLinkedEditingRangeSupport(@NotNull PsiFile file) {
        super(file);
    }

    /**
     * Returns true if a started language server of the file supports linked editing and false otherwise. It starts no
     * language server.
     *
     * @param file the Psi file.
     * @return true if a started language server of the file supports linked editing and false otherwise.
     */
    public static boolean isLinkedEditingRangeAvailable(@NotNull PsiFile file) {
        return LanguageServiceAccessor.getInstance(file.getProject()).hasAny(file, ls -> {
            var linkedEditingRangeFeature = ls.getClientFeatures().getLinkedEditingRangeFeature();
            return linkedEditingRangeFeature.isEnabled(file) && linkedEditingRangeFeature.isSupported(file);
        });
    }

    public CompletableFuture<List<LinkedEditingRanges>> getLinkedEditingRanges(LSPLinkedEditingRangeParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<LinkedEditingRanges>> doLoad(LSPLinkedEditingRangeParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getLinkedEditingRanges(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<LinkedEditingRanges>> getLinkedEditingRanges(@NotNull PsiFile file,
                                                                                               @NotNull LSPLinkedEditingRangeParams params,
                                                                                               @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getLinkedEditingRangeFeature().isEnabled(file),
                f -> f.getLinkedEditingRangeFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have linked editing range capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(List.of());
                    }

                    // Collect list of textDocument/linkedEditingRange future for each language servers
                    List<CompletableFuture<List<LinkedEditingRanges>>> linkedEditingRangesPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getLinkedEditingRangesFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/linkedEditingRange future in one future which return the list of linked editing ranges
                    return mergeInOneFuture(linkedEditingRangesPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<LinkedEditingRanges>> getLinkedEditingRangesFor(@NotNull LSPLinkedEditingRangeParams params,
                                                                                         @NotNull PsiFile file,
                                                                                         @NotNull LanguageServerItem languageServer,
                                                                                         @NotNull CancellationSupport cancellationSupport) {
        // Each language server gets its own parameters, since its request is written later, on its own thread
        var serverParams = new LSPLinkedEditingRangeParams(new TextDocumentIdentifier(), params.getPosition(), params.getOffset());
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(serverParams.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .linkedEditingRange(serverParams), languageServer, LSPRequestConstants.TEXT_DOCUMENT_LINKED_EDITING_RANGE)
                .thenApplyAsync(linkedEditingRanges -> {
                    if (linkedEditingRanges == null || linkedEditingRanges.getRanges() == null
                            || linkedEditingRanges.getRanges().isEmpty()) {
                        // textDocument/linkedEditingRange may return null
                        return List.of();
                    }
                    return List.of(linkedEditingRanges);
                });
    }

}
