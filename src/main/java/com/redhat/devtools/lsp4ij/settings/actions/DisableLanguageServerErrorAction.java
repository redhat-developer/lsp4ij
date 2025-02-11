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
package com.redhat.devtools.lsp4ij.settings.actions;

import com.intellij.notification.Notification;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.ErrorReportingKind;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Disable error reporting action.
 */
public class DisableLanguageServerErrorAction extends AnAction {

    private final @NotNull Notification notification;
    private final @NotNull LanguageServerDefinition languageServerDefinition;
    private final @NotNull Project project;

    public DisableLanguageServerErrorAction(@NotNull Notification notification,
                                            @NotNull LanguageServerDefinition languageServerDefinition,
                                            @NotNull Project project) {
        super(LanguageServerBundle.message("action.language.server.error.reporting.disable.text"));
        this.notification = notification;
        this.languageServerDefinition = languageServerDefinition;
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        UserDefinedLanguageServerSettings manager = UserDefinedLanguageServerSettings.getInstance(project);
        manager.updateSettings(languageServerDefinition.getId(),
                new UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings()
                        .setErrorReportingKind(ErrorReportingKind.none));
        notification.expire();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}