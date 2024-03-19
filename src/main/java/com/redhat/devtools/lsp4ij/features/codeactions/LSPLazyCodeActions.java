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
package com.redhat.devtools.lsp4ij.features.codeactions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * This class returns 10 IJ {@link LSPLazyCodeActionIntentionAction} which does nothing. It loads the LSP code actions
 * for the given diagnostic only when user triggers the quick fixes for the diagnostic.
 *
 * @author Angelo ZERR
 */
public class LSPLazyCodeActions {

    public static final Either<Command, CodeAction> NO_CODE_ACTION_AT_INDEX = Either.forLeft(new Command());

    private static final int NB_LAZY_CODE_ACTIONS = 10;

    // The diagnostic
    private final List<Diagnostic> diagnostics;

    // The virtual file
    private final VirtualFile file;

    // The language server which has reported the diagnostic
    private final LanguageServerWrapper languageServerWrapper;

    // List of lazy code actions
    private final List<LSPLazyCodeActionIntentionAction> codeActions;

    // LSP code actions request used to load code action for the diagnostic.
    private CompletableFuture<List<Either<Command, CodeAction>>> lspCodeActionRequest = null;

    public LSPLazyCodeActions(List<Diagnostic> diagnostics, VirtualFile file, LanguageServerWrapper languageServerWrapper) {
        this.diagnostics = diagnostics;
        this.file = file;
        this.languageServerWrapper = languageServerWrapper;
        // Create 10 lazy IJ quick fixes which does nothing (IntentAction#isAvailable returns false)
        codeActions = new ArrayList<>(NB_LAZY_CODE_ACTIONS);
        for (int i = 0; i < NB_LAZY_CODE_ACTIONS; i++) {
            codeActions.add(new LSPLazyCodeActionIntentionAction(this, i));
        }
    }

    /**
     * Returns the LSP CodeAction for the given index and null otherwise.
     *
     * @param index the code action index.
     * @return the LSP CodeAction for the given index and null otherwise.
     */
    public @Nullable Either<Command, CodeAction> getCodeActionAt(int index) {
        List<Either<Command, CodeAction>> codeActions = getOrLoadCodeActions();
        if (codeActions != null) {
            if (codeActions.size() > index) {
                // The LSP code actions are loaded and it matches the given index
                return codeActions.get(index);
            }
            return NO_CODE_ACTION_AT_INDEX;
        }
        return null;
    }

    @Nullable
    private List<Either<Command, CodeAction>> getOrLoadCodeActions() {
        if (lspCodeActionRequest == null) {
            // Create LSP textDocument/codeAction request
            lspCodeActionRequest = loadCodeActionsFor(diagnostics);
        }
        // Get the response of the LSP textDocument/codeAction request.
        List<Either<Command, CodeAction>> codeActions = null;
        try {
            waitUntilDone(lspCodeActionRequest);
            if (isDoneNormally(lspCodeActionRequest)) {
                codeActions = lspCodeActionRequest.getNow(null);
            }
        } catch (ProcessCanceledException | ExecutionException e) {
            // ProcessCanceledException occurs when user move the mouse, in this case the hover popup is closed
            // but the lspCodeActionRequest is not cancelled here if user hover again the error.
        }
        return codeActions;
    }

    /**
     * load code actions for the given diagnostic.
     *
     * @param diagnostics the LSP diagnostic.
     * @return list of Intellij {@link IntentionAction} which are used to create Intellij QuickFix.
     */
    private CompletableFuture<List<Either<Command, CodeAction>>> loadCodeActionsFor(List<Diagnostic> diagnostics) {
        return CompletableFutures
                .computeAsyncCompose(cancelChecker -> languageServerWrapper
                        .getInitializedServer()
                        .thenCompose(ls -> {
                            // Language server is initialized here
                            cancelChecker.checkCanceled();

                            // Collect code action for the given file by using the language server
                            CodeActionParams params = createCodeActionParams(diagnostics, file);
                            return ls.getTextDocumentService()
                                    .codeAction(params)
                                    .thenApply(codeActions -> {
                                        // Code action are collected here
                                        cancelChecker.checkCanceled();
                                        return Objects.requireNonNullElse(codeActions, Collections.emptyList());
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
    private static CodeActionParams createCodeActionParams(List<Diagnostic> diagnostics, VirtualFile file) {
        CodeActionParams params = new CodeActionParams();
        params.setTextDocument(LSPIJUtils.toTextDocumentIdentifier(file));
        // As diagnostic list is never empty, and it is sorted by the max range, the code action range parameter is the first diagnostic
        Range range = diagnostics.get(0).getRange();
        params.setRange(range);

        CodeActionContext context = new CodeActionContext(diagnostics);
        // TODO: Collect only 'quickfix' code actions when other code actions could appear in some refactoring menu
        // context.setOnly(Collections.singletonList(CodeActionKind.QuickFix));
        context.setTriggerKind(CodeActionTriggerKind.Automatic);
        params.setContext(context);
        return params;
    }

    /**
     * Returns the language server which has reported the diagnostic.
     *
     * @return the language server which has reported the diagnostic.
     */
    public LanguageServerWrapper getLanguageServerWrapper() {
        return languageServerWrapper;
    }

    /**
     * Returns the list of lazy code actions.
     *
     * @return the list of lazy code actions.
     */
    public List<LSPLazyCodeActionIntentionAction> getCodeActions() {
        return codeActions;
    }

    /**
     * Cancel if needed the LSP request textDocument/codeAction
     */
    public void cancel() {
        if (lspCodeActionRequest != null && !lspCodeActionRequest.isDone()) {
            lspCodeActionRequest.cancel(true);
        }
    }
}
