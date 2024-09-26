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
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP declaration support which collect:
 * 
 * <ul>
 *      <li>textDocument/declaration</li>
 *  </ul>
 */
public class LSPDeclarationSupport extends AbstractLSPDocumentFeatureSupport<LSPDeclarationParams, List<Location>> {

    private Integer previousOffset;

    public LSPDeclarationSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<Location>> getDeclarations(LSPDeclarationParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<Location>> doLoad(LSPDeclarationParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return collectDeclarations(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<Location>> collectDeclarations(@NotNull PsiFile file,
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
                    List<CompletableFuture<List<Location>>> declarationsPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getDeclarationFor(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/declaration future in one future which return the list of declaration ranges
                    return CompletableFutures.mergeInOneFuture(declarationsPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<Location>> getDeclarationFor(LSPDeclarationParams params,
                                                                          LanguageServerItem languageServer,
                                                                          CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .declaration(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_DECLARATION)
                .thenApplyAsync(locations -> {
                    if (locations == null) {
                        // textDocument/declaration may return null
                        return Collections.emptyList();
                    }
                    if (locations.isLeft()) {
                        return locations.getLeft()
                                .stream()
                                .map(l -> new Location(l.getUri(), l.getRange()))
                                .toList();

                    }
                    return locations.getRight()
                            .stream()
                            .map(l -> new Location(l.getTargetUri(), l.getTargetRange()))
                            .toList();
                });
    }

}
