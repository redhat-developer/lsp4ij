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
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.HoverParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP hover support which loads and caches hover by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/hover' requests</li>
 * </ul>
 */
public class LSPHoverSupport extends AbstractLSPDocumentFeatureSupport<HoverParams, List<HoverData>> {

    private Integer previousOffset;

    public LSPHoverSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<HoverData>> getHover(LSPHoverParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<HoverData>> doLoad(HoverParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getHover(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<HoverData>> getHover(@NotNull PsiFile file,
                                                                        @NotNull HoverParams params,
                                                                        @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getHoverFeature().isEnabled(file),
                f -> f.getHoverFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have hover capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/hover future for each language servers
                    List<CompletableFuture<HoverData>> hoverPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getHoverFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/hover future in one future which return the list of highlights
                    return mergeInOneFuture(hoverPerServerFutures, cancellationSupport);
                });
    }

    public static @NotNull CompletableFuture<List<HoverData>> mergeInOneFuture(@NotNull List<CompletableFuture<HoverData>> futures,
                                                                               @NotNull CancellationSupport cancellationSupport) {
        CompletableFuture<Void> allFutures = cancellationSupport
                .execute(CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])));
        return allFutures.thenApply(Void -> {
            List<HoverData> mergedDataList = new ArrayList<>(futures.size());
            for (CompletableFuture<HoverData> dataListFuture : futures) {
                var data = dataListFuture.join();
                if (data != null) {
                    mergedDataList.add(data);
                }
            }
            return mergedDataList;
        });
    }

    private static CompletableFuture<@Nullable HoverData> getHoverFor(@NotNull HoverParams params,
                                                                      @NotNull PsiFile file,
                                                                      @NotNull LanguageServerItem languageServer,
                                                                      @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .hover(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_HOVER)
                .thenApply(hover -> hover != null ? new HoverData(hover, languageServer) : null);
    }

}
