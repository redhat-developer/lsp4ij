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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;

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
        super(file, true, true);
    }

    /**
     * Returns the implementations that the language servers found, as textDocument/implementation answers them. A
     * language server which fails, or doesn't answer, adds none: when no language server answers, the future completes
     * with an empty list, and with null when no language server can be asked.
     *
     * @param params the parameters of textDocument/implementation.
     * @return the implementations that the language servers found.
     */
    public CompletableFuture<List<LocationData>> getImplementations(LSPImplementationParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    /**
     * Returns the implementations that the language servers answered. It shares the request, and its cache, with
     * {@link #getImplementations(LSPImplementationParams)}, but its future fails when no language server answered: a
     * cancelled or failed request has nothing to show. The next call sends again a request that no language server
     * answered.
     *
     * @param params the parameters of textDocument/implementation.
     * @return the implementations that the language servers answered.
     */
    @ApiStatus.Internal
    public synchronized CompletableFuture<List<LocationData>> getAnsweredImplementations(LSPImplementationParams params) {
        if (CompletableFutures.isUnanswered(super.getFuture())) {
            // Send the request again, instead of the cached result that no language server answered
            super.cancel();
        }
        return CompletableFutures.answersOf(getImplementations(params));
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
                        // No language server can be asked: null, as the public method gave before, which
                        // getAnsweredImplementations doesn't take for an answer
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of textDocument/implementation future for each language servers
                    List<CompletableFuture<List<LocationData>>> implementationsPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getImplementationFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/implementation future in one future which return the list of implementation ranges,
                    // which is a marked empty list when no language server has answered
                    return CompletableFutures.mergeAnswersInOneFuture(implementationsPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<LocationData>> getImplementationFor(@NotNull LSPImplementationParams params,
                                                                          @NotNull PsiFile file,
                                                                          @NotNull LanguageServerItem languageServer,
                                                                          @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        var request = languageServer
                .getTextDocumentService()
                .implementation(params);
        return cancellationSupport.execute(request, languageServer, LSPRequestConstants.TEXT_DOCUMENT_IMPLEMENTATION)
                // null when the language server hasn't answered: CancellationSupport has reported its error, if any,
                // and completed with null instead
                .thenApplyAsync(locations -> isDoneNormally(request)
                        ? LSPIJUtils.getLocations(locations, languageServer)
                        : null);
    }
}
