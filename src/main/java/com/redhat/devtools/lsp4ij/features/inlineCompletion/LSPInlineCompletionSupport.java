/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlineCompletion;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.InlineCompletionContext;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionList;
import org.eclipse.lsp4j.InlineCompletionParams;
import org.eclipse.lsp4j.InlineCompletionTriggerKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP Inline Completion support which loads and caches inline completion information by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/inlineCompletion' requests</li>
 * </ul>
 */
public class LSPInlineCompletionSupport extends AbstractLSPDocumentFeatureSupport<LSPInlineCompletionParams, List<InlineCompletionData>> {

    private LSPInlineCompletionParams previousParams;

    public LSPInlineCompletionSupport(@NotNull PsiFile file) {
        super(file);
    }

    public synchronized CompletableFuture<List<InlineCompletionData>> getInlineCompletions(LSPInlineCompletionParams params) {
        // At the same caret position, an explicit invocation or another selected popup
        // item is a different request and must not reuse an automatic suggestion.
        if (!params.equals(previousParams)) {
            super.cancel();
        }
        previousParams = params;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<InlineCompletionData>> doLoad(@NotNull LSPInlineCompletionParams params,
                                                                   @NotNull CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getInlineCompletions(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<InlineCompletionData>> getInlineCompletions(@NotNull PsiFile file,
                                                                                               @NotNull LSPInlineCompletionParams params,
                                                                                               @NotNull CancellationSupport cancellationSupport) {

        return getLanguageServers(file,
                f -> f.getInlineCompletionFeature().isEnabled(file),
                f -> f.getInlineCompletionFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have inline completion capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/inlineCompletion future for each language servers
                    List<CompletableFuture<List<InlineCompletionData>>> inlineCompletionPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getInlineCompletionsFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/inlineCompletion future in one future which return the list of inline completion items
                    return CompletableFutures.mergeInOneFuture(inlineCompletionPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<InlineCompletionData>> getInlineCompletionsFor(@NotNull LSPInlineCompletionParams params,
                                                                                         @NotNull PsiFile file,
                                                                                         @NotNull LanguageServerItem languageServer,
                                                                                         @NotNull CancellationSupport cancellationSupport) {

        // Each server may map the file URI differently. Keep the caller's context and
        // cache key unchanged while preparing this server's request.
        var identifier = new TextDocumentIdentifier(params.getTextDocument().getUri());
        updateTextDocumentUri(identifier, file, languageServer);
        var context = params.getContext() != null ? params.getContext()
                : new InlineCompletionContext(InlineCompletionTriggerKind.Automatic);
        var request = new InlineCompletionParams(identifier, params.getPosition(), context);
        request.setWorkDoneToken(params.getWorkDoneToken());

        // Call textDocument/inlineCompletion
        return cancellationSupport.execute(
                languageServer
                        .getTextDocumentService()
                        .inlineCompletion(request),
                languageServer,
                LSPRequestConstants.TEXT_DOCUMENT_INLINE_COMPLETION)
                .thenApplyAsync(result -> {
                    if (result == null) {
                        // textDocument/inlineCompletion may return null
                        return Collections.emptyList();
                    }
                    return List.of(new InlineCompletionData(result, languageServer));
                });
    }

}
