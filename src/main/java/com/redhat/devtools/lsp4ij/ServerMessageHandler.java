/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;

import javax.swing.Icon;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter.toHTML;

public class ServerMessageHandler {
    private ServerMessageHandler() {
        // this class shouldn't be instantiated
    }

    private static final String NAME_PATTERN = "%s (%s)"; //$NON-NLS-1$


    public static void logMessage(LanguageServerWrapper wrapper, MessageParams params) {
        //TODO: implements message to console
    }

    private static Icon messageTypeToIcon(MessageType type) {
        return switch (type) {
            case Error -> AllIcons.General.Error;
            case Info, Log -> AllIcons.General.Information;
            case Warning -> AllIcons.General.Warning;
        };
    }

    private static NotificationType messageTypeToNotificationType(MessageType type) {
        return switch (type) {
            case Error -> NotificationType.ERROR;
            case Info, Log -> NotificationType.INFORMATION;
            case Warning -> NotificationType.WARNING;
        };
    }


    /**
     * Implements the LSP <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessage">window/showMessage</a> specification.
     * @param title the notification title
     * @param params the message parameters
     */
    public static void showMessage(String title, MessageParams params) {
        Notification notification = new Notification(LanguageServerBundle.message("language.server.protocol.groupId"), title, toHTML(params.getMessage()), messageTypeToNotificationType(params.getType()));
        notification.setListener(NotificationListener.URL_OPENING_LISTENER);
        notification.setIcon(messageTypeToIcon(params.getType()));
        Notifications.Bus.notify(notification);
    }

    /**
     * Implements the LSP <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessageRequest">window/showMessageRequest</a> specification.
     * @param wrapper the language server wrapper
     * @param params the message request parameters
     */
    public static CompletableFuture<MessageActionItem> showMessageRequest(LanguageServerWrapper wrapper, ShowMessageRequestParams params) {
        String[] options = params.getActions().stream().map(MessageActionItem::getTitle).toArray(String[]::new);
        CompletableFuture<MessageActionItem> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            MessageActionItem result = new MessageActionItem();
            int dialogResult = Messages.showIdeaMessageDialog(null, params.getMessage(), wrapper.serverDefinition.getDisplayName(), options, 0, Messages.getInformationIcon(), null);
            if (dialogResult != -1) {
                result.setTitle(options[dialogResult]);
            }
            future.complete(result);
        });
        return future;
    }
}
