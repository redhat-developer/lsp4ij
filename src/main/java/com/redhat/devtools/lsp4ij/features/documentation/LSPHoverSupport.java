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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.jetbrains.annotations.NotNull;

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
public class LSPHoverSupport extends AbstractLSPDocumentFeatureSupport<HoverParams, List<Hover>> {

    private Integer previousOffset;

    public LSPHoverSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<Hover>> getHover(LSPHoverParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<Hover>> doLoad(HoverParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getHover(file.getVirtualFile(), file.getProject(), params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<Hover>> getHover(@NotNull VirtualFile file,
                                                                    @NotNull Project project,
                                                                    @NotNull HoverParams params,
                                                                    @NotNull CancellationSupport cancellationSupport) {

        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, LanguageServerItem::isHoverSupported)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have hover capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/hover future for each language servers
                    List<CompletableFuture<Hover>> hoverPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getHoverFor(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/hover future in one future which return the list of highlights
                    return mergeInOneFuture(hoverPerServerFutures, cancellationSupport);
                });
    }

    public static @NotNull CompletableFuture<List<Hover>> mergeInOneFuture(@NotNull List<CompletableFuture<Hover>> futures,
                                                                           @NotNull CancellationSupport cancellationSupport) {
        CompletableFuture<Void> allFutures = cancellationSupport
                .execute(CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])));
        return allFutures.thenApply(Void -> {
            List<Hover> mergedDataList = new ArrayList<>(futures.size());
            for (CompletableFuture<Hover> dataListFuture : futures) {
                var data = dataListFuture.join();
                if (data != null) {
                    mergedDataList.add(data);
                }
            }
            return mergedDataList;
        });
    }

    private static CompletableFuture<Hover> getHoverFor(@NotNull HoverParams params,
                                                        @NotNull LanguageServerItem languageServer,
                                                        @NotNull CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                .getTextDocumentService()
                .hover(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_HOVER);
    }

}
