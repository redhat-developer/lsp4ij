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
package com.redhat.devtools.intellij.lsp4ij.operations.documentLink;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LSPVirtualFileData;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.intellij.lsp4ij.LanguageServiceAccessor;
import org.eclipse.lsp4j.DocumentLink;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

/**
 * {@link GotoDeclarationHandler} implementation used to open LSP document link with CTrl+Click.
 */
public class LSPDocumentLinkGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        Document document = editor.getDocument();
        VirtualFile file = LSPIJUtils.getFile(document);
        Module module = LSPIJUtils.getModule(file, sourceElement.getProject());
        Project project = module != null ? module.getProject() : null;
        if (project == null || project.isDisposed()) {
            return PsiElement.EMPTY_ARRAY;
        }

        URI fileUri = LSPIJUtils.toUri(file);
        Collection<LSPDocumentLinkForServer> allLinks = getAllDocumentLink(fileUri, project);
        if (allLinks.isEmpty()) {
            return PsiElement.EMPTY_ARRAY;
        }
        // The file has some LSP document links
        for (LSPDocumentLinkForServer links : allLinks) {
            for (DocumentLink documentLink : links.getDocumentLinks()) {
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
                        return new PsiElement[]{PsiManager.getInstance(project).findFile(targetFile)};
                    }
                }
            }
        }
        return PsiElement.EMPTY_ARRAY;
    }

    private Collection<LSPDocumentLinkForServer> getAllDocumentLink(URI fileUri, Project project) {
        Collection<LSPDocumentLinkForServer> links = new ArrayList<>();
        LanguageServiceAccessor.getInstance(project)
                .getStartedServers()
                .forEach(ls -> {
                    LSPVirtualFileData data = ls.getLSPVirtualFileData(fileUri);
                    if (data != null) {
                        links.add(data.getDocumentLinkForServer());
                    }
                });
        return links;
    }
}
