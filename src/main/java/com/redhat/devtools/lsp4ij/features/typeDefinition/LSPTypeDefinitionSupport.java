/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and typeDefinition
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.typeDefinition;

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
 * LSP typeDefinition support which collect:
 *
 * <ul>
 *      <li>textDocument/typeDefinition</li>
 *  </ul>
 */
public class LSPTypeDefinitionSupport extends AbstractLSPDocumentFeatureSupport<LSPTypeDefinitionParams, List<Location>> {

    private Integer previousOffset;

    public LSPTypeDefinitionSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<Location>> getTypeDefinitions(LSPTypeDefinitionParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<Location>> doLoad(LSPTypeDefinitionParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return collectTypeDefinitions(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<Location>> collectTypeDefinitions(@NotNull PsiFile file,
                                                                                     @NotNull LSPTypeDefinitionParams params,
                                                                                     @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getTypeDefinitionFeature().isEnabled(file),
                f -> f.getTypeDefinitionFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have typeDefinition capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of textDocument/typeDefinition future for each language servers
                    List<CompletableFuture<List<Location>>> typeDefinitionsPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getTypeDefinitionFor(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/typeDefinition future in one future which return the list of typeDefinition ranges
                    return CompletableFutures.mergeInOneFuture(typeDefinitionsPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<Location>> getTypeDefinitionFor(LSPTypeDefinitionParams params,
                                                                          LanguageServerItem languageServer,
                                                                          CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .typeDefinition(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_TYPE_DEFINITION)
                .thenApplyAsync(locations -> {
                    if (locations == null) {
                        // textDocument/typeDefinition may return null
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
