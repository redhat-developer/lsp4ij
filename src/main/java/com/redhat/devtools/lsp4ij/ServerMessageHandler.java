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
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter.toHTML;

public class ServerMessageHandler {

    private static final ShowDocumentResult SHOW_DOCUMENT_RESULT_WITH_SUCCESS = new ShowDocumentResult(true);

    public static final ShowDocumentResult SHOW_DOCUMENT_RESULT_WITH_FAILURE = new ShowDocumentResult(false);

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
     *
     * @param title  the notification title
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
     *
     * @param wrapper the language server wrapper
     * @param params  the message request parameters
     */
    public static CompletableFuture<MessageActionItem> showMessageRequest(LanguageServerWrapper wrapper, ShowMessageRequestParams params) {
        String[] options = params.getActions().stream().map(MessageActionItem::getTitle).toArray(String[]::new);
        CompletableFuture<MessageActionItem> future = new CompletableFuture<>();

        ApplicationManager.getApplication()
                .invokeLater(() -> {
                    MessageActionItem result = new MessageActionItem();
                    int dialogResult = Messages.showIdeaMessageDialog(null, params.getMessage(), wrapper.getServerDefinition().getDisplayName(), options, 0, Messages.getInformationIcon(), null);
                    if (dialogResult != -1) {
                        result.setTitle(options[dialogResult]);
                    }
                    future.complete(result);
                });
        return future;
    }

    public static CompletableFuture<ShowDocumentResult> showDocument(@NotNull ShowDocumentParams params,
                                                                     @NotNull Project project) {
        String uri = params.getUri();
        if (StringUtils.isEmpty(uri)) {
            return CompletableFuture
                    .completedFuture(SHOW_DOCUMENT_RESULT_WITH_FAILURE);
        }
        if (params.getExternal() != null && params.getExternal()) {
            /**
             * Indicates to show the resource in an external program.
             * To show for example <a href="https://www.eclipse.org/">
             * https://www.eclipse.org/</a>
             * in the default WEB browser set to {@code true}.
             */
            BrowserUtil.browse(params.getUri());
            return CompletableFuture
                    .completedFuture(SHOW_DOCUMENT_RESULT_WITH_SUCCESS);
        }
        boolean focusEditor = params.getTakeFocus() != null ? params.getTakeFocus() : false;
        var position = params.getSelection() != null ? params.getSelection().getStart() : null;

        CompletableFuture<ShowDocumentResult> future = new CompletableFuture<>();
        ApplicationManager.getApplication()
                .invokeLater(() -> {
                    if (LSPIJUtils.openInEditor(uri, position, focusEditor, project)) {
                        future.complete(SHOW_DOCUMENT_RESULT_WITH_SUCCESS);
                    }
                    future.complete(SHOW_DOCUMENT_RESULT_WITH_FAILURE);
                });
        return future;
    }
}
