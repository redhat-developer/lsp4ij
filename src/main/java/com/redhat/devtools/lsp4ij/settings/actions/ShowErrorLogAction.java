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
import com.redhat.devtools.lsp4ij.console.LSPConsoleToolWindowPanel;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Select "log" tab (for a given language server definition) action.
 */
public class ShowErrorLogAction extends AnAction {

    private final @NotNull LanguageServerDefinition languageServerDefinition;
    private final @NotNull Project project;

    public ShowErrorLogAction(@NotNull LanguageServerDefinition languageServerDefinition,
                              @NotNull Project project) {
        super(LanguageServerBundle.message("action.language.server.error.show.logs.text"));
        this.languageServerDefinition = languageServerDefinition;
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        LSPConsoleToolWindowPanel.selectLogTab(languageServerDefinition, project);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}