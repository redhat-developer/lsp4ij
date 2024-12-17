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
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.features.refactoring.WorkspaceEditData;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP rename support which loads and caches {@link org.eclipse.lsp4j.WorkspaceEdit} rename by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/rename' requests</li>
 * </ul>
 */
public class LSPRenameSupport extends AbstractLSPDocumentFeatureSupport<LSPRenameParams, List<WorkspaceEditData>> {

    private LSPRenameParams previousParams;

    public LSPRenameSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<WorkspaceEditData>> getRename(@NotNull LSPRenameParams params) {
        if (previousParams != null && !previousParams.equals(params)) {
            super.cancel();
        }
        previousParams = params;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<WorkspaceEditData>> doLoad(@NotNull LSPRenameParams params,
                                                                @NotNull CancellationSupport cancellationSupport) {
        return getRename(params, getFile(), cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<WorkspaceEditData>> getRename(@NotNull LSPRenameParams params,
                                                                                 @NotNull PsiFile file,
                                                                                 @NotNull CancellationSupport cancellationSupport) {
        // Collect list of textDocument/rename future for each language servers
        List<CompletableFuture<List<WorkspaceEditData>>> renamePerServerFutures = params.getLanguageServers()
                .stream()
                .map(languageServer -> getRenameFor(params, file, languageServer, cancellationSupport))
                .toList();

        // Merge list of textDocument/rename future in one future which return the list of workspace edit
        return CompletableFutures.mergeInOneFuture(renamePerServerFutures, cancellationSupport);
    }

    private static CompletableFuture<List<WorkspaceEditData>> getRenameFor(@NotNull RenameParams params,
                                                                           @NotNull PsiFile file,
                                                                           @NotNull LanguageServerItem languageServer,
                                                                           @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                                .getTextDocumentService()
                                .rename(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_RENAME,
                        false /* if rename throws an error, the error must not be displayed as notification but as hint in the editor  */)
                .thenApplyAsync(workspaceEdit -> {
                    if (isValidWorkspaceEdit(workspaceEdit)) {
                        return List.of(new WorkspaceEditData(workspaceEdit, languageServer));
                    }
                    return Collections.emptyList();
                });
    }

    /**
     * Returns true if the given workspace edit is valid and false otherwise.
     *
     * @param workspaceEdit the workspace edit.
     * @return true if the given workspace edit is valid and false otherwise.
     */
    private static boolean isValidWorkspaceEdit(@Nullable WorkspaceEdit workspaceEdit) {
        if (workspaceEdit == null) {
            return false;
        }
        if (workspaceEdit.getChanges() != null && !workspaceEdit.getChanges().isEmpty()) {
            return true;
        }
        if (workspaceEdit.getDocumentChanges() != null && !workspaceEdit.getDocumentChanges().isEmpty()) {
            return true;
        }
        if (workspaceEdit.getChangeAnnotations() != null && !workspaceEdit.getChangeAnnotations().isEmpty()) {
            return true;
        }
        return false;
    }


}
