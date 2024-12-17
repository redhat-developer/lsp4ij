/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and definition
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.navigation;

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
 * LSP definition support which collect:
 *
 * <ul>
 *      <li>textDocument/definition</li>
 *  </ul>
 */
public class LSPDefinitionSupport extends AbstractLSPDocumentFeatureSupport<LSPDefinitionParams, List<LocationData>> {

    private Integer previousOffset;

    public LSPDefinitionSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<LocationData>> getDefinitions(LSPDefinitionParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<LocationData>> doLoad(LSPDefinitionParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return collectDefinitions(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<LocationData>> collectDefinitions(@NotNull PsiFile file,
                                                                                 @NotNull LSPDefinitionParams params,
                                                                                 @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getDefinitionFeature().isEnabled(file),
                f -> f.getDefinitionFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have definition capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of textDocument/definition future for each language servers
                    List<CompletableFuture<List<LocationData>>> definitionsPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getDefinitionFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/definition future in one future which return the list of definition ranges
                    return CompletableFutures.mergeInOneFuture(definitionsPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<LocationData>> getDefinitionFor(@NotNull LSPDefinitionParams params,
                                                                      @NotNull PsiFile file,
                                                                      @NotNull LanguageServerItem languageServer,
                                                                      @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .definition(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_DECLARATION)
                .thenApplyAsync(locations -> LSPIJUtils.getLocations(locations, languageServer));
    }
}
