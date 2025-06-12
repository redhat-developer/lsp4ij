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
package com.redhat.devtools.lsp4ij.installation.definition;

import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.NlsContexts;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.installation.PrintableProgressIndicator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the installation of Language Servers(LSP) and Debug Adapter Protocol (DAP) servers
 * for a given IntelliJ project.
 * <p>
 * This manager handles the registration of installer task factories, parses server installer
 * JSON descriptors, and executes the declared tasks (e.g., check or run).
 * </p>
 * <p>
 * Typical use includes verifying the presence of a server or triggering its installation
 * when missing, based on user-defined configurations.
 * </p>
 * <p>
 * This class is registered as a application-level service and can be obtained via
 * {@link #getInstance()}.
 * </p>
 */
public class ServerInstallerManager extends InstallerTaskRegistry {

    private ServerInstallerManager() {
        super();
        var beans = InstallerTaskFactoryPointBean.EP_NAME.getExtensions();
        for (InstallerTaskFactoryPointBean bean : beans) {
            try {
                super.registerFactory(bean.type, bean.getInstance());
            } catch (Exception e) {
                // Do nothing
            }
        }
    }

    /**
     * Returns the server installer manager instance for the given project.
     *
     * @return the server installer manager instance for the given project.
     */
    public static ServerInstallerManager getInstance() {
        return ApplicationManager.getApplication().getService(ServerInstallerManager.class);
    }

    private static @NotNull Notification getNotification(@NotNull String name,
                                                         @NotNull InstallerContext context,
                                                         boolean hasError) {
        String message = getMessage(context);
        var notification = new Notification(ServerMessageHandler.LSP_WINDOW_SHOW_MESSAGE_GROUP_ID,
                (hasError ? "Installation of <code>" + name + "</code> failed." : "Installation of <code>" + name + "</code> completed successfully."),
                message,
                hasError ? NotificationType.ERROR : NotificationType.INFORMATION);
        notification.setListener(NotificationListener.URL_OPENING_LISTENER);
        return notification;
    }

    private static @NotNull String getMessage(@NotNull InstallerContext context) {
        StringBuilder message = new StringBuilder();
        var items = context.getStatus();
        boolean hasSeveralMessages = items.size() > 1;
        if (hasSeveralMessages) {
            message.append("<ul>");
        }
        for (var item : context.getStatus()) {
            if (hasSeveralMessages) {
                message.append("<li>");
            }
            message.append(item.message());
            if (hasSeveralMessages) {
                message.append("</li>");
            }
        }
        if (hasSeveralMessages) {
            message.append("</ul>");
        }
        return message.toString();
    }

    public void install(@NotNull String installerConfigurationContent,
                        @NotNull InstallerContext context) {
        final var serverInstallerDescriptor = loadInstaller(installerConfigurationContent);
        String installerName = serverInstallerDescriptor.getName();
        StringBuilder title = new StringBuilder();
        switch (context.getAction()) {
            case CHECK -> title.append("Checking '");
            case RUN -> title.append("Running '");
            case CHECK_AND_RUN -> title.append("Checking and running '");
        }
        title.append(installerName);
        title.append("' installation...");

        ProgressManager.getInstance().run(new Task.Backgroundable(context.getProject(), title.toString(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                context.setProgressIndicator(new PrintableProgressIndicator(indicator) {

                    @Override
                    protected void printProgress(@NotNull String progressMessage) {
                        context.printProgress(progressMessage);
                    }
                });
                install(serverInstallerDescriptor, context);
            }
        });
    }

    public boolean install(@NotNull ServerInstallerDescriptor installerDescriptor,
                           @NotNull InstallerContext context) {
        var commandUpdater = context.getCommandLineUpdater();
        if (commandUpdater != null) {
            context.putProperty("server.command", commandUpdater.getCommandLine());
        }
        var properties = installerDescriptor.getProperties();
        var entrySet = properties.entrySet();
        for (var entry : entrySet) {
            context.putProperty(entry.getKey(), entry.getValue());
        }
        Boolean checkResult = null;
        Boolean checkRun = null;

        context.clear();
        context.print(installerDescriptor.getName());

        try {
            runPreInstallActions(context);
            var action = context.getAction();
            if (action == InstallerContext.InstallerAction.CHECK || action == InstallerContext.InstallerAction.CHECK_AND_RUN) {
                var check = installerDescriptor.getCheck();
                if (check != null) {
                    checkResult = check.execute(context);
                }
            }

            if (action == InstallerContext.InstallerAction.RUN || action == InstallerContext.InstallerAction.CHECK_AND_RUN) {
                var run = installerDescriptor.getRun();
                if (run != null) {
                    checkRun = run.execute(context);
                }
            }
        } finally {
            runPostInstallActions(context);
        }

        boolean hasError = (checkResult != null && !checkResult) || (checkRun != null && !checkRun);
        if (context.isShowNotification()) {
            Notification notification = getNotification(installerDescriptor.getName(), context, hasError);
            Notifications.Bus.notify(notification, context.getProject());
        }
        return !hasError;
    }

    private void runPreInstallActions(InstallerContext context) {
        for (Runnable action : context.getOnPreInstall()) {
            action.run();
        }
    }

    private void runPostInstallActions(InstallerContext context) {
        for (Runnable action : context.getOnPostInstall()) {
            action.run();
        }
    }
}
