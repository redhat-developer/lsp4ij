/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.console.LSPConsoleToolWindowPanel;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Select "log" tab (for a given language server or language server definition) action.
 */
public class ShowErrorLogAction extends AnAction {

    private final @NotNull Runnable selectLogTab;

    public ShowErrorLogAction(@NotNull LanguageServerDefinition languageServerDefinition,
                              @NotNull Project project) {
        this(() -> LSPConsoleToolWindowPanel.selectLogTab(languageServerDefinition, project));
    }

    public ShowErrorLogAction(@NotNull LanguageServerWrapper languageServer) {
        // By definition and root: another language server with that root can replace this one, and takes its node over
        this(selectLogTabOf(languageServer.getServerDefinition(), languageServer.getInstanceRoot(), languageServer.getProject()));
    }

    private static @NotNull Runnable selectLogTabOf(@NotNull LanguageServerDefinition serverDefinition,
                                                    @Nullable WorkspaceFolder instanceRoot,
                                                    @NotNull Project project) {
        return () -> LSPConsoleToolWindowPanel.selectLogTab(serverDefinition, instanceRoot, project);
    }

    private ShowErrorLogAction(@NotNull Runnable selectLogTab) {
        super(LanguageServerBundle.message("action.language.server.error.show.logs.text"));
        this.selectLogTab = selectLogTab;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        selectLogTab.run();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}