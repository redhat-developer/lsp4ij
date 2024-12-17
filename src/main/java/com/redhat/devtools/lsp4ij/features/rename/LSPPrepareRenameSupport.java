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
package com.redhat.devtools.lsp4ij.features.rename;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP prepare rename support which loads and caches prepare rename by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/prepareRename' requests</li>
 * </ul>
 */
public class LSPPrepareRenameSupport extends AbstractLSPDocumentFeatureSupport<LSPPrepareRenameParams, List<PrepareRenameResultData>> {
    private int previousOffset = -1;

    public LSPPrepareRenameSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<PrepareRenameResultData>> getPrepareRenameResult(LSPPrepareRenameParams params) {
        int offset = params.getOffset();
        if (previousOffset != offset) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<PrepareRenameResultData>> doLoad(@NotNull LSPPrepareRenameParams params,
                                                                      @NotNull CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getPrepareRenameResult(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<PrepareRenameResultData>> getPrepareRenameResult(@NotNull PsiFile file,
                                                                                                    @NotNull LSPPrepareRenameParams params,
                                                                                                    @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getRenameFeature().isEnabled(file),
                f -> f.getRenameFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have 'rename' support
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    List<CompletableFuture<List<PrepareRenameResultData>>> prepareRenamePerServerFutures = new ArrayList<>();
                    DefaultPrepareRenameResultProvider defaultPrepareRenameResult = new DefaultPrepareRenameResultProvider(params);
                    for (var languageServer : languageServers) {
                        CompletableFuture<List<PrepareRenameResultData>> future = null;
                        if (languageServer.isPrepareRenameSupported()) {
                            future = getPrepareRenamesFor(params, file, defaultPrepareRenameResult, languageServer, cancellationSupport);
                        } else {
                            var result = defaultPrepareRenameResult.apply(languageServer);
                            if (result != null) {
                                prepareRenamePerServerFutures.add(CompletableFuture.completedFuture(List.of(result)));
                            }
                        }
                        if (future != null) {
                            // The rename has been done in a valid location
                            // ex : foo.ba|r(), the prepare rename future is added ('bar' as placeholder)
                            prepareRenamePerServerFutures.add(future);
                        }
                    }
                    // Merge list of textDocument/prepareRename future in one future which return the list of color information
                    return CompletableFutures.mergeInOneFuture(prepareRenamePerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<PrepareRenameResultData>> getPrepareRenamesFor(@NotNull PrepareRenameParams params,
                                                                                         @NotNull PsiFile file,
                                                                                         @NotNull DefaultPrepareRenameResultProvider defaultPrepareRenameResultProvider,
                                                                                         @NotNull LanguageServerItem languageServer,
                                                                                         @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                                .getTextDocumentService()
                                .prepareRename(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_PREPARE_RENAME,
                        false /* if prepare name throws an error, the error must not be displayed as notification but as hint in the editor  */)
                .thenApplyAsync(prepareRename -> {
                    PrepareRenameResultData result = getPrepareRenameResultData(defaultPrepareRenameResultProvider, languageServer, prepareRename);
                    return result != null ? List.of(result) : Collections.emptyList();
                });
    }

    @Nullable
    private static PrepareRenameResultData getPrepareRenameResultData(@NotNull DefaultPrepareRenameResultProvider defaultPrepareRenameResultProvider,
                                                                      @NotNull LanguageServerItem languageServer,
                                                                      @Nullable Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> prepareRename) {
        // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareRename

        if (prepareRename == null) {
            // The language server supports prepare rename,
            // and the textDocument/prepareRename returns nothing,
            // The element cannot be renamed.
            return null;
        }

        if (prepareRename.isThird()) {
            // PrepareRenameDefaultBehavior
            return defaultPrepareRenameResultProvider.apply(languageServer);
        }

        Range range = null;
        String placeholder = null;
        if (prepareRename.isFirst()) {
            // Range
            range = prepareRename.getFirst();
        } else if (prepareRename.isSecond()) {
            // PrepareRenameResult
            PrepareRenameResult prepareRenameResult = prepareRename.getSecond();
            range = prepareRenameResult.getRange();
            placeholder = prepareRenameResult.getPlaceholder();
        }
        var document = defaultPrepareRenameResultProvider.getDocument();
        var textRange = range != null ? LSPIJUtils.toTextRange(range, document) : defaultPrepareRenameResultProvider.getTextRange();
        if (textRange == null) {
            // Invalid text range
            // ex: the rename is done in spaces or an empty file
            return null;
        }
        if (placeholder == null) {
            placeholder = document.getText(textRange);
        }
        return new PrepareRenameResultData(textRange, placeholder, languageServer);
    }


}
