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
package com.redhat.devtools.lsp4ij.features.implementation;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP implementation support which collect:
 *
 * <ul>
 *      <li>textDocument/implementation</li>
 *  </ul>
 */
public class LSPImplementationSupport extends AbstractLSPDocumentFeatureSupport<LSPImplementationParams, List<LocationData>> {

    private Integer previousOffset;

    public LSPImplementationSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<LocationData>> getImplementations(LSPImplementationParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<LocationData>> doLoad(LSPImplementationParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return collectImplementations(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<LocationData>> collectImplementations(@NotNull PsiFile file,
                                                                                     @NotNull LSPImplementationParams params,
                                                                                     @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getImplementationFeature().isEnabled(file),
                f -> f.getImplementationFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have implementation capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of textDocument/implementation future for each language servers
                    List<CompletableFuture<List<LocationData>>> implementationsPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getImplementationFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/implementation future in one future which return the list of implementation ranges
                    return CompletableFutures.mergeInOneFuture(implementationsPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<LocationData>> getImplementationFor(@NotNull LSPImplementationParams params,
                                                                          @NotNull PsiFile file,
                                                                          @NotNull LanguageServerItem languageServer,
                                                                          @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .implementation(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_IMPLEMENTATION)
                .thenApplyAsync(locations -> LSPIJUtils.getLocations(locations, languageServer));
    }
}
