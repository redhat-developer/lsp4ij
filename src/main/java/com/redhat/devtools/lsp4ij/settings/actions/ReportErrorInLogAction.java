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
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.settings.ErrorReportingKind;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Disable error reporting action.
 */
public class ReportErrorInLogAction extends AnAction {

    private final Notification notification;
    private final LanguageServerItem languageServer;

    public ReportErrorInLogAction(@NotNull Notification notification,
                                  @NotNull LanguageServerItem languageServer) {
        super(LanguageServerBundle.message("action.language.server.error.reporting.in_log.text"));
        this.notification = notification;
        this.languageServer = languageServer;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        UserDefinedLanguageServerSettings manager = UserDefinedLanguageServerSettings.getInstance(languageServer.getProject());
        manager.updateSettings(languageServer.getServerDefinition().getId(),
                new UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings()
                        .setErrorReportingKind(ErrorReportingKind.in_log));
        notification.expire();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}