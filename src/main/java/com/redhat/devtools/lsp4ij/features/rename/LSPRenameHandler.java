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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.internal.CancellationUtil;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDoneAsync;

/**
 * LSP rename handler which:
 *
 * <ul>
 *     <li>consumes LSP 'textDocument/prepareRename' requests. </li>
 *     <li>consumes LSP 'textDocument/rename' requests. </li>
 * </ul>
 */
public class LSPRenameHandler implements RenameHandler, TitledHandler {

    private boolean searchingRenameHandlers;

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile, DataContext dataContext) {
        int offset = editor.getCaretModel().getCurrentCaret().getOffset();
        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return;
        }
        Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return;
        }
        final TextDocumentIdentifier textDocument = LSPIJUtils.toTextDocumentIdentifier(file);
        final Position position = LSPIJUtils.toPosition(offset, document);

        // Step 1: consume the LSP 'textDocument/prepareRename' request

        // Get the text range and placeholder of the LSP rename with 'textDocument/prepareRename'.
        // If the language server doesn't support prepare rename capability,
        // the support returns a prepare rename response by using the token strategy.
        LSPPrepareRenameParams prepareRenameParams = new LSPPrepareRenameParams(textDocument, position, offset, document, psiFile);
        var prepareRenameSupport = LSPFileSupport.getSupport(psiFile).getPrepareRenameSupport();
        // Cancel the previous prepare rename
        prepareRenameSupport.cancel();
        CompletableFuture<List<PrepareRenameResultData>> future =
                prepareRenameSupport.getPrepareRenameResult(prepareRenameParams);

        // As invoke method is invoked in the EDT Thread,
        // com.redhat.devtools.lsp4ij.internal.CompletableFutures#waitUntilDone
        // cannot be used to avoid freezing IJ, in this case the response Future
        // is collected when the future is ready.

        // Wait until the future is finished and stop waiting if there are some ProcessCanceledExceptions.
        // The 'prepare rename' is stopped:
        // - if user changes the editor content
        // - if they cancel the Task
        waitUntilDoneAsync(future, LanguageServerBundle.message("lsp.refactor.rename.prepare.progress.title", psiFile.getVirtualFile().getName(), offset), psiFile);

        future.handle((prepareRenamesResult, error) -> {
            if (error != null) {
                // Handle error
                if (CancellationUtil.isRequestCancelledException(error)) {
                    // Don't show cancelled error
                    return error;
                }
                if (error instanceof CompletionException || error instanceof ExecutionException) {
                    error = error.getCause();
                }
                // The language server throws an error while preparing rename, display it as hint in the editor
                showErrorHint(editor, error.getMessage());
                return error;
            }
            if (prepareRenamesResult.isEmpty()) {
                // Show "The element can't be renamed." hint error in the editor
                showErrorHint(editor, LanguageServerBundle.message("lsp.refactor.rename.cannot.be.renamed.error"));
                return prepareRenamesResult;
            }

            // Here we have collected the prepare rename results for each language server supporting the rename capability.

            // Step 2: open the LSP rename dialog to consume the LSP 'textDocument/rename' request when OK is pressed.
            var renameRunnable = showDialogAndRename(editor, psiFile, prepareRenamesResult, textDocument, position);
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                renameRunnable.run();
            } else {
                ApplicationManager
                        .getApplication()
                        .invokeLater(renameRunnable);
            }
            return prepareRenamesResult;
        });

    }

    @NotNull
    private static Runnable showDialogAndRename(@NotNull Editor editor,
                                                @NotNull PsiFile psiFile,
                                                @NotNull List<PrepareRenameResultData> prepareRenamesResult,
                                                @NotNull TextDocumentIdentifier textDocument,
                                                @NotNull Position position) {
        return () -> {
            // Create rename parameters
            LSPRenameParams renameParams = createRenameParams(prepareRenamesResult, textDocument, position);
            // Open the LSP rename dialog
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                LSPRenameUnitTestMode.get().showRenameRefactoringDialog(renameParams);
                LSPRenameRefactoringDialog.doRename(renameParams, psiFile, editor);
            } else {
                LSPRenameRefactoringDialog dialog = new LSPRenameRefactoringDialog(renameParams, psiFile, editor);
                dialog.show();
            }
        };
    }

    @NotNull
    private static LSPRenameParams createRenameParams(List<PrepareRenameResultData> prepareRenamesResult, TextDocumentIdentifier textDocument, Position position) {
        String placeholder = prepareRenamesResult.get(0).placeholder();
        List<LanguageServerItem> languageServers = prepareRenamesResult
                .stream()
                .map(PrepareRenameResultData::languageServer)
                .toList();
        LSPRenameParams renameParams = new LSPRenameParams(textDocument, position, languageServers);
        renameParams.setNewName(placeholder);
        return renameParams;
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {

    }

    @Override
    public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
        if (searchingRenameHandlers) {
            return false;
        }
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project == null || project.isDisposed()) {
            return false;
        }
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (editor == null) {
            return false;
        }
        PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
        if (file == null) {
            return false;
        }

        // Checks if a language server is associated to the file, with the 'rename' capability.
        // At this step we consider that language servers are started, we just wait for 200ms
        // to avoid freezing the UI
        if (LanguageServiceAccessor.getInstance(project)
                .hasAny(file, ls -> ls.getClientFeatures().getRenameFeature().isRenameSupported(file))) {
            // There is at least one language server providing rename support for the file.
            try {
                searchingRenameHandlers = true;
                var renameHandlers = RenameHandlerRegistry.getInstance().getRenameHandlers(dataContext);
                if (renameHandlers.isEmpty()) {
                    return true;
                }
                // When there are several rename handlers, IJ removes all VariableInplaceRenameHandler, MemberInplaceRenameHandlers (ex: inline Java field rename)
                // See https://github.com/JetBrains/intellij-community/blob/cc10f72bc90a650b8d9d9f0427ae5a56111940dd/platform/lang-impl/src/com/intellij/refactoring/rename/RenameHandlerRegistry.java#L106
                // To avoid showing the LSP Rename dialog when a Java field is renamed (which will do nothing)
                // We want to show the IJ inline variable rename handler instead of LSP rename dialog.
                // So we check none of them is an instance of MemberInplaceRenameHandler,
                // in which case, the LSP rename dialog will not be available
                return !renameHandlers
                        .stream()
                        .allMatch(renameHandler -> renameHandler instanceof VariableInplaceRenameHandler);
            } finally {
                searchingRenameHandlers = false;
            }
        }
        return false;
    }

    @Override
    public String getActionTitle() {
        return LanguageServerBundle.message("lsp.refactor.rename.symbol.handler.title");
    }

    static void showErrorHint(@NotNull Editor editor, @NotNull String text) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            LSPRenameUnitTestMode.get().showErrorHint(text);
            return;
        }
        if (ApplicationManager.getApplication().isDispatchThread()) {
            HintManager.getInstance().showErrorHint(editor, text);
        } else {
            ApplicationManager.getApplication()
                    .invokeLater(() -> HintManager.getInstance().showErrorHint(editor, text));
        }
    }


}