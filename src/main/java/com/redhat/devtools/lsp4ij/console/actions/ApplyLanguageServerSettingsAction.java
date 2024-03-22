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
 * Action to update the proper language server settings and language server definition
 * from the UI language server view fields.
 */
public class ApplyLanguageServerSettingsAction extends AnAction {

    private final LanguageServerView languageServerView;
    public ApplyLanguageServerSettingsAction(LanguageServerView languageServerView) {
        this.languageServerView = languageServerView;
        final String message = LanguageServerBundle.message("action.lsp.detail.apply.text");
        getTemplatePresentation().setDescription(message);
        getTemplatePresentation().setText(message);
        getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.detail.apply.description"));
        getTemplatePresentation().setIcon(AllIcons.Actions.MenuSaveall);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        languageServerView.apply();
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
