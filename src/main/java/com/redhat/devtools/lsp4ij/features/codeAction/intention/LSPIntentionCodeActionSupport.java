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
package com.redhat.devtools.lsp4ij.features.codeAction.intention;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.AbstractLSPFeatureSupport;
import com.redhat.devtools.lsp4ij.features.codeAction.CodeActionData;
import com.redhat.devtools.lsp4ij.features.codeAction.LSPLazyCodeActionProvider;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP code action support which loads and caches code actions by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/codeAction' requests</li>
 * </ul>
 */
public class LSPIntentionCodeActionSupport extends AbstractLSPFeatureSupport<CodeActionParams, List<CodeActionData>> implements LSPLazyCodeActionProvider {

    private CodeActionParams previousParams;

    public LSPIntentionCodeActionSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<CodeActionData>> getCodeActions(CodeActionParams params) {
        if (previousParams != null && !previousParams.equals(params)) {
            // the caret editor has changed, cancel teh previous load of textDocument/codeAction.
            super.cancel();
        }
        previousParams = params;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<CodeActionData>> doLoad(@NotNull CodeActionParams params,
                                                             @NotNull CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getCodeActions(file.getVirtualFile(), file.getProject(), params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<CodeActionData>> getCodeActions(@NotNull VirtualFile file,
                                                                                   @NotNull Project project,
                                                                                   @NotNull CodeActionParams params,
                                                                                   @NotNull CancellationSupport cancellationSupport) {

        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, LanguageServerItem::isCodeActionSupported)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have code action capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/codeAction future for each language servers
                    List<CompletableFuture<List<CodeActionData>>> codeActionPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getCodeActionsFor(params, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/codeAction future in one future which return the list of code actions
                    return CompletableFutures.mergeInOneFuture(codeActionPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<CodeActionData>> getCodeActionsFor(@NotNull CodeActionParams params,
                                                                             @NotNull LanguageServerItem languageServer,
                                                                             @NotNull CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .codeAction(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_CODE_ACTION)
                .thenApplyAsync(codeActions -> {
                    if (codeActions == null) {
                        // textDocument/codeAction may return null
                        return Collections.emptyList();
                    }
                    return codeActions.stream()
                            .filter(LSPIntentionCodeActionSupport::isValidCodeAction)
                            .map(codeAction -> new CodeActionData(codeAction, languageServer.getServerWrapper()))
                            .toList();
                });
    }

    /**
     * Returns the LSP CodeAction for the given index and null otherwise.
     *
     * @param index the code action index.
     * @return the LSP CodeAction for the given index and null otherwise.
     */
    @Override
    public @Nullable Either<CodeActionData, Boolean> getCodeActionAt(int index) {
        if (super.isValidLSPFuture()) {
            List<CodeActionData> codeActions = getFeatureData(null).getNow(null);
            if (codeActions != null) {
                if (codeActions.size() > index) {
                    // The LSP code actions are loaded and it matches the given index
                    return Either.forLeft(codeActions.get(index));
                }
                return Either.forRight(Boolean.FALSE);
            }
        }
        return null;
    }

    /**
     * Returns true if the given code action is valid and false otherwise.
     *
     * @param codeAction the code action.
     * @return true if the given code action is valid and false otherwise.
     */
    private static boolean isValidCodeAction(@Nullable Either<org.eclipse.lsp4j.Command, org.eclipse.lsp4j.CodeAction> codeAction) {
        if (codeAction == null) {
            return false;
        }
        if (codeAction.isRight()) {
            // For IJ intention, 'quickfix' code action must be filtered.
            return !CodeActionKind.QuickFix.equals(codeAction.getRight().getKind());
        }
        return true;
    }
}