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
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP Completion support which loads and caches Completion information by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/completion' requests</li>
 * </ul>
 */
public class LSPCompletionSupport extends AbstractLSPDocumentFeatureSupport<LSPCompletionParams, List<CompletionData>> {

    private Integer previousOffset;

    public LSPCompletionSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<CompletionData>> getCompletions(LSPCompletionParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<CompletionData>> doLoad(@NotNull LSPCompletionParams params,
                                                             @NotNull CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getCompletions(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<CompletionData>> getCompletions(@NotNull PsiFile file,
                                                                                   @NotNull LSPCompletionParams params,
                                                                                   @NotNull CancellationSupport cancellationSupport) {

        return getLanguageServers(file,
                f -> f.getCompletionFeature().isEnabled(file),
                f -> f.getCompletionFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have completion capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/completion future for each language servers
                    List<CompletableFuture<List<CompletionData>>> completionPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getCompletionsFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/completion future in one future which return the list of completion items
                    return CompletableFutures.mergeInOneFuture(completionPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<CompletionData>> getCompletionsFor(@NotNull LSPCompletionParams params,
                                                                             @NotNull PsiFile file,
                                                                             @NotNull LanguageServerItem languageServer,
                                                                             @NotNull CancellationSupport cancellationSupport) {

        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        params.setContext(createCompletionContext(params, file, languageServer));
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .completion(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_COMPLETION)
                .thenApplyAsync(result -> {
                    if (result == null) {
                        // textDocument/completion may return null
                        return Collections.emptyList();
                    }
                    return List.of(new CompletionData(result, languageServer));
                });
    }

    private static CompletionContext createCompletionContext(LSPCompletionParams params, @NotNull PsiFile file, LanguageServerItem languageServer) {
        String completionChar = params.getCompletionChar();
        if (params.isAutoPopup() &&
                languageServer.getClientFeatures().getCompletionFeature().isCompletionTriggerCharactersSupported(file, completionChar)) {
            return new CompletionContext(CompletionTriggerKind.TriggerCharacter, completionChar);
        }
        return new CompletionContext(CompletionTriggerKind.Invoked);
    }


}
