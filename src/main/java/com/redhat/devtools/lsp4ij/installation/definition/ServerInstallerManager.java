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

import com.intellij.ide.actions.ShowLogAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Disposer;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.installation.PrintableProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.logging.Level;

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
public class ServerInstallerManager extends InstallerTaskRegistry implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerInstallerManager.class);

    // The parent of the extension point listener, which dispose() disposes, so that it ends with this service. Not a
    // CoroutineScope: the overload of addExtensionPointListener with one is internal from 2024.3 to 2026.2 (#2274)
    private final Disposable extensionPointListenerDisposable =
            Disposer.newDisposable("LSP4IJ installer task extension point listener");

    private ServerInstallerManager() {
        super();
        var beans = InstallerTaskFactoryPointBean.EP_NAME.getExtensions();
        for (InstallerTaskFactoryPointBean bean : beans) {
            registerFactoryFromBean(bean);
        }
        InstallerTaskFactoryPointBean.EP_NAME.addExtensionPointListener(
                new ExtensionPointListener<>() {
                    @Override
                    public void extensionAdded(@NotNull InstallerTaskFactoryPointBean bean,
                                               @NotNull PluginDescriptor pluginDescriptor) {
                        registerFactoryFromBean(bean);
                    }

                    @Override
                    public void extensionRemoved(@NotNull InstallerTaskFactoryPointBean bean,
                                                 @NotNull PluginDescriptor pluginDescriptor) {
                        unregisterFactory(bean.type);
                    }
                }, extensionPointListenerDisposable);
    }

    private void registerFactoryFromBean(@NotNull InstallerTaskFactoryPointBean bean) {
        try {
            registerFactory(bean.type, bean.getInstance());
        } catch (Exception e) {
            LOGGER.warn("Failed to instantiate installerTaskFactory for type='{}'", bean.type, e);
        }
    }

    @Override
    public void dispose() {
        // Removes the extension point listener
        Disposer.dispose(extensionPointListenerDisposable);
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
        if (hasError) {
            // What the installation printed, and the IDE's log, which has each step, as its command, its exit code and
            // the output of a step which failed (#2296)
            context.getFailureNotificationActions().forEach(notification::addAction);
            if (ShowLogAction.isSupported()) {
                notification.addAction(ShowLogAction.notificationAction());
            }
        }
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
        context.setInstallerName(installerName);
        if (!hasTaskToExecute(serverInstallerDescriptor, context.getAction())) {
            // Else the installation would do nothing, and report that it completed successfully (#1229)
            notifyNothingToExecute(installerName, context);
            return;
        }
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

    /**
     * Returns true if the given installer declares the task that the given action is for: {@code check} for a check,
     * and {@code run} for a run, with or without a check before it.
     *
     * @param installerDescriptor the installer.
     * @param action              the action.
     * @return true if the given installer declares the task that the given action is for and false otherwise.
     */
    static boolean hasTaskToExecute(@NotNull ServerInstallerDescriptor installerDescriptor,
                                    @NotNull InstallerContext.InstallerAction action) {
        return switch (action) {
            case CHECK -> installerDescriptor.getCheck() != null;
            case RUN, CHECK_AND_RUN -> installerDescriptor.getRun() != null;
        };
    }

    private static void notifyNothingToExecute(@NotNull String name, @NotNull InstallerContext context) {
        boolean check = context.getAction() == InstallerContext.InstallerAction.CHECK;
        String task = check ? "check" : "run";
        context.clear();
        context.print(name);
        context.printError("The installer has no \"" + task + "\".");
        var notification = new Notification(ServerMessageHandler.LSP_WINDOW_SHOW_MESSAGE_GROUP_ID,
                (check ? "Nothing to check for <code>" : "Nothing to install for <code>") + name + "</code>.",
                "The installer has no <code>" + task + "</code>.",
                NotificationType.WARNING);
        Notifications.Bus.notify(notification, context.getProject());
    }

    public boolean install(@NotNull ServerInstallerDescriptor installerDescriptor,
                           @NotNull InstallerContext context) {
        context.setInstallerName(installerDescriptor.getName());
        context.log(Level.INFO, switch (context.getAction()) {
            case CHECK -> "checking the installation";
            case RUN -> "running the installation";
            case CHECK_AND_RUN -> "checking and running the installation";
        });
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
        // How the installation ended, as its notification says, for the log
        context.log(Level.INFO, context.getAction() == InstallerContext.InstallerAction.CHECK
                ? (hasError ? "the check failed" : "the check passed")
                : (hasError ? "the installation failed" : "the installation completed successfully"));
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
