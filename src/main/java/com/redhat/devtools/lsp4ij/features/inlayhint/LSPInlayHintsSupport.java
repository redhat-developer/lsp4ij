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
package com.redhat.devtools.lsp4ij.features.inlayhint;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP inlayHint support which loads and caches inlay hints by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/inlayHint' requests</li>
 *     <li>LSP 'inlayHint/resolve' requests</li>
 * </ul>
 */
public class LSPInlayHintsSupport extends AbstractLSPDocumentFeatureSupport<InlayHintParams, List<InlayHintData>> {

    public LSPInlayHintsSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<InlayHintData>> getInlayHints(InlayHintParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<InlayHintData>> doLoad(InlayHintParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getInlayHints(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<InlayHintData>> getInlayHints(@NotNull PsiFile file,
                                                                                 @NotNull InlayHintParams params,
                                                                                 @NotNull CancellationSupport cancellationSupport) {

        return getLanguageServers(file,
                f -> f.getInlayHintFeature().isEnabled(file),
                f -> f.getInlayHintFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have inlay hint capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedStage(Collections.emptyList());
                    }

                    // Collect list of textDocument/inlayHint future for each language servers
                    List<CompletableFuture<List<InlayHintData>>> inlayHintPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getInlayHintsFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/inlayHint future in one future which return the list of inlay hints
                    return CompletableFutures.mergeInOneFuture(inlayHintPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<InlayHintData>> getInlayHintsFor(@NotNull InlayHintParams params,
                                                                           @NotNull PsiFile file,
                                                                           @NotNull LanguageServerItem languageServer,
                                                                           @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .inlayHint(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_INLAY_HINT)
                .thenApplyAsync(inlayHints -> {
                    if (inlayHints == null) {
                        // textDocument/inlayHint may return null
                        return Collections.emptyList();
                    }
                    List<InlayHintData> data = new ArrayList<>();
                    inlayHints.stream()
                            .filter(Objects::nonNull)
                            .forEach(inlayHint -> {
                                CompletableFuture<InlayHint> resolvedInlayHintFuture = null;
                                if (inlayHint.getLabel() == null && languageServer.getClientFeatures().getInlayHintFeature().isResolveInlayHintSupported(file)) {
                                    // - the inlayHint has no label, and the language server supports inlayHint/resolve
                                    // prepare the future which resolves the inlayHint.
                                    resolvedInlayHintFuture = cancellationSupport.execute(languageServer
                                            .getTextDocumentService()
                                            .resolveInlayHint(inlayHint), languageServer, LSPRequestConstants.INLAY_HINT_RESOLVE);
                                }
                                if (inlayHint.getLabel() != null || resolvedInlayHintFuture != null) {
                                    // The inlayHint content is filled or the inlayHint must be resolved
                                    data.add(new InlayHintData(inlayHint, languageServer, resolvedInlayHintFuture));
                                }
                            });
                    return data;
                });
    }


}
