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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the connection between open editor files and Language Servers:
 * <ul>
 *   <li>Starts Language Servers when a supported file becomes active in the editor.</li>
 *   <li>Releases resources when a file is closed.</li>
 *   <li>Properly stops Language Servers when the project is closed ({@link ProjectClosingListener}).</li>
 * </ul>
 * <p>
 * It is declared as a project listener, so the platform delivers it the editor events of a project from the start,
 * including the selection of the editors reopened when the project opens.
 */
public class ConnectDocumentToLanguageServerSetupParticipant implements FileEditorManagerListener {

    /**
     * Connects the file of a selected tab to its language servers. The connection is released once it is made, as a
     * request's is: an editor that shows the file then keeps it connected, and a file whose tab closed meanwhile is
     * disconnected again, since the close's disconnection has already happened.
     */
    private static void connectToLanguageServer(@NotNull PsiFile psiFile, @NotNull Project project) {
        var connection = new DocumentConnectionScope();
        LanguageServiceAccessor.getInstance(project)
                .getLanguageServersForScope(psiFile, null, null, connection)
                .whenComplete((servers, error) -> connection.close());
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

        // Don't start Language Servers if the project is being disposed/closed
        if (project.isDisposed()) {
            return;
        }

        // Since file-to-server matching requires a read action,
        // and Language Server startup may be expensive,
        // schedule the connection asynchronously after indexing is finished.
        // Note: selectionChanged is always called on EDT, so we move getPsiFile()
        // and isFileSupported() calls off EDT to avoid slow operation violations.
        ProjectIndexingManager
                .waitForIndexingAll()
                .thenAccept(unused -> {
                    // Double-check project is still valid after async operations
                    if (project.isDisposed()) {
                        return;
                    }
                    // Execute in non-blocking ReadAction since getPsiFile and connectToLanguageServer require it
                    ReadAction.nonBlocking(() -> {
                                if (project.isDisposed()) {
                                    return;
                                }
                                if (!FileEditorManager.getInstance(project).isFileOpen(file)) {
                                    // The tab was closed before this ran, and its file has no editor to connect for
                                    return;
                                }
                                PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
                                if (psiFile == null || LSPFileSupport.hasSupport(psiFile)) {
                                    // No PsiFile available, or the file is already connected to a Language Server.
                                    return;
                                }
                                connectToLanguageServer(psiFile, project);
                            })
                            .expireWhen(() -> project.isDisposed())
                            .submit(AppExecutorUtil.getAppExecutorService());
                });
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        var project = source.getProject();
        if (project.isDisposed()) {
            return;
        }
        ReadAction.nonBlocking(() -> {
                    if (project.isDisposed()) {
                        return;
                    }
                    PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
                    if (psiFile != null && LSPFileSupport.hasSupport(psiFile)) {
                        LSPFileSupport.getSupport(psiFile).dispose();
                    }
                })
                .coalesceBy(this, file, project)
                .expireWhen(() -> project.isDisposed())
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    /**
     * Stops the active Language Servers of a project when it is closed.
     */
    public static class ProjectClosingListener implements ProjectManagerListener {

        @Override
        public void projectClosing(@NotNull Project project) {
            // Stop active Language Servers and clean up resources before the project is closed.
            LanguageServerLifecycleManager.getInstance(project).dispose();
            LanguageServiceAccessor.getInstance(project).projectClosing(project);
        }
    }
}
