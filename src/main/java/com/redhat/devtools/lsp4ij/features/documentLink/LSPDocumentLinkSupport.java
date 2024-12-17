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
package com.redhat.devtools.lsp4ij.features.documentLink;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP document link support which loads and caches document link by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/documentLink' requests</li>
 * </ul>
 */
public class LSPDocumentLinkSupport extends AbstractLSPDocumentFeatureSupport<DocumentLinkParams, List<DocumentLinkData>> {

    public LSPDocumentLinkSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<DocumentLinkData>> getDocumentLinks(DocumentLinkParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<DocumentLinkData>> doLoad(@NotNull DocumentLinkParams params,
                                                               @NotNull CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getDocumentLinks(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<DocumentLinkData>> getDocumentLinks(@NotNull PsiFile file,
                                                                                       @NotNull DocumentLinkParams params,
                                                                                       @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getDocumentLinkFeature().isEnabled(file),
                f -> f.getDocumentLinkFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have document link capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/documentLink future for each language servers
                    List<CompletableFuture<List<DocumentLinkData>>> linkInformationPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getDocumentLinksFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/documentLink future in one future which return the list of document link
                    return CompletableFutures.mergeInOneFuture(linkInformationPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<DocumentLinkData>> getDocumentLinksFor(@NotNull DocumentLinkParams params,
                                                                                 @NotNull PsiFile file,
                                                                                 @NotNull LanguageServerItem languageServer,
                                                                                 @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .documentLink(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_LINK)
                .thenApplyAsync(documentLink -> {
                    if (documentLink == null) {
                        // textDocument/colorInformation may return null
                        return Collections.emptyList();
                    }
                    return documentLink.stream()
                            .filter(Objects::nonNull)
                            .map(color -> new DocumentLinkData(color, languageServer))
                            .toList();
                });
    }


}
