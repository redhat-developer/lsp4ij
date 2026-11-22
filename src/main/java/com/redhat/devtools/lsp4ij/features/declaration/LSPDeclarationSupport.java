/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and declaration
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.declaration;

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
 * LSP declaration support which collect:
 *
 * <ul>
 *      <li>textDocument/declaration</li>
 *  </ul>
 */
public class LSPDeclarationSupport extends AbstractLSPDocumentFeatureSupport<LSPDeclarationParams, List<LocationData>> {

    private Integer previousOffset;

    public LSPDeclarationSupport(@NotNull PsiFile file) {
        super(file, true, true);
    }

    /**
     * Returns the declarations that the language servers found, as textDocument/declaration answers them. A language
     * server which fails, or doesn't answer, adds none: when no language server answers, the future completes with an
     * empty list.
     *
     * @param params the parameters of textDocument/declaration.
     * @return the declarations that the language servers found.
     */
    public CompletableFuture<List<LocationData>> getDeclarations(LSPDeclarationParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    /**
     * Returns the declarations that the language servers answered. It shares the request, and its cache, with
     * {@link #getDeclarations(LSPDeclarationParams)}, but its future fails when no language server answered: a
     * cancelled or failed request has nothing to show. The next call sends again a request that no language server
     * answered.
     *
     * @param params the parameters of textDocument/declaration.
     * @return the declarations that the language servers answered.
     */
    @ApiStatus.Internal
    public synchronized CompletableFuture<List<LocationData>> getAnsweredDeclarations(LSPDeclarationParams params) {
        if (CompletableFutures.isUnanswered(super.getFuture())) {
            // Send the request again, instead of the cached result that no language server answered
            super.cancel();
        }
        return CompletableFutures.answersOf(getDeclarations(params));
    }

    @Override
    protected CompletableFuture<List<LocationData>> doLoad(LSPDeclarationParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return collectDeclarations(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<LocationData>> collectDeclarations(@NotNull PsiFile file,
                                                                                  @NotNull LSPDeclarationParams params,
                                                                                  @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getDeclarationFeature().isEnabled(file),
                f -> f.getDeclarationFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have declaration capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of textDocument/declaration future for each language servers
                    List<CompletableFuture<List<LocationData>>> declarationsPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getDeclarationFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/declaration future in one future which return the list of declaration ranges,
                    // which is a marked empty list when no language server has answered
                    return CompletableFutures.mergeAnswersInOneFuture(declarationsPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<LocationData>> getDeclarationFor(@NotNull LSPDeclarationParams params,
                                                                           @NotNull PsiFile file,
                                                                           @NotNull LanguageServerItem languageServer,
                                                                           @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        var request = languageServer
                .getTextDocumentService()
                .declaration(params);
        return cancellationSupport.execute(request, languageServer, LSPRequestConstants.TEXT_DOCUMENT_DECLARATION)
                // null when the language server hasn't answered: CancellationSupport has reported its error, if any,
                // and completed with null instead
                .thenApplyAsync(locations -> isDoneNormally(request)
                        ? LSPIJUtils.getLocations(locations, languageServer)
                        : null);
    }
}
