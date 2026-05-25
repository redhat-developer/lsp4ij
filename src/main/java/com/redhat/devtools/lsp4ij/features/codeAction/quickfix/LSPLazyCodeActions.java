/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.codeAction.CodeActionData;
import com.redhat.devtools.lsp4ij.features.codeAction.LSPLazyCodeActionIntentionAction;
import com.redhat.devtools.lsp4ij.features.codeAction.LSPLazyCodeActionProvider;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.internal.PsiFileChangedException;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * This class returns 20 IJ {@link LSPLazyCodeActionIntentionAction} which does nothing. It loads the LSP code actions
 * for the given diagnostic only when user triggers the quick fixes for the diagnostic.
 *
 * @author Angelo ZERR
 */
public class LSPLazyCodeActions implements LSPLazyCodeActionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPLazyCodeActions.class);

    private static final int NB_LAZY_CODE_ACTIONS = 20;

    // The diagnostic
    private final List<Diagnostic> diagnostics;

    // The virtual file
    private final PsiFile file;

    // The language server which has reported the diagnostic
    private final LanguageServerItem languageServer;

    // List of lazy code actions
    private final List<IntentionAction> codeActions;

    // LSP code actions request used to load code action for the diagnostic.
    private CompletableFuture<List<CodeActionData>> lspCodeActionRequest = null;

    public LSPLazyCodeActions(@NotNull List<Diagnostic> diagnostics,
                              @NotNull VirtualFile file,
                              @NotNull LanguageServerItem languageServer) {
        this.diagnostics = diagnostics;
        this.file = LSPIJUtils.getPsiFile(file, languageServer.getProject());
        this.languageServer = languageServer;
        // Create 20 lazy IJ quick fixes which does nothing (IntentAction#isAvailable returns false)
        codeActions = new ArrayList<>(NB_LAZY_CODE_ACTIONS);
        for (int i = 0; i < NB_LAZY_CODE_ACTIONS; i++) {
            codeActions.add(new LSPQuickFixIntentionAction(this, i));
        }
    }

    /**
     * Returns the LSP CodeAction for the given index and null otherwise.
     *
     * @param index the code action index.
     * @return the LSP CodeAction for the given index and null otherwise.
     */
    @Override
    public @Nullable Either<CodeActionData, Boolean> getCodeActionAt(int index) {
        List<CodeActionData> codeActions = getOrLoadCodeActions();
        if (codeActions != null) {
            if (codeActions.size() > index) {
                // The LSP code actions are loaded and it matches the given index
                return Either.forLeft(codeActions.get(index));
            }
            return Either.forRight(Boolean.FALSE);
        }
        return null;
    }

    private @Nullable List<CodeActionData> getOrLoadCodeActions() {
        if (lspCodeActionRequest == null) {
            // Create LSP textDocument/codeAction request
            lspCodeActionRequest = loadCodeActionsFor(diagnostics);
        }
        // Get the response of the LSP textDocument/codeAction request.
        List<CodeActionData> codeActions = null;
        // Wait for the textDocument/codeAction LSP request to complete.
        // waitUntilDone() will:
        // - Poll in 25ms increments without blocking continuously
        // - Check for cancellation via ProgressManager
        // - Detect if the PSI file was modified during the wait
        // - Display a progress indicator if it takes more than 5 seconds
        var future = lspCodeActionRequest;
        try {
            waitUntilDone(future, file);
        } catch (PsiFileChangedException e) {
            // The file was modified while waiting - cancel the now-obsolete LSP request
            cancel();
        } catch (ProcessCanceledException e) {
            // User or IDE canceled the operation - propagate the cancellation
            throw e;
        } catch (CancellationException e) {
        	// Occur when code action is loading and a cancel has occurred, ignore the error.
            return null;
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/codeAction' request", e);
            return null;
        }
        if (isDoneNormally(lspCodeActionRequest)) {
            codeActions = lspCodeActionRequest.getNow(null);
        }
        return codeActions;
    }

    /**
     * load code actions for the given diagnostic.
     *
     * @param diagnostics the LSP diagnostic.
     * @return list of Intellij {@link IntentionAction} which are used to create Intellij QuickFix.
     */
    private CompletableFuture<List<CodeActionData>> loadCodeActionsFor(@NotNull List<Diagnostic> diagnostics) {
        return CompletableFutures
                .computeAsyncCompose(cancelChecker -> languageServer
                        .getInitializedServer()
                        .thenCompose(ls -> {
                            // Language server is initialized here
                            cancelChecker.checkCanceled();

                            // Collect code action for the given file by using the language server
                            CodeActionParams params = createCodeActionParams(diagnostics, file, languageServer.getClientFeatures());
                            return ls.getTextDocumentService()
                                    .codeAction(params)
                                    .thenApply(codeActions -> {
                                        // Code action are collected here
                                        cancelChecker.checkCanceled();
                                        if (codeActions == null || codeActions.isEmpty()) {
                                            return Collections.emptyList();
                                        }
                                        return codeActions
                                                .stream()
                                                .filter(ca -> {
                                                    if (ca.isRight()) {
                                                        CodeAction codeAction = ca.getRight();
                                                        return codeAction.getKind() == null ||
                                                                codeAction.getKind().isEmpty() ||
                                                                CodeActionKind.QuickFix.equals(codeAction.getKind());
                                                    }
                                                    return true;
                                                })
                                                .map(ca -> new CodeActionData(ca, languageServer))
                                                .toList();
                                    });
                        }));
    }

    /**
     * Create the LSP code action parameters for the given diagnostic and file.
     *
     * @param diagnostics the diagnostic.
     * @param file        the file.
     * @return the LSP code action parameters for the given diagnostic and file.
     */
    private static CodeActionParams createCodeActionParams(@NotNull List<Diagnostic> diagnostics,
                                                           @NotNull PsiFile file,
                                                           @NotNull FileUriSupport fileUriSupport) {
        CodeActionParams params = new CodeActionParams();
        var identifier = new TextDocumentIdentifier(FileUriSupport.toString(file.getVirtualFile(), fileUriSupport));
        params.setTextDocument(identifier);
        // As diagnostic list is never empty, and it is sorted by the max range, the code action range parameter is the first diagnostic
        Range range = diagnostics.get(0).getRange();
        params.setRange(range);

        CodeActionContext context = new CodeActionContext(diagnostics);
        // Collect only 'quickfix' code actions, the other code actions will appear in the Refactoring menu
        context.setOnly(Collections.singletonList(CodeActionKind.QuickFix));
        context.setTriggerKind(CodeActionTriggerKind.Automatic);
        params.setContext(context);
        return params;
    }

    /**
     * Returns the list of lazy code actions.
     *
     * @return the list of lazy code actions.
     */
    public List<IntentionAction> getCodeActions() {
        return codeActions;
    }

    /**
     * Cancel if needed the LSP request textDocument/codeAction
     */
    public void cancel() {
        CancellationSupport.cancel(lspCodeActionRequest);
    }
}
