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
package com.redhat.devtools.lsp4ij.console.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.settings.LanguageServerView;
import org.jetbrains.annotations.NotNull;

/**
 * Action to update the UI language server view from
 * the registered language server definition + settings.
 */
public class ResetLanguageServerSettingsAction extends AnAction {

    private final LanguageServerView languageServerView;
    public ResetLanguageServerSettingsAction(LanguageServerView languageServerView) {
        this.languageServerView = languageServerView;
        final String message = LanguageServerBundle.message("action.lsp.detail.reset.text");
        getTemplatePresentation().setDescription(message);
        getTemplatePresentation().setText(message);
        getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.detail.reset.description"));
        getTemplatePresentation().setIcon(AllIcons.Actions.Undo);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        languageServerView.reset();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(languageServerView.isModified());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
