/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Enables a language server which too many failed starts disabled, and starts it again, from the notification which
 * says so.
 */
public class EnableAndStartLanguageServerAction extends NotificationAction {

    private final @NotNull LanguageServerWrapper languageServer;

    public EnableAndStartLanguageServerAction(@NotNull LanguageServerWrapper languageServer) {
        super(LanguageServerBundle.message("action.language.server.enable.and.start.text"));
        this.languageServer = languageServer;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        // A dispose expires the notification, but a click may come first
        if (!languageServer.isDisposed()) {
            // It enables the language server, and counts its failed starts from 0 again
            languageServer.restart();
        }
    }
}
