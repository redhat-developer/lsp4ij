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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;

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
 * This class is registered as a project-level service and can be obtained via
 * {@link #getInstance(Project)}.
 * </p>
 */
public class ServerInstallerManager extends InstallerTaskRegistry {

    private final @NotNull Project project;

    private ServerInstallerManager(@NotNull Project project) {
        super();
        this.project = project;
        var beans = InstallerTaskFactoryPointBean.EP_NAME.getExtensions();
        for (int i = 0; i < beans.length; i++) {
            try {
                var bean = beans[i];
                super.registerFactory(bean.type, bean.getInstance());
            } catch (Exception e) {

            }
        }
    }

    /**
     * Returns the server installer manager instance for the given project.
     *
     * @param project the project.
     * @return the server installer manager instance for the given project.
     */
    public static ServerInstallerManager getInstance(@NotNull Project project) {
        return project.getService(ServerInstallerManager.class);
    }

    private static @NotNull Notification getNotification(@NotNull String name, @NotNull InstallerContext context, Boolean checkResult, Boolean checkRun) {
        boolean hasError = (checkResult != null && !checkResult) || (checkRun != null && !checkRun);
        StringBuilder message = new StringBuilder();
        for (var item : context.getStatus()) {
            if (!message.isEmpty()) {
                message.append("\n");
                message.append(" - ");
            }
            message.append(item.message());
        }
        var notification = new Notification(ServerMessageHandler.LSP_WINDOW_SHOW_MESSAGE_GROUP_ID,
                (hasError ? "Installation of <code>" + name + "</code> failed." : "Installation of <code>" + name + "</code> completed successfully!"),
                message.toString(),
                hasError ? NotificationType.ERROR : NotificationType.INFORMATION);
        notification.setListener(NotificationListener.URL_OPENING_LISTENER);
        return notification;
    }

    public void install(@NotNull String installerConfigurationContent,
                        @NotNull InstallerContext context) {
        JsonObject json = loadJson(installerConfigurationContent);
        String installerName = JSONUtils.getString(json, "name");
        StringBuilder title = new StringBuilder();
        switch (context.getAction()) {
            case CHECK -> title.append("Checking '");
            case RUN -> title.append("Running '");
            case CHECK_AND_RUN -> title.append("Checking and running '");
        }
        title.append(installerName);
        title.append("' installation...");

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title.toString(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                context.setProgressIndicator(new DelegatingProgressIndicator(indicator) {

                    @Override
                    public void setText2(@Nls @NlsContexts.ProgressDetails String text) {
                        super.setText2(text);
                        if (text.startsWith("<html><code>")) {
                            String s = text.substring("<html><code>".length(), text.length() - "</code></html>".length());
                            context.printProgress(s);
                        }
                    }
                });
                install(json, context);
            }
        });
    }

    private JsonObject loadJson(@NotNull String installerConfigurationContent) {
        JsonElement installerConfiguration = JsonParser.parseReader(new StringReader(installerConfigurationContent));
        if (installerConfiguration.isJsonObject()) {
            return installerConfiguration.getAsJsonObject();
        } else {
            throw new RuntimeException("Invalid Json object");
        }
    }

    private void install(@NotNull JsonObject json,
                         @NotNull InstallerContext context) {
        var installerDeclaration = loadInstaller(json);
        install(installerDeclaration, context);
    }

    private void install(@NotNull ServerInstallerDescriptor installerDescriptor,
                         @NotNull InstallerContext context) {
        context.clear();
        context.print(installerDescriptor.getName());

        Boolean checkResult = null;
        Boolean checkRun = null;
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

        Notification notification = getNotification(installerDescriptor.getName(), context, checkResult, checkRun);
        Notifications.Bus.notify(notification, context.getProject());
    }

}
