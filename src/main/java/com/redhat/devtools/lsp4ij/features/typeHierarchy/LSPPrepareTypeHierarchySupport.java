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
package com.redhat.devtools.lsp4ij.features.typeHierarchy;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP prepare type hierarchy support which loads and caches type hierarchy item by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/prepareTypeHierarchy' requests</li>
 * </ul>
 */
public class LSPPrepareTypeHierarchySupport extends AbstractLSPDocumentFeatureSupport<TypeHierarchyPrepareParams, List<TypeHierarchyItemData>> {

    private Integer previousOffset;

    public LSPPrepareTypeHierarchySupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<TypeHierarchyItemData>> getPrepareTypeHierarchies(LSPTypeHierarchyPrepareParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<TypeHierarchyItemData>> doLoad(TypeHierarchyPrepareParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getPrepareTypeHierarchies(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<TypeHierarchyItemData>> getPrepareTypeHierarchies(@NotNull PsiFile file,
                                                                                                     @NotNull TypeHierarchyPrepareParams params,
                                                                                                     @NotNull CancellationSupport cancellationSupport) {

        return getLanguageServers(file,
                f -> f.getTypeHierarchyFeature().isEnabled(file),
                f -> f.getTypeHierarchyFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have type hierarchy capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/prepareTypeHierarchy future for each language servers
                    List<CompletableFuture<List<TypeHierarchyItemData>>> typeHierarchyPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getTypeHierarchiesFor(params, languageServer, file, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/prepareTypeHierarchy future in one future which return the list of type hierarchy items
                    return CompletableFutures.mergeInOneFuture(typeHierarchyPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<TypeHierarchyItemData>> getTypeHierarchiesFor(@NotNull TypeHierarchyPrepareParams params,
                                                                                        @NotNull LanguageServerItem languageServer,
                                                                                        @NotNull PsiFile file,
                                                                                        @NotNull CancellationSupport cancellationSupport) {

        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .prepareTypeHierarchy(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_PREPARE_TYPE_HIERARCHY)
                .thenApplyAsync(typeHierarchyItems -> {
                    if (typeHierarchyItems == null) {
                        // textDocument/prepareTypeHierarchy may return null
                        return Collections.emptyList();
                    }
                    List<TypeHierarchyItemData> data = new ArrayList<>();
                    typeHierarchyItems
                            .stream()
                            .filter(LSPPrepareTypeHierarchySupport::isValidTypeHierarchyItem)
                            .forEach(typeHierarchy -> {
                                var typeHierarchyFeature = languageServer.getClientFeatures().getTypeHierarchyFeature();
                                if (typeHierarchyFeature.getText(typeHierarchy) != null) {
                                    data.add(new TypeHierarchyItemData(typeHierarchy, languageServer));
                                }
                            });
                    return data;
                });
    }

    private static boolean isValidTypeHierarchyItem(TypeHierarchyItem typeHierarchyItem) {
        return Objects.nonNull(typeHierarchyItem) && Objects.nonNull(typeHierarchyItem.getRange());
    }

}
