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
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.internal.CancellationUtil;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.*;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDoneAsync;

/**
 * LSP rename handler which:
 *
 * <ul>
 *     <li>consumes LSP 'textDocument/prepareRename' request. </li>
 *     <li>consumes LSP 'textDocument/rename' request. </li>
 * </ul>
 */
public class LSPRenameHandler implements RenameHandler, TitledHandler {

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
        // the support returns a prepare rename result by using token strategy.
        LSPPrepareRenameParams prepareRenameParams = new LSPPrepareRenameParams(textDocument, position, offset, document);
        var prepareRenameSupport = LSPFileSupport.getSupport(psiFile)
                .getPrepareRenameSupport();
        // Cancel the previous prepare rename
        prepareRenameSupport.cancel();
        CompletableFuture<List<PrepareRenameResultData>> future =
                prepareRenameSupport.getPrepareRenameResult(prepareRenameParams);

        // As invoke method is invoked in EDT Thread,
        // com.redhat.devtools.lsp4ij.internal.CompletableFutures#waitUntilDone
        // cannot be used to avoid freezing IJ, in this case the result future
        // is collected when future is ready.

        // Wait until the future is finished and stop the wait if there are some ProcessCanceledException.
        // The 'prepare rename' is stopped:
        // - if user change the editor content
        // - if it cancels the Task
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
                showErrorHint(editor, LanguageServerBundle.message("lsp.refactor.rename.prepare.error", error.getMessage()));
                return error;
            }
            if (prepareRenamesResult.isEmpty()) {
                // Show "The element can't be renamed." hint error in the editor
                showErrorHint(editor, LanguageServerBundle.message("lsp.refactor.rename.cannot.be.renamed.error"));
                return prepareRenamesResult;
            }

            // Here we have collected premare rename result for each language server which support the rename capability.
            // Step 2: open the LSP rename dialog to consume the LSP 'textDocument/rename' request when OK is done.
            ApplicationManager.getApplication()
                    .invokeLater(() -> {

                        // Create rename parameters
                        LSPRenameParams renameParams = createRenameParams(prepareRenamesResult, textDocument, position);

                        // Open the LSP rename dialog
                        LSPRenameRefactoringDialog dialog = new LSPRenameRefactoringDialog(renameParams, psiFile, editor);
                        dialog.show();
                    });

            return prepareRenamesResult;
        });

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

        try {
            // Checks if it exists a language server which is associated to the file and which have the 'rename' capability.
            // At this step we consider that language servers are started, we wait just for 200ms
            // to avoid freezing the UI
            return !LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file.getVirtualFile(), LanguageServerItem::isRenameSupported)
                    .get(200, TimeUnit.MILLISECONDS)
                    .isEmpty();
        } catch (TimeoutException e) {
            // Show "Rename... is not available during language servers starting."
            showErrorHint(editor, LanguageServerBundle.message("lsp.refactor.rename.language.server.not.ready"));
        } catch (Exception e) {

        }
        return false;
    }

    @Override
    public String getActionTitle() {
        return LanguageServerBundle.message("lsp.refactor.rename.symbol.handler.title");
    }

    static void showErrorHint(@NotNull Editor editor, @NotNull String text) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            HintManager.getInstance().showErrorHint(editor, text);
        } else {
            ApplicationManager.getApplication()
                    .invokeLater(() -> HintManager.getInstance().showErrorHint(editor, text));
        }
    }


}