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
package com.redhat.devtools.lsp4ij.features.documentLink;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * {@link GotoDeclarationHandler} implementation used to open LSP document link with CTrl+Click.
 */
public class LSPDocumentLinkGotoDeclarationHandler implements GotoDeclarationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDocumentLinkGotoDeclarationHandler.class);

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        PsiFile psiFile = sourceElement.getContainingFile();
        if (!LanguageServersRegistry.getInstance().isFileSupported(psiFile)) {
            return PsiElement.EMPTY_ARRAY;
        }
        Document document = editor.getDocument();
        VirtualFile file = LSPIJUtils.getFile(document);
        Module module = LSPIJUtils.getModule(file, sourceElement.getProject());
        Project project = module != null ? module.getProject() : null;
        if (project == null || project.isDisposed()) {
            return PsiElement.EMPTY_ARRAY;
        }

        LSPDocumentLinkSupport documentLinkSupport = LSPFileSupport.getSupport(psiFile).getDocumentLinkSupport();
        var params = new DocumentLinkParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()));
        CompletableFuture<List<DocumentLinkData>> documentLinkFuture = documentLinkSupport.getDocumentLinks(params);
        try {
            waitUntilDone(documentLinkFuture, psiFile);
        } catch (ProcessCanceledException | CancellationException e) {
            // cancel the LSP requests textDocument/documentLink
            documentLinkSupport.cancel();
            return null;
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/documentLink' request", e);
            return null;
        }

        if (isDoneNormally(documentLinkFuture)) {
            List<DocumentLinkData> documentLinks = documentLinkFuture.getNow(null);
            if (documentLinks != null) {
                for (DocumentLinkData documentLinkData : documentLinks) {
                    DocumentLink documentLink = documentLinkData.documentLink();
                    TextRange range = LSPIJUtils.toTextRange(documentLink.getRange(), document);
                    if (range.contains(offset)) {
                        // The Ctrl+Click has been done in a LSP document link,try to open the document.
                        final String target = documentLink.getTarget();
                        if (target != null && !target.isEmpty()) {
                            VirtualFile targetFile = LSPIJUtils.findResourceFor(target);
                            if (targetFile == null) {
                                // The LSP document link file doesn't exist, open a file dialog
                                // which asks if user want to create the file.
                                // At this step we cannot open a dialog directly, we need to open the dialog
                                // with invoke later.
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    int result = Messages.showYesNoDialog(LanguageServerBundle.message("lsp.create.file.confirm.dialog.message", target),
                                            LanguageServerBundle.message("lsp.create.file.confirm.dialog.title"), Messages.getQuestionIcon());
                                    if (result == Messages.YES) {
                                        try {
                                            // Create file
                                            VirtualFile newFile = LSPIJUtils.createFile(target);
                                            if (newFile != null) {
                                                // Open it in an editor
                                                LSPIJUtils.openInEditor(newFile, null, project);
                                            }
                                        } catch (IOException e) {
                                            Messages.showErrorDialog(LanguageServerBundle.message("lsp.create.file.error.dialog.message", target, e.getMessage()),
                                                    LanguageServerBundle.message("lsp.create.file.error.dialog.title"));
                                        }
                                    }
                                });
                                // Return an empty result here.
                                // If user accepts to create the file, the open is done after the creation of teh file.
                                return PsiElement.EMPTY_ARRAY;
                            }
                            return new PsiElement[]{ LSPIJUtils.getPsiFile(targetFile, project)};
                        }
                    }
                }
            }
        }
        return PsiElement.EMPTY_ARRAY;
    }

}
