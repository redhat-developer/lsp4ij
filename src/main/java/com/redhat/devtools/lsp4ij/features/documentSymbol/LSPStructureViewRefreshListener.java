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
package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Refreshes the structure view when a language server has started.
 */
@ApiStatus.Internal
public class LSPStructureViewRefreshListener implements ProjectActivity, LanguageServerLifecycleListener {

    @Override
    @Nullable
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        LanguageServerLifecycleManager.getInstance(project).addLanguageServerLifecycleListener(this);
        return null;
    }

    @Override
    public void handleStatusChanged(LanguageServerWrapper languageServer) {
        ServerStatus status = languageServer.getServerStatus();
        if (status == ServerStatus.started || status == ServerStatus.stopped) {
            refreshStructureView(languageServer.getProject());
        }
    }

    private void refreshStructureView(Project project) {
        // Invalidate document symbol cache for the currently selected file
        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        if (selectedFiles.length > 0) {
            PsiFile psiFile = LSPIJUtils.getPsiFile(selectedFiles[0], project);
            if (psiFile != null && LSPFileSupport.hasSupport(psiFile)) {
                // clearCaches() changes the modification stamp which invalidates the LSP future cache
                psiFile.clearCaches();
            }
        }

        // Refresh the structure view
        ApplicationManager.getApplication().invokeLater(() -> {
            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED)
                    .run();
        });
    }

    @Override
    public void handleLSPMessage(Message message, MessageConsumer consumer, LanguageServerWrapper languageServer) {
    }

    @Override
    public void handleError(LanguageServerWrapper languageServer, Throwable exception) {
    }

    @Override
    public void dispose() {
    }
}
