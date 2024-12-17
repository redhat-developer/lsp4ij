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
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP call hierarchy support incomingCalls which loads and caches call hierarchy incomingCalls by consuming:
 *
 * <ul>
 *     <li>LSP 'callHierarchy/incomingCalls' requests</li>
 * </ul>
 */
public class LSPCallHierarchyIncomingCallsSupport extends AbstractLSPDocumentFeatureSupport<CallHierarchyIncomingCallsParams, List<CallHierarchyItemData>> {

    public LSPCallHierarchyIncomingCallsSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<CallHierarchyItemData>> getCallHierarchyIncomingCalls(CallHierarchyIncomingCallsParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<CallHierarchyItemData>> doLoad(CallHierarchyIncomingCallsParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getCallHierarchyIncomingCalls(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<CallHierarchyItemData>> getCallHierarchyIncomingCalls(@NotNull PsiFile file,
                                                                                                         @NotNull CallHierarchyIncomingCallsParams params,
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

                    // Collect list of callHierarchy/incomingCalls future for each language servers
                    List<CompletableFuture<List<CallHierarchyItemData>>> callHierarchyPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getCallHierarchyIncomingCalls(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of callHierarchy/incomingCalls future in one future which return the list of call hierarchy items
                    return CompletableFutures.mergeInOneFuture(callHierarchyPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<CallHierarchyItemData>> getCallHierarchyIncomingCalls(@NotNull CallHierarchyIncomingCallsParams params,
                                                                                                @NotNull LanguageServerItem languageServer,
                                                                                                @NotNull CancellationSupport cancellationSupport) {

        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .callHierarchyIncomingCalls(params), languageServer, LSPRequestConstants.CALL_HIERARCHY_INCOMING_CALLS)
                .thenApplyAsync(callHierarchyIncomingCalls -> {
                    if (callHierarchyIncomingCalls == null) {
                        // callHierarchy/incomingCalls may return null
                        return Collections.emptyList();
                    }
                    List<CallHierarchyItemData> data = new ArrayList<>();
                    callHierarchyIncomingCalls
                            .stream()
                            .map(c -> c.getFrom())
                            .filter(LSPCallHierarchyIncomingCallsSupport::isValidCallHierarchyItem)
                            .forEach(callHierarchyItem -> {
                                var callHierarchyFeature = languageServer.getClientFeatures().getCallHierarchyFeature();
                                if (callHierarchyFeature.getText(callHierarchyItem) != null) {
                                    data.add(new CallHierarchyItemData(callHierarchyItem, languageServer));
                                }
                            });
                    return data;
                });
    }

    private static boolean isValidCallHierarchyItem(CallHierarchyItem callHierarchyItem) {
        return Objects.nonNull(callHierarchyItem) && Objects.nonNull(callHierarchyItem.getRange());
    }

}
