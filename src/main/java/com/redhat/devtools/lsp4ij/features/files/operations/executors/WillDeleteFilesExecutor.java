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
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.FileDelete;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The 'workspace/willDeleteFiles' executor to collect {@link WorkspaceEdit} .
 * and apply the changes to the new file which has been renamed.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willDeleteFiles">workspace/willDeleteFiles</a>
 */
public class WillDeleteFilesExecutor extends WillOperationFilesExecutor {

    public WillDeleteFilesExecutor(FileOperationsManager fileOperationsManager) {
        super(fileOperationsManager, LSPRequestConstants.WORKSPACE_WILL_DELETE_FILES);
    }

    /**
     * Execute 'workspace/willDeleteFiles' with the given uri to collect the {@link WorkspaceEdit} to apply it to the new deleted file.
     *
     * @param deletedFileUri     the deleted file uri.
     * @param unusedFileUri     unused file uri.
     * @param languageServer the language server.
     * @return the {@link WorkspaceEdit} future to apply it to the new renamed file.
     */
    @Override
    protected CompletableFuture<WorkspaceEdit> executeWillOperationFiles(@NotNull URI deletedFileUri,
                                                                         @Nullable URI unusedFileUri,
                                                                         @NotNull LanguageServer languageServer) {
        var params = new DeleteFilesParams(List.of(new FileDelete(deletedFileUri.toASCIIString())));
        return languageServer.getWorkspaceService()
                .willDeleteFiles(params);
    }

    @Override
    protected void sendDidOperationFilesNotificationIfNeeded(@NotNull URI deletedFileUri,
                                                             @Nullable URI unusedFileUri,
                                                             boolean isFolder) {
        if (fileOperationsManager.canDidDeleteFiles(deletedFileUri, isFolder)) {
            // Send 'workspace/didDeleteFiles' if the {@link WorkspaceEdit} collected with 'workspace/willDeleteFiles' has been applied correctly.
            sendDidDeleteFiles(new FileDelete(deletedFileUri.toASCIIString()));
        }
    }

    private void sendDidDeleteFiles(FileDelete... fileDeletes) {
        var languageServerWrapper = fileOperationsManager.getLanguageServerWrapper();
        languageServerWrapper.sendNotification(ls -> {
            var params = new DeleteFilesParams(List.of(fileDeletes));
            ls.getWorkspaceService()
                    .didDeleteFiles(params);
        });
    }

}
