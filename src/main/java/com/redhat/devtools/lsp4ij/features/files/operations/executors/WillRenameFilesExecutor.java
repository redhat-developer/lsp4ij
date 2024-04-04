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
package com.redhat.devtools.lsp4ij.features.files.operations.executors;

import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.features.files.operations.FileOperationsManager;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The 'workspace/willRenameFiles' executor to collect {@link WorkspaceEdit} .
 * and apply the changes to the new file which has been renamed.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willRenameFiles">workspace/willRenameFiles</a>
 */
public class WillRenameFilesExecutor extends WillOperationFilesExecutor {

    public WillRenameFilesExecutor(@NotNull FileOperationsManager fileOperationsManager) {
        super(fileOperationsManager, LSPRequestConstants.WORKSPACE_WILL_RENAME_FILES);
    }

    /**
     * Execute 'workspace/willRenameFiles' with the given old/new uri to collect the {@link WorkspaceEdit} to apply it to the new renamed file.
     *
     * @param oldFileUri     the old file uri which has been renamed.
     * @param newFileUri     the new file uri name.
     * @param languageServer the language server.
     * @return the {@link WorkspaceEdit} future to apply it to the new renamed file.
     */
    @Override
    protected CompletableFuture<WorkspaceEdit> executeWillOperationFiles(@NotNull URI oldFileUri,
                                                                         @Nullable URI newFileUri,
                                                                         @NotNull LanguageServer languageServer) {
        var params = new RenameFilesParams(List.of(new FileRename(oldFileUri.toASCIIString(), newFileUri.toASCIIString())));
        return languageServer.getWorkspaceService()
                .willRenameFiles(params);
    }

    @Override
    protected void sendDidOperationFilesNotificationIfNeeded(@NotNull URI oldFileUri,
                                                             @Nullable URI newFileUri,
                                                             boolean isFolder) {
        if (fileOperationsManager.canDidRenameFiles(oldFileUri, isFolder)) {
            // Send 'workspace/didRenameFiles' if the {@link WorkspaceEdit} collected with 'workspace/willRenameFiles' has been applied correctly.
            sendDidRenameFiles(new FileRename(oldFileUri.toASCIIString(), newFileUri.toASCIIString()));
        }
    }

    private void sendDidRenameFiles(FileRename... fileRenames) {
        var languageServerWrapper = fileOperationsManager.getLanguageServerWrapper();
        languageServerWrapper.sendNotification(ls -> {
            var params = new RenameFilesParams(List.of(fileRenames));
            ls.getWorkspaceService()
                    .didRenameFiles(params);
        });
    }

}
