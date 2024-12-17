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
package com.redhat.devtools.lsp4ij.features.color;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.DocumentColorParams;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP color support which loads and caches color information by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/documentColor' requests</li>
 * </ul>
 */
public class LSPColorSupport extends AbstractLSPDocumentFeatureSupport<DocumentColorParams, List<ColorData>> {

    public LSPColorSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<ColorData>> getColors(DocumentColorParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<ColorData>> doLoad(@NotNull DocumentColorParams params,
                                                        @NotNull CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getColors(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<ColorData>> getColors(@NotNull PsiFile file,
                                                                         @NotNull DocumentColorParams params,
                                                                         @NotNull CancellationSupport cancellationSupport) {

        return getLanguageServers(file,
                        f -> f.getDocumentColorFeature().isEnabled(file),
                        f -> f.getDocumentColorFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have color capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/documentColor future for each language servers
                    List<CompletableFuture<List<ColorData>>> colorInformationPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getColorsFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/documentColor future in one future which return the list of color information
                    return CompletableFutures.mergeInOneFuture(colorInformationPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<ColorData>> getColorsFor(@NotNull DocumentColorParams params,
                                                                   @NotNull PsiFile file,
                                                                   @NotNull LanguageServerItem languageServer,
                                                                   @NotNull CancellationSupport cancellationSupport) {

        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .documentColor(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_COLOR)
                .thenApplyAsync(colorInformation -> {
                    if (colorInformation == null) {
                        // textDocument/colorInformation may return null
                        return Collections.emptyList();
                    }
                    return colorInformation.stream()
                            .filter(Objects::nonNull)
                            .map(color -> new ColorData(color, languageServer))
                            .toList();
                });
    }


}
