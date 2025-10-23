/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;


import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Ref;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * TODO: Revisit this task
 */
public class ShowMessageTask extends InstallerTask {

    private final @NotNull List<InstallerTask> actions;
    private final @NotNull String content;

    public ShowMessageTask(@Nullable String id,
                           @Nullable String name,
                           @NotNull String content,
                           @Nullable InstallerTask onFail,
                           @Nullable InstallerTask onSuccess,
                           @NotNull List<InstallerTask> actions,
                           @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        super(id, name, onFail, onSuccess, serverInstallerDeclaration);
        this.content = content;
        this.actions = actions;
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        Ref<InstallerTask> selectedAction = new Ref<>();
        String title =  getName() != null  ? getName() : "Error";
        Notification notification = new Notification(ServerMessageHandler.LSP_WINDOW_SHOW_MESSAGE_GROUP_ID,
                title,
                content,
                NotificationType.ERROR);
        notification.setListener(NotificationListener.URL_OPENING_LISTENER);

        for (var action : actions) {
            notification.addAction(new AnAction(action.getName()) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                    selectedAction.set(action);
                }
            });
        }
        Notifications.Bus.notify(notification, context.getProject());

        if (actions.isEmpty()) {
            context.setShowNotification(false);
            return false;
        }



        while(selectedAction.isNull() || notification.isExpired()) {
            // Wait...
            try {
                synchronized (selectedAction) {
                    selectedAction.wait(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!selectedAction.isNull()) {
            var stepAction = selectedAction.get();
            return stepAction.execute(context);
        }
        return false;
    }
}
