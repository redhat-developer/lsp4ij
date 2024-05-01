/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mitja Leino <mitja.leino@hotmail.com> - Initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.console.explorer.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.LanguageServerListConfigurable;
import org.jetbrains.annotations.NotNull;

public class EditServerAction extends AnAction {
    private final LanguageServerDefinition languageServerDefinition;

    public EditServerAction(LanguageServerDefinition languageServerDefinition) {
        this.languageServerDefinition = languageServerDefinition;
        getTemplatePresentation().setText(LanguageServerBundle.message("action.lsp.console.explorer.edit.server.text"));
        getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.console.explorer.edit.server.description"));
        getTemplatePresentation().setIcon(AllIcons.Actions.Edit);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // Set up node override for opening the settings page with the correct node selected
        Project project = e.getProject();
        if (project != null) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, LanguageServerListConfigurable.class, languageServerListConfigurable -> {
                languageServerListConfigurable.name = languageServerDefinition.getDisplayName();
            });
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}