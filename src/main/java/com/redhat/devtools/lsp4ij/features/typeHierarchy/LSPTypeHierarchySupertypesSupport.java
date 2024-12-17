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
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP type hierarchy supertypes support which loads and caches type hierarchy item by consuming:
 *
 * <ul>
 *     <li>LSP 'typeHierarchy/supertypes' requests</li>
 * </ul>
 */
public class LSPTypeHierarchySupertypesSupport extends AbstractLSPDocumentFeatureSupport<TypeHierarchySupertypesParams, List<TypeHierarchyItemData>> {

    public LSPTypeHierarchySupertypesSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<TypeHierarchyItemData>> getTypeHierarchySupertypes(TypeHierarchySupertypesParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<TypeHierarchyItemData>> doLoad(TypeHierarchySupertypesParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getTypeHierarchySupertypes(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<TypeHierarchyItemData>> getTypeHierarchySupertypes(@NotNull PsiFile file,
                                                                                                      @NotNull TypeHierarchySupertypesParams params,
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

                    // Collect list of typeHierarchy/supertypes future for each language servers
                    List<CompletableFuture<List<TypeHierarchyItemData>>> typeHierarchyPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getTypeHierarchiesFor(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of typeHierarchy/supertypes future in one future which return the list of type hierarchy items
                    return CompletableFutures.mergeInOneFuture(typeHierarchyPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<TypeHierarchyItemData>> getTypeHierarchiesFor(@NotNull TypeHierarchySupertypesParams params,
                                                                                        @NotNull LanguageServerItem languageServer,
                                                                                        @NotNull CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .typeHierarchySupertypes(params), languageServer, LSPRequestConstants.TYPE_HIERARCHY_SUPER_TYPES)
                .thenApplyAsync(typeHierarchyItems -> {
                    if (typeHierarchyItems == null) {
                        // typeHierarchy/supertypes may return null
                        return Collections.emptyList();
                    }
                    List<TypeHierarchyItemData> data = new ArrayList<>();
                    typeHierarchyItems
                            .stream()
                            .filter(LSPTypeHierarchySupertypesSupport::isValidTypeHierarchyItem)
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
