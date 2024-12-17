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
package com.redhat.devtools.lsp4ij.features.callHierarchy;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP prepare call hierarchy support which loads and caches call hierarchy item by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/prepareCallHierarchy' requests</li>
 * </ul>
 */
public class LSPPrepareCallHierarchySupport extends AbstractLSPDocumentFeatureSupport<CallHierarchyPrepareParams, List<CallHierarchyItemData>> {

    private Integer previousOffset;

    public LSPPrepareCallHierarchySupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<CallHierarchyItemData>> getPrepareCallHierarchies(LSPCallHierarchyPrepareParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<CallHierarchyItemData>> doLoad(CallHierarchyPrepareParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getPrepareCallHierarchies(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<CallHierarchyItemData>> getPrepareCallHierarchies(@NotNull PsiFile file,
                                                                                                     @NotNull CallHierarchyPrepareParams params,
                                                                                                     @NotNull CancellationSupport cancellationSupport) {

        return getLanguageServers(file,
                        f -> f.getCallHierarchyFeature().isEnabled(file),
                        f -> f.getCallHierarchyFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have call hierarchy capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/prepareCallHierarchy future for each language servers
                    List<CompletableFuture<List<CallHierarchyItemData>>> callHierarchyPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getCallHierarchiesFor(params, languageServer, file, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/prepareCallHierarchy future in one future which return the list of call hierarchy items
                    return CompletableFutures.mergeInOneFuture(callHierarchyPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<CallHierarchyItemData>> getCallHierarchiesFor(@NotNull CallHierarchyPrepareParams params,
                                                                                   @NotNull LanguageServerItem languageServer,
                                                                                   @NotNull PsiFile file,
                                                                                   @NotNull CancellationSupport cancellationSupport) {

        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .prepareCallHierarchy(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_PREPARE_CALL_HIERARCHY)
                .thenApplyAsync(callHierarchyItems -> {
                    if (callHierarchyItems == null) {
                        // textDocument/prepareCallHierarchy may return null
                        return Collections.emptyList();
                    }
                    List<CallHierarchyItemData> data = new ArrayList<>();
                    callHierarchyItems
                            .stream()
                            .filter(LSPPrepareCallHierarchySupport::isValidCallHierarchyItem)
                            .forEach(callHierarchy -> {
                                var callHierarchyFeature = languageServer.getClientFeatures().getCallHierarchyFeature();
                                if (callHierarchyFeature.getText(callHierarchy) != null) {
                                    data.add(new CallHierarchyItemData(callHierarchy, languageServer));
                                }
                            });
                    return data;
                });
    }

    private static boolean isValidCallHierarchyItem(CallHierarchyItem callHierarchyItem) {
        return Objects.nonNull(callHierarchyItem) && Objects.nonNull(callHierarchyItem.getRange());
    }

}
