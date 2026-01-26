/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the connection between open editor files and Language Servers:
 * <ul>
 *   <li>Starts Language Servers when a supported file becomes active in the editor.</li>
 *   <li>Releases resources when a file is closed.</li>
 *   <li>Properly stops Language Servers when the project is closed.</li>
 * </ul>
 */
public class ConnectDocumentToLanguageServerSetupParticipant implements ProjectManagerListener, FileEditorManagerListener {

    private static void connectToLanguageServer(@NotNull VirtualFile file, @NotNull Project project) {
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
        if (psiFile != null) {
            // Force the startup of all Language Servers mapped to this file.
            LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(psiFile, null, null);
        }
    }

    @Override
    public void projectOpened(@NotNull Project project) {
        // Subscribe to file open/close/selection events for this project.
        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
    }

    @Override
    public void projectClosing(@NotNull Project project) {
        // Stop active Language Servers and clean up resources before the project is closed.
        LanguageServerLifecycleManager.getInstance(project).dispose();
        LanguageServiceAccessor.getInstance(project).projectClosing(project);
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        // Do not send textDocument/didOpen here:
        // this method is also called when a file is opened in the background (tab not visible).
        // To optimize performance, Language Servers are only started when the editor tab becomes active.
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        if (file == null) {
            return;
        }
        var project = event.getManager().getProject();

        // Since file-to-server matching requires a read action,
        // and Language Server startup may be expensive,
        // schedule the connection asynchronously after indexing is finished.
        // Note: selectionChanged is always called on EDT, so we move getPsiFile()
        // and isFileSupported() calls off EDT to avoid slow operation violations.
        ProjectIndexingManager
                .waitForIndexingAll()
                .thenApplyAsync(unused -> {
                    if (!LanguageServersRegistry.getInstance().isFileSupported(file, project)) {
                        return null;
                    }
                    PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
                    if (psiFile == null || LSPFileSupport.hasSupport(psiFile)) {
                        // No PsiFile available, or the file is already connected to a Language Server.
                        return null;
                    }
                    connectToLanguageServer(file, project);
                    return null;
                });
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        // When a file is closed:
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, source.getProject());
        if (psiFile != null) {
            if (LSPFileSupport.hasSupport(psiFile)) {
                // If the file is associated with a Language Server,
                // release its resources (code lenses, inlay hints, color providers, etc.).
                LSPFileSupport.getSupport(psiFile).dispose();
            }
        }
    }
}
