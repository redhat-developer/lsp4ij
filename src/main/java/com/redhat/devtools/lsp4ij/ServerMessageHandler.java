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
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.console.LSPConsoleToolWindowPanel;
import com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;

public class ServerMessageHandler {

    public static final String LSP_WINDOW_SHOW_MESSAGE_GROUP_ID = "LSP/window/showMessage";
    public static final String LSP_WINDOW_SHOW_MESSAGE_REQUEST_GROUP_ID = "LSP/window/showMessageRequest";

    private static final ShowDocumentResult SHOW_DOCUMENT_RESULT_WITH_SUCCESS = new ShowDocumentResult(true);
    private static final ShowDocumentResult SHOW_DOCUMENT_RESULT_WITH_FAILURE = new ShowDocumentResult(false);

    private ServerMessageHandler() {
        // this class shouldn't be instantiated
    }

    /**
     * Implements the LSP <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_logMessage">window/logMessage</a> specification.
     *
     * @param serverDefinition the language server definition.
     * @param params  the message request parameters.
     * @param project the project.
     */
    public static void logMessage(@NotNull LanguageServerDefinition serverDefinition,
                                  @NotNull MessageParams params,
                                  @NotNull Project project) {
        LSPConsoleToolWindowPanel.showLog(serverDefinition, params, project );
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
     * @param title   the notification title.
     * @param params  the message parameters.
     * @param project the project.
     */
    public static void showMessage(@NotNull String title,
                                   @NotNull MessageParams params,
                                   @NotNull Project project) {
        Notification notification = new Notification(LSP_WINDOW_SHOW_MESSAGE_GROUP_ID,
                title,
                MarkdownConverter.getInstance(project).toHtml(params.getMessage()),
                messageTypeToNotificationType(params.getType()));
        notification.setListener(NotificationListener.URL_OPENING_LISTENER);
        notification.setIcon(messageTypeToIcon(params.getType()));
        Notifications.Bus.notify(notification, project);
    }

    /**
     * Implements the LSP <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessageRequest">window/showMessageRequest</a> specification.
     *
     * @param languageServerName the language server name.
     * @param params  the message request parameters.
     * @param project the project.
     */
    public static CompletableFuture<MessageActionItem> showMessageRequest(@NotNull String languageServerName,
                                                                          @NotNull ShowMessageRequestParams params,
                                                                          @NotNull Project project) {
        CompletableFuture<MessageActionItem> future = new CompletableFuture<>();
        ApplicationManager.getApplication()
                .invokeLater(() -> {
                    String content = MarkdownConverter.getInstance(project).toHtml(params.getMessage());
                    final Notification notification = new Notification(
                            LSP_WINDOW_SHOW_MESSAGE_REQUEST_GROUP_ID,
                            languageServerName,
                            content,
                            messageTypeToNotificationType(params.getType()));
                    notification.setIcon(messageTypeToIcon(params.getType()));
                    for (var action : params.getActions()) {
                        notification.addAction(new AnAction(action.getTitle()) {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e) {
                                MessageActionItem result = new MessageActionItem();
                                result.setTitle(action.getTitle());
                                future.complete(result);
                                notification.expire();
                            }

                            @Override
                            public @NotNull ActionUpdateThread getActionUpdateThread() {
                                return ActionUpdateThread.BGT;
                            }
                        });
                    }
                    notification.whenExpired(()-> {
                        if (!future.isDone()) {
                            future.cancel(true);
                        }
                    });

                    Notifications.Bus.notify(notification, project);
                    var balloon= notification.getBalloon();
                    if (balloon != null) {
                        balloon.addListener(new JBPopupListener() {
                            @Override
                            public void onClosed(@NotNull LightweightWindowEvent event) {
                                if (!future.isDone()) {
                                    future.cancel(true);
                                }
                            }
                        });
                    }
                });

        return future;
    }

    public static CompletableFuture<ShowDocumentResult> showDocument(@NotNull ShowDocumentParams params,
                                                                     @Nullable FileUriSupport fileUriSupport,
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
                .executeOnPooledThread(() -> {
                    if (LSPIJUtils.openInEditor(uri, position, focusEditor, false, fileUriSupport, project)) {
                        future.complete(SHOW_DOCUMENT_RESULT_WITH_SUCCESS);
                    }
                    future.complete(SHOW_DOCUMENT_RESULT_WITH_FAILURE);
                });
        return future;
    }
}
