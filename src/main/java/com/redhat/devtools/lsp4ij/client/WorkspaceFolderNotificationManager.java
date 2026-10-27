/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.client;

import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.ServerStatus;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.function.Function;

/**
 * Manager for sending workspace folder notifications to the language server.
 */
public class WorkspaceFolderNotificationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceFolderNotificationManager.class);

    private final LanguageServerWrapper serverWrapper;

    public WorkspaceFolderNotificationManager(@NotNull LanguageServerWrapper serverWrapper) {
        this.serverWrapper = serverWrapper;
    }

    /**
     * Computes the workspace folder for the given file if it needs to be notified.
     * This method performs I/O operations and should be called OUTSIDE synchronized blocks.
     *
     * @param file the file being connected
     * @return the workspace folder to notify, or null if not needed
     */
    @Nullable
    public WorkspaceFolder computeWorkspaceFolderToNotify(@NotNull VirtualFile file) {
        if (serverWrapper.getServerStatus() != ServerStatus.started) {
            return null;
        }

        var clientFeatures = serverWrapper.getClientFeatures();
        var workspaceFolderFeature = clientFeatures.getWorkspaceFolderFeature();

        // Compute workspace folder (slow I/O operation)
        return workspaceFolderFeature.computeWorkspaceFolderToNotify(file);
    }

    /**
     * Marks the workspace folder as notified.
     * This is a fast operation that should be called INSIDE synchronized blocks.
     *
     * @param folder the workspace folder to mark
     * @return true if the folder was newly marked, false if already notified
     */
    public boolean markFolderAsNotified(@NotNull WorkspaceFolder folder) {
        var clientFeatures = serverWrapper.getClientFeatures();
        var workspaceFolderFeature = clientFeatures.getWorkspaceFolderFeature();
        return workspaceFolderFeature.markFolderAsNotified(folder);
    }

    /**
     * Sends a workspace/didChangeWorkspaceFolders notification to add a folder, after the notifications sent
     * before it.
     *
     * @param folder the workspace folder to notify
     */
    public void sendFolderAddedNotification(@NotNull WorkspaceFolder folder) {
        if (serverWrapper.getLanguageServer() == null) {
            return;
        }
        serverWrapper.sendNotification(folderAddedNotification(folder));
    }

    /**
     * Returns the workspace/didChangeWorkspaceFolders notification that adds a folder, to send with
     * {@link LanguageServerWrapper#sendNotification(Function)}.
     *
     * @param folder the workspace folder to add
     * @return the notification
     */
    public @NotNull Function<LanguageServer, LanguageServer> folderAddedNotification(@NotNull WorkspaceFolder folder) {
        WorkspaceFoldersChangeEvent event = new WorkspaceFoldersChangeEvent();
        event.setAdded(Collections.singletonList(folder));
        event.setRemoved(Collections.emptyList());

        DidChangeWorkspaceFoldersParams params = new DidChangeWorkspaceFoldersParams();
        params.setEvent(event);
        return languageServer -> {
            languageServer.getWorkspaceService().didChangeWorkspaceFolders(params);
            LOGGER.info("Workspace folder added: " + folder.getUri());
            return languageServer;
        };
    }
}
