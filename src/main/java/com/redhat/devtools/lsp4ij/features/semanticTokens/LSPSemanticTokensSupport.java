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
package com.redhat.devtools.lsp4ij.features.semanticTokens;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP semanticTokens support which loads and caches semantic tokens by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/semanticTokens' requests</li>
 * </ul>
 */
public class LSPSemanticTokensSupport extends AbstractLSPDocumentFeatureSupport<SemanticTokensParams, SemanticTokensData> {

    private static final SemanticTokensLegend DEFAULT_LEGEND;

    static {
        DEFAULT_LEGEND = new SemanticTokensLegend();
        DEFAULT_LEGEND.setTokenModifiers(Collections.emptyList());
        DEFAULT_LEGEND.setTokenTypes(Collections.emptyList());
    }

    public LSPSemanticTokensSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<SemanticTokensData> getSemanticTokens(SemanticTokensParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<SemanticTokensData> doLoad(SemanticTokensParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getSemanticTokens(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<SemanticTokensData> getSemanticTokens(@NotNull PsiFile file,
                                                                                    @NotNull SemanticTokensParams params,
                                                                                    @NotNull CancellationSupport cancellationSupport) {

        return getLanguageServers(file,
                f -> f.getSemanticTokensFeature().isEnabled(file),
                f -> f.getSemanticTokensFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have folding range capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of textDocument/semanticTokens future for each language servers
                    List<CompletableFuture<SemanticTokensData>> semanticTokensPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getSemanticTokensFor(params, file, languageServer, cancellationSupport))
                            .filter(Objects::nonNull)
                            .toList();

                    // Merge list of textDocument/foldingRange future in one future which return the list of folding ranges
                    return semanticTokensPerServerFutures.get(0); //CompletableFutures.mergeInOneFuture(semanticTokensPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<SemanticTokensData> getSemanticTokensFor(@NotNull SemanticTokensParams params,
                                                                              @NotNull PsiFile file,
                                                                              @NotNull LanguageServerItem languageServer,
                                                                              @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .semanticTokensFull(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_SEMANTIC_TOKENS_FULL)
                .thenApplyAsync(semanticTokens -> {
                    if (semanticTokens == null) {
                        // textDocument/semanticTokens/full may return null
                        return null;
                    }
                    return new SemanticTokensData(semanticTokens, getLegend(languageServer), languageServer.getSemanticTokensColorsProvider());
                });
    }

    @NotNull
    private static SemanticTokensLegend getLegend(LanguageServerItem languageServer) {
        var semanticTokenFeature = languageServer.getClientFeatures().getSemanticTokensFeature();
        SemanticTokensLegend legend = semanticTokenFeature.getLegend();
        if (legend != null) {
            return legend;
        }
        var serverCapabilities = languageServer.getServerCapabilities();
        if (serverCapabilities != null &&
                serverCapabilities.getSemanticTokensProvider() != null &&
                serverCapabilities.getSemanticTokensProvider().getLegend() != null) {
            return serverCapabilities.getSemanticTokensProvider().getLegend();
        }
        return DEFAULT_LEGEND;
    }

}
