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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.features.AbstractLSPFeatureSupport;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
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
public class LSPFoldingRangeSupport extends AbstractLSPFeatureSupport<FoldingRangeRequestParams, List<FoldingRange>> {
    private final FoldingRangeRequestParams params;

    public LSPFoldingRangeSupport(@NotNull PsiFile file) {
        super(file);
        this.params = new FoldingRangeRequestParams(LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile()));
    }

    public CompletableFuture<List<FoldingRange>> getFoldingRanges() {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<FoldingRange>> doLoad(FoldingRangeRequestParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getFoldingRanges(file.getVirtualFile(), file.getProject(), params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<FoldingRange>> getFoldingRanges(@NotNull VirtualFile file,
                                                                                   @NotNull Project project,
                                                                                   @NotNull FoldingRangeRequestParams params,
                                                                                   @NotNull CancellationSupport cancellationSupport) {

        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, LanguageServerItem::isFoldingSupported)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have folding range capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedStage(Collections.emptyList());
                    }

                    // Collect list of textDocument/foldingRange future for each language servers
                    List<CompletableFuture<List<FoldingRange>>> foldingRangesPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getFoldingRangesFor(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/foldingRange future in one future which return the list of folding ranges
                    return CompletableFutures.mergeInOneFuture(foldingRangesPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<FoldingRange>> getFoldingRangesFor(FoldingRangeRequestParams params,
                                                                             LanguageServerItem languageServer,
                                                                             CancellationSupport cancellationSupport) {
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
