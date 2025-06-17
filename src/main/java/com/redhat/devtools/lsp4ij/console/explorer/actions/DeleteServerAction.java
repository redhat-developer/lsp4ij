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
package com.redhat.devtools.lsp4ij.console.explorer.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Delete (only) user defined language server action.
 */
public class DeleteServerAction extends AnAction {

    private final LanguageServerDefinition languageServerDefinition;

    public DeleteServerAction(LanguageServerDefinition languageServerDefinition) {
        this.languageServerDefinition = languageServerDefinition;
        getTemplatePresentation().setText(LanguageServerBundle.message("action.lsp.console.explorer.delete.server.text"));
        getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.console.explorer.delete.server.description"));
        getTemplatePresentation().setIcon(AllIcons.General.Remove);
    }


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var project = e.getProject();
        if (project == null) {
            return;
        }
        int result = Messages.showYesNoDialog(LanguageServerBundle.message("action.lsp.console.explorer.delete.server.confirm.dialog.message",
                        languageServerDefinition.getDisplayName()),
                LanguageServerBundle.message("action.lsp.console.explorer.delete.server.confirm.dialog.title"), Messages.getQuestionIcon());
        if (result == Messages.YES) {
            LanguageServersRegistry.getInstance()
                    .removeServerDefinition(project, languageServerDefinition);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

}
