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
package com.redhat.devtools.intellij.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.intellij.lsp4ij.client.CoalesceByKey;
import com.redhat.devtools.intellij.lsp4ij.internal.PromiseToCompletableFuture;
import com.redhat.devtools.intellij.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

/**
 * Track file opened / closed to start language servers / disconnect file from language servers.
 */
public class ConnectDocumentToLanguageServerSetupParticipant implements ProjectManagerListener, FileEditorManagerListener {

    private static class ConnectToLanguageServerCompletableFuture extends PromiseToCompletableFuture<Void> {

        private static final String MESSAGE_KEY = "Connect ''{0}'' file to language servers.";

        public ConnectToLanguageServerCompletableFuture(@NotNull VirtualFile file, @NotNull Project project) {
            super(monitor -> {
                connectToLanguageServer(file, project);
                return null;
            }, MessageFormat.format(MESSAGE_KEY, file.getUrl()), project, null, new CoalesceByKey(ConnectDocumentToLanguageServerSetupParticipant.class.getName(), file.getUrl()));
            init();
        }
    }

    @Override
    public void projectOpened(@NotNull Project project) {
        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
    }

    @Override
    public void projectClosing(@NotNull Project project) {
        LanguageServerLifecycleManager.getInstance(project).dispose();
        LanguageServiceAccessor.getInstance(project).projectClosing(project);
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        Project project = source.getProject();
        // As document matcher requires read action, and language server starts can take some times, we connect the file in a future
        // to avoid starting language server in the EDT Thread which could freeze IJ.
        // Wait for indexing is finished and read action is enabled
        // --> force the start of all languages servers mapped with the given file when indexing is finished and read action is allowed
        new ConnectToLanguageServerCompletableFuture(file, project);
    }

    private static void connectToLanguageServer(@NotNull VirtualFile file, @NotNull Project project) {
        // Force the start of all languages servers mapped with the given file
        // Server capabilities filter is set to null to avoid waiting
        // for the start of the server when server capabilities are checked
        LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, null);
    }

}
