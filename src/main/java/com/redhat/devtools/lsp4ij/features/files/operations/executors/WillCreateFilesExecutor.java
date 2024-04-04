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
import org.eclipse.lsp4j.FileCreate;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The 'workspace/willCreateFiles' executor to collect {@link WorkspaceEdit} .
 * and apply the changes to the new file which has been renamed.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willCreateFiles">workspace/willCreateFiles</a>
 */
public class WillCreateFilesExecutor extends WillOperationFilesExecutor {

    public WillCreateFilesExecutor(@NotNull FileOperationsManager fileOperationsManager) {
        super(fileOperationsManager, LSPRequestConstants.WORKSPACE_WILL_CREATE_FILES);
    }

    /**
     * Execute 'workspace/willCreateFiles' with the given uri to collect the {@link WorkspaceEdit} to apply it to the new created file.
     *
     * @param createdFileUri     the created file uri.
     * @param unusedFileUri     unused file uri.
     * @param languageServer the language server.
     * @return the {@link WorkspaceEdit} future to apply it to the new renamed file.
     */
    @Override
    protected CompletableFuture<WorkspaceEdit> executeWillOperationFiles(@NotNull URI createdFileUri,
                                                                         @Nullable URI unusedFileUri,
                                                                         @NotNull LanguageServer languageServer) {
        var params = new CreateFilesParams(List.of(new FileCreate(createdFileUri.toASCIIString())));
        return languageServer.getWorkspaceService()
                .willCreateFiles(params);
    }

    @Override
    protected void sendDidOperationFilesNotificationIfNeeded(@NotNull URI createdFileUri,
                                                             @Nullable URI unusedFileUri,
                                                             boolean isFolder) {
        if (fileOperationsManager.canDidCreateFiles(createdFileUri, isFolder)) {
            // Send 'workspace/didCreateFiles' if the {@link WorkspaceEdit} collected with 'workspace/willCreateFiles' has been applied correctly.
            sendDidCreateFiles(new FileCreate(createdFileUri.toASCIIString()));
        }
    }

    private void sendDidCreateFiles(FileCreate... fileCreates) {
        var languageServerWrapper = fileOperationsManager.getLanguageServerWrapper();
        languageServerWrapper.sendNotification(ls -> {
            var params = new CreateFilesParams(List.of(fileCreates));
            ls.getWorkspaceService()
                    .didCreateFiles(params);
        });
    }

}
