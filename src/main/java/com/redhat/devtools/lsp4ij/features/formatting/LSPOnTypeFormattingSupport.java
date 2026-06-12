/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.DocumentContentSynchronizer;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.OpenedDocument;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP on-type formatting support which uses:
 *
 * <ul>
 *     <li>LSP 'textDocument/onTypeFormatting' requests</li>
 * </ul>
 */
public class LSPOnTypeFormattingSupport extends AbstractLSPDocumentFeatureSupport<DocumentOnTypeFormattingParams, List<TextEdit>> {

    public LSPOnTypeFormattingSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<TextEdit>> doLoad(DocumentOnTypeFormattingParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return onTypeFormatting(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<TextEdit>> onTypeFormatting(@NotNull PsiFile file,
                                                                               @NotNull DocumentOnTypeFormattingParams params,
                                                                               @NotNull CancellationSupport cancellationSupport) {
        String charTyped = params.getCh();
        return getLanguageServers(file,
                f -> f.getOnTypeFormattingFeature().isEnabled(file) && f.getOnTypeFormattingFeature().isOnTypeFormattingTriggerCharacter(file, charTyped),
                f -> f.getOnTypeFormattingFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have on-type formatting capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Flush pending didChange notifications before sending onTypeFormatting requests
                    // to ensure the server has an up-to-date document with the typed character already synced.
                    // This prevents out-of-bounds position errors (issue #1589).
                    return flushPendingChangesForLanguageServers(file, languageServers)
                            .thenCompose(v -> {
                                // Collect list of textDocument/onTypeFormatting future for each language servers
                                List<CompletableFuture<List<TextEdit>>> textEditsPerServerFutures = languageServers
                                        .stream()
                                        .map(languageServer -> getTextEditsFor(params, file, languageServer, cancellationSupport))
                                        .toList();

                                // Merge list of textDocument/onTypeFormatting future in one future which return the list of text edits
                                return CompletableFutures.mergeInOneFuture(textEditsPerServerFutures, cancellationSupport);
                            });
                });
    }

    private static CompletableFuture<List<TextEdit>> getTextEditsFor(@NotNull DocumentOnTypeFormattingParams params,
                                                                     @NotNull PsiFile file,
                                                                     @NotNull LanguageServerItem languageServer,
                                                                     @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .onTypeFormatting(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_ON_TYPE_FORMATTING)
                .thenApplyAsync(textEdits -> {
                    if (textEdits == null) {
                        // textDocument/onTypeFormatting may return null
                        return Collections.emptyList();
                    }
                    List<TextEdit> filteredTextEdits = new ArrayList<>(textEdits.size());
                    ContainerUtil.addAllNotNull(filteredTextEdits, textEdits);
                    return filteredTextEdits;
                });
    }

    /**
     * Flushes pending didChange notifications for all language servers that will handle the onTypeFormatting request.
     * This ensures each server has received the typed character before receiving the formatting request,
     * preventing out-of-bounds position errors.
     *
     * @param file the PSI file
     * @param languageServers the language servers that will handle the onTypeFormatting request
     * @return a CompletableFuture that completes when all didChange notifications have been sent
     */
    private static CompletableFuture<Void> flushPendingChangesForLanguageServers(@NotNull PsiFile file,
                                                                                   @NotNull List<LanguageServerItem> languageServers) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> flushFutures = new ArrayList<>();

        for (LanguageServerItem languageServer : languageServers) {
            URI fileUri = FileUriSupport.getFileUri(virtualFile, languageServer.getClientFeatures());
            if (fileUri == null) {
                continue;
            }

            OpenedDocument openedDocument = languageServer.getServerWrapper().getOpenedDocument(fileUri);
            if (openedDocument == null) {
                continue;
            }

            DocumentContentSynchronizer synchronizer = openedDocument.getSynchronizer();
            if (synchronizer != null) {
                flushFutures.add(synchronizer.flushPendingChanges());
            }
        }

        // Wait for all flush operations to complete
        return CompletableFuture.allOf(flushFutures.toArray(new CompletableFuture[0]));
    }

}
