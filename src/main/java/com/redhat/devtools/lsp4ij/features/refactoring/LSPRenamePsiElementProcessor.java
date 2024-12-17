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
package com.redhat.devtools.lsp4ij.features.refactoring;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP {@link RenamePsiElementProcessor} implementation to consume
 * LSP 'workspace/willRenameFiles' request when a file is renamed.
 */
public class LSPRenamePsiElementProcessor extends RenamePsiElementProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPRenamePsiElementProcessor.class);

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        return (element instanceof LSPPsiElement) ||
                ((element instanceof PsiFile) && LanguageServersRegistry.getInstance().isFileSupported((PsiFile) element));
    }

    @Override
    public void prepareRenaming(@NotNull PsiElement element,
                                @NotNull String newName,
                                @NotNull Map<PsiElement, String> allRenames,
                                @NotNull SearchScope scope) {
        PsiFile file = element.getContainingFile();
        var project = file.getProject();

        // Create LSP rename files parameters.
        var params = createtRenameFilesParams(file, newName);

        // Prepare future of LSP 'workspace/willRenameFiles' request
        CancellationSupport cancellationSupport = new CancellationSupport();
        var willRenameFilesFuture =
                LanguageServiceAccessor.getInstance(project)
                        .getLanguageServers(file.getVirtualFile(),
                                null,
                                f -> f.getRenameFeature().isWillRenameFilesSupported(file))
                        .thenComposeAsync(languageServerItems -> {

                            if (languageServerItems.isEmpty()) {
                                return CompletableFuture.completedFuture(null);
                            }

                            List<CompletableFuture<WorkspaceEditData>> r = languageServerItems
                                    .stream()
                                    .filter(ls -> ls.isWillRenameFilesSupported(file))
                                    .map(ls -> cancellationSupport
                                            .execute(ls.getWorkspaceService().willRenameFiles(params),
                                                    ls,
                                                    LSPRequestConstants.WORKSPACE_WILL_RENAME_FILES)
                                            .thenApplyAsync(we -> new WorkspaceEditData(we, ls)))
                                    .toList();

                            // TODO: we return the WorkspaceEdit of the first language server, what about when there are several
                            // language servers which returns WorkspaceEdit?
                            return r.isEmpty() ? CompletableFuture.completedFuture(null) : r.get(0);
                        });

        try {
            // Wait until the future of LSP 'workspace/willRenameFiles' request
            // is finished and stop the wait if there are some ProcessCanceledException.
            waitUntilDone(willRenameFilesFuture, file);
        } catch (
                ProcessCanceledException ignore) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
        } catch (CancellationException ignore) {
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'workspace/willRenameFiles' request", e);
        }

        if (isDoneNormally(willRenameFilesFuture)) {
            // Future has been done correctly, create a fake PsiElement which stores the WorkspaceEdit
            WorkspaceEditData editData = willRenameFilesFuture.getNow(null);
            if (editData != null && editData.edit() != null) {

                LSPRenameFilesContextHolder.set(new LSPRenameFilesContext(params, Collections.singletonList(editData.languageServer()), file));

                var edit = editData.edit();
                var fileUriSupport = editData.languageServer().getClientFeatures();
                var documentChanges = edit.getDocumentChanges();
                if (documentChanges != null) {
                    for (var documentChange : documentChanges) {
                        if (documentChange.isLeft()) {
                            var ed = documentChange.getLeft();
                            String uri = ed.getTextDocument().getUri();
                            List<TextEdit> textEdits = ed.getEdits();
                            addPsiElementRename(uri, textEdits, allRenames, fileUriSupport, project);
                        }
                    }
                }

                var changes = edit.getChanges();
                if (changes != null) {
                    changes.entrySet().forEach(entry -> {
                        String uri = entry.getKey();
                        List<TextEdit> textEdits = entry.getValue();
                        addPsiElementRename(uri, textEdits, allRenames, fileUriSupport, project);
                    });
                }
            }
        }
    }

    private static void addPsiElementRename(@NotNull String uri,
                                            @NotNull List<TextEdit> textEdits,
                                            @NotNull Map<PsiElement, String> allRenames,
                                            @Nullable FileUriSupport fileUriSupport,
                                            @NotNull Project project) {
        VirtualFile file = FileUriSupport.findFileByUri(uri, fileUriSupport);
        if (file != null) {
            Document document = LSPIJUtils.getDocument(file);
            PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
            textEdits
                    .forEach(textEdit -> {
                        TextRange textRange = LSPIJUtils.toTextRange(textEdit.getRange(), document);
                        var elt = new LSPRenamePsiElement(psiFile, textRange, textEdit);
                        allRenames.put(elt, textEdit.getNewText());
                    });
        }
    }

    @Override
    public void renameElement(@NotNull PsiElement element, @NotNull String newName, UsageInfo @NotNull [] usages, @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
        if (element instanceof LSPRenamePsiElement) {
            ((LSPRenamePsiElement) element).setName(newName);
            ((LSPRenamePsiElement) element).rename();
            if (listener != null) {
                listener.elementRenamed(element);
            }
        }
    }

    @NotNull
    private static RenameFilesParams createtRenameFilesParams(@NotNull PsiFile file,
                                                              @NotNull String newName) {
        String oldFileUri = LSPIJUtils.toUri(file).toASCIIString();
        int index = oldFileUri.lastIndexOf('/');
        String newFileUri = oldFileUri.substring(0, index) + "/" + newName;
        return new RenameFilesParams(List.of(new FileRename(oldFileUri, newFileUri)));
    }


}