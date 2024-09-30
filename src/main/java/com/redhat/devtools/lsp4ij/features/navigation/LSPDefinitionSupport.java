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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP definition support which collect:
 *
 * <ul>
 *      <li>textDocument/definition</li>
 *  </ul>
 */
public class LSPDefinitionSupport extends AbstractLSPDocumentFeatureSupport<LSPDefinitionParams, List<Location>> {

    private Integer previousOffset;

    public LSPDefinitionSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<Location>> getDefinitions(LSPDefinitionParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<Location>> doLoad(LSPDefinitionParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return collectDefinitions(file.getVirtualFile(), file.getProject(), params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<Location>> collectDefinitions(@NotNull VirtualFile file,
                                                                                 @NotNull Project project,
                                                                                 @NotNull LSPDefinitionParams params,
                                                                                 @NotNull CancellationSupport cancellationSupport) {
        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, LanguageServerItem::isDefinitionSupported)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have definition capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of textDocument/definition future for each language servers
                    List<CompletableFuture<List<Location>>> definitionsPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getDefinitionFor(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/definition future in one future which return the list of definition ranges
                    return CompletableFutures.mergeInOneFuture(definitionsPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<Location>> getDefinitionFor(LSPDefinitionParams params,
                                                                      LanguageServerItem languageServer,
                                                                      CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .definition(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_DECLARATION)
                .thenApplyAsync(locations -> {
                    if (locations == null) {
                        // textDocument/definition may return null
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
