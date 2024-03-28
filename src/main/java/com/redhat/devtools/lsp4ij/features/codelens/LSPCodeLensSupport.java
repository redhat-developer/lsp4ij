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
package com.redhat.devtools.lsp4ij.features.codelens;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.features.AbstractLSPFeatureSupport;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.features.codelens.LSPCodeLensProvider.getCodeLensContent;

/**
 * LSP codeLens support which loads and caches code lenses by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/codeLens' requests</li>
 *     <li>LSP 'codeLens/resolve' requests</li>
 * </ul>
 */
public class LSPCodeLensSupport extends AbstractLSPFeatureSupport<CodeLensParams, List<CodeLensData>> {
    private final CodeLensParams params;

    public LSPCodeLensSupport(@NotNull PsiFile file) {
        super(file);
        this.params = new CodeLensParams(LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile()));
    }

    public CompletableFuture<List<CodeLensData>> getCodeLenses() {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<CodeLensData>> doLoad(CodeLensParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getCodeLenses(file.getVirtualFile(), file.getProject(), params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<CodeLensData>> getCodeLenses(@NotNull VirtualFile file,
                                                                                @NotNull Project project,
                                                                                @NotNull CodeLensParams params,
                                                                                @NotNull CancellationSupport cancellationSupport) {

        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, LanguageServerItem::isCodeLensSupported)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have code lens capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedStage(Collections.emptyList());
                    }

                    // Collect list of textDocument/codeLens future for each language servers
                    List<CompletableFuture<List<CodeLensData>>> codeLensPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getCodeLensesFor(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/codelens future in one future which return the list of code lenses
                    return CompletableFutures.mergeInOneFuture(codeLensPerServerFutures, cancellationSupport);
                })
                .thenApply(codeLensData -> {
                    // Sort codelens by line number
                    codeLensData.sort(LSPCodeLensProvider::sortCodeLensByLine);
                    return codeLensData;
                });
    }

    private static CompletableFuture<List<CodeLensData>> getCodeLensesFor(CodeLensParams params, LanguageServerItem languageServer, CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .codeLens(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_CODE_LENS)
                .thenApplyAsync(codeLenses -> {
                    if (codeLenses == null) {
                        // textDocument/codeLens may return null
                        return Collections.emptyList();
                    }
                    List<CodeLensData> data = new ArrayList<>();
                    codeLenses
                            .stream()
                            .filter(LSPCodeLensSupport::isValidCodeLens)
                            .forEach(codeLens -> {
                                CompletableFuture<CodeLens> resolvedCodeLensFuture = null;
                                if (codeLens.getCommand() == null && languageServer.isResolveCodeLensSupported()) {
                                    // - the codelens has no command, and the language server supports codeLens/resolve
                                    // prepare the future which resolves the codelens.
                                    resolvedCodeLensFuture = cancellationSupport.execute(languageServer
                                            .getTextDocumentService()
                                            .resolveCodeLens(codeLens), languageServer, LSPRequestConstants.TEXT_DOCUMENT_RESOLVE_CODE_LENS);
                                }
                                if (getCodeLensContent(codeLens) != null || resolvedCodeLensFuture != null) {
                                    // The codelens content is filled or the codelens must be resolved
                                    data.add(new CodeLensData(codeLens, languageServer, resolvedCodeLensFuture));
                                }
                            });
                    return data;
                });
    }

    private static boolean isValidCodeLens(CodeLens codeLens) {
        return Objects.nonNull(codeLens) && Objects.nonNull(codeLens.getRange());
    }
}
