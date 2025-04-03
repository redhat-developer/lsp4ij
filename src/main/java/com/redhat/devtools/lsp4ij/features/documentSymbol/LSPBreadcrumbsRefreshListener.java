/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.xml.breadcrumbs.BreadcrumbListener;
import com.intellij.xml.breadcrumbs.BreadcrumbsPanel;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.client.features.LSPBreadcrumbsFeature;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Refreshes the breadcrumbs and sticky lines for open editor tabs when a language server has started.
 */
public class LSPBreadcrumbsRefreshListener implements ProjectActivity, LanguageServerLifecycleListener {

    private static final Key<Boolean> ADDED_BREADCRUMB_LISTENER = Key.create(LSPBreadcrumbsRefreshListener.class + ".ADDED_BREADCRUMB_LISTENER");
    private static final Key<Boolean> NEEDS_RESTART = Key.create(LSPBreadcrumbsRefreshListener.class + ".NEEDS_RESTART");

    @Override
    @Nullable
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        LanguageServerLifecycleManager.getInstance(project).addLanguageServerLifecycleListener(this);
        return null;
    }

    @Override
    public void handleStatusChanged(LanguageServerWrapper languageServer) {
        if (languageServer.getServerStatus() == ServerStatus.started) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                Project project = languageServer.getProject();
                LSPBreadcrumbsFeature breadcrumbsFeature = languageServer.getClientFeatures().getBreadcrumbsFeature();

                for (VirtualFile virtualFile : FileEditorManager.getInstance(project).getOpenFiles()) {
                    PsiFile file = LSPIJUtils.getPsiFile(virtualFile, project);
                    if ((file != null) && breadcrumbsFeature.isSupported(file) && breadcrumbsFeature.isEnabled(file)) {
                        for (Editor editor : LSPIJUtils.editorsForFile(virtualFile, project)) {
                            BreadcrumbsPanel breadcrumbsComponent = BreadcrumbsPanel.getBreadcrumbsComponent(editor);
                            if (breadcrumbsComponent != null) {
                                // Only add a listener to the editor once
                                if (editor.getUserData(ADDED_BREADCRUMB_LISTENER) == null) {
                                    breadcrumbsComponent.addBreadcrumbListener(new LSPBreadcrumbListener(file, editor), breadcrumbsComponent);
                                    editor.putUserData(ADDED_BREADCRUMB_LISTENER, true);
                                }

                                // Denote the file as needing restart and queue an update to its breadcrumbs
                                file.putUserData(NEEDS_RESTART, true);
                                ApplicationManager.getApplication().invokeLater(breadcrumbsComponent::queueUpdate);
                            }
                        }
                    }
                }
            });
        }
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

    private record LSPBreadcrumbListener(PsiFile file, Editor editor) implements BreadcrumbListener {
        private LSPBreadcrumbListener(@NotNull PsiFile file, @NotNull Editor editor) {
            this.file = file;
            this.editor = editor;
        }

        @Override
        public void breadcrumbsChanged(@NotNull Iterable<? extends Crumb> crumbs) {
            // If the file is still valid and needs restart, clear that flag and schedule restart
            if (file.isValid() && (file.getUserData(NEEDS_RESTART) == Boolean.TRUE)) {
                file.putUserData(NEEDS_RESTART, false);
                // We must force the modification stamp to increment or sticky lines won't be recomputed
                file.clearCaches();
                ApplicationManager.getApplication().invokeLater(() -> LSPFileSupport.getSupport(file).restartDaemonCodeAnalyzerWithDebounce());
            }
        }
    }
}
