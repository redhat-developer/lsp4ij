/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and reference
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.references;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.eclipse.lsp4j.Location;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;

/**
 * LSP reference support which collect:
 *
 * <ul>
 *      <li>textDocument/reference</li>
 *  </ul>
 */
public class LSPReferenceSupport extends AbstractLSPDocumentFeatureSupport<LSPReferenceParams, List<LocationData>> {

    private Integer previousOffset;

    public LSPReferenceSupport(@NotNull PsiFile file) {
        super(file, true, true);
    }

    /**
     * Returns the references that the language servers found, as textDocument/references answers them. A language
     * server which fails, or doesn't answer, adds none: when no language server answers, the future completes with an
     * empty list.
     *
     * @param params the parameters of textDocument/references.
     * @return the references that the language servers found.
     */
    public CompletableFuture<List<LocationData>> getReferences(LSPReferenceParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    /**
     * Returns the references that the language servers answered. It shares the request, and its cache, with
     * {@link #getReferences(LSPReferenceParams)}, but its future fails when no language server answered: a cancelled or
     * failed request has nothing to show. The next call sends again a request that no language server answered.
     *
     * @param params the parameters of textDocument/references.
     * @return the references that the language servers answered.
     */
    @ApiStatus.Internal
    public synchronized CompletableFuture<List<LocationData>> getAnsweredReferences(LSPReferenceParams params) {
        if (CompletableFutures.isUnanswered(super.getFuture())) {
            // Send the request again, instead of the cached result that no language server answered
            super.cancel();
        }
        return CompletableFutures.answersOf(getReferences(params));
    }

    @Override
    protected CompletableFuture<List<LocationData>> doLoad(LSPReferenceParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return collectReferences(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<LocationData>> collectReferences(@NotNull PsiFile file,
                                                                                @NotNull LSPReferenceParams params,
                                                                                @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getReferencesFeature().isEnabled(file),
                f -> f.getReferencesFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have reference capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of textDocument/reference future for each language servers
                    List<CompletableFuture<List<LocationData>>> referencesPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getReferenceFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/reference future in one future which return the list of reference ranges,
                    // which is a marked empty list when no language server has answered
                    return CompletableFutures.mergeAnswersInOneFuture(referencesPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<LocationData>> getReferenceFor(@NotNull LSPReferenceParams params,
                                                                     @NotNull PsiFile file,
                                                                     @NotNull LanguageServerItem languageServer,
                                                                     @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        var request = languageServer
                .getTextDocumentService()
                .references(params);
        return cancellationSupport.execute(request, languageServer, LSPRequestConstants.TEXT_DOCUMENT_REFERENCES)
                .thenApplyAsync(locations -> {
                    if (!isDoneNormally(request)) {
                        // The language server hasn't answered: CancellationSupport has reported its error, if any,
                        // and completed with null instead
                        return null;
                    }
                    if (locations == null) {
                        // textDocument/reference may return null
                        return Collections.emptyList();
                    }
                    return locations
                            .stream()
                            .map(l -> new LocationData(new Location(l.getUri(), l.getRange()), languageServer))
                            .toList();
                });
    }

}
