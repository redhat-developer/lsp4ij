/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
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
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP color presentation support which provides textual color representations by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/colorPresentation' requests</li>
 * </ul>
 */
public class LSPColorPresentationSupport extends AbstractLSPDocumentFeatureSupport<ColorPresentationParams, List<ColorPresentation>> {

    public LSPColorPresentationSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<ColorPresentation>> getColorPresentations(@NotNull ColorPresentationParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<ColorPresentation>> doLoad(@NotNull ColorPresentationParams params,
                                                                @NotNull CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getColorPresentations(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<ColorPresentation>> getColorPresentations(@NotNull PsiFile file,
                                                                                              @NotNull ColorPresentationParams params,
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

                    // Collect list of textDocument/colorPresentation future for each language servers
                    List<CompletableFuture<List<ColorPresentation>>> colorPresentationPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getColorPresentationsFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/colorPresentation future in one future which return the list of color presentations
                    return CompletableFutures.mergeInOneFuture(colorPresentationPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<ColorPresentation>> getColorPresentationsFor(@NotNull ColorPresentationParams params,
                                                                                        @NotNull PsiFile file,
                                                                                        @NotNull LanguageServerItem languageServer,
                                                                                        @NotNull CancellationSupport cancellationSupport) {

        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .colorPresentation(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_COLOR_PRESENTATION)
                .thenApplyAsync(presentations -> {
                    if (presentations == null) {
                        // textDocument/colorPresentation may return null
                        return Collections.emptyList();
                    }
                    return presentations.stream()
                            .filter(Objects::nonNull)
                            .toList();
                });
    }
}
