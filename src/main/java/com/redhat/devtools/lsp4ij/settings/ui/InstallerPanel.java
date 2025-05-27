/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import com.redhat.devtools.lsp4ij.settings.jsonSchema.ServerInstallerJsonSchemaFileProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * UI panel which display in a tabbed pane the server installer:
 *
 * <ul>
 *     <li>on the left the JSON editor which hosts the JSON installer description.</li>
 *     <li>on the right a console which shows the result of the check/run installation.</li>
 * </ul>
 */
public class InstallerPanel implements Disposable {

    private final @NotNull Project project;
    private final @NotNull JsonTextField installerConfigurationWidget;
    private final @NotNull ConsoleView console;
    private final boolean flushOnEachPrint;
    private @Nullable CommandLineUpdater commandLineUpdater;
    private @Nullable Set<Runnable> onPreInstall;
    private @Nullable Set<Runnable> onPostInstall;

    public InstallerPanel(@NotNull FormBuilder builder,
                          @NotNull CommandLineWidget commandLine,
                          boolean flushOnEachPrint,
                          @NotNull Project project) {
        this.flushOnEachPrint = flushOnEachPrint;
        this.project = project;
        // Display on the left Json editor to fill installer.json content
        JPanel linksPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        HyperlinkLabel checkInstallerAction = new HyperlinkLabel(LanguageServerBundle.message("language.server.installer.check"));
        linksPanel.add(checkInstallerAction);
        HyperlinkLabel runInstallerAction = new HyperlinkLabel(LanguageServerBundle.message("language.server.installer.run"));
        linksPanel.add(runInstallerAction);
        HyperlinkLabel checkAndRunInstallerAction = new HyperlinkLabel(LanguageServerBundle.message("language.server.installer.check.and.run"));
        linksPanel.add(checkAndRunInstallerAction);
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.configuration"), linksPanel);

        installerConfigurationWidget = new JsonTextField(project);
        installerConfigurationWidget.setJsonFilename(ServerInstallerJsonSchemaFileProvider.INSTALLER_JSON_FILE_NAME);

        // Display on the right, console which shows traces of execution of installer
        TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
        console = consoleBuilder.getConsole();

        OnePixelSplitter splitter = new OnePixelSplitter(false, 0.5f);
        splitter.setShowDividerControls(true);
        splitter.setHonorComponentsMinimumSize(true);
        splitter.setFirstComponent(installerConfigurationWidget.getComponent());
        splitter.setSecondComponent(console.getComponent());
        builder.addComponentFillVertically(splitter, 0);
        checkInstallerAction.addHyperlinkListener(e -> {
            processInstall(InstallerContext.InstallerAction.CHECK);
        });

        runInstallerAction.addHyperlinkListener(e -> {
            processInstall(InstallerContext.InstallerAction.RUN);
        });

        checkAndRunInstallerAction.addHyperlinkListener(e -> {
            processInstall(InstallerContext.InstallerAction.CHECK_AND_RUN);
        });
    }

    private void processInstall(@NotNull InstallerContext.InstallerAction action) {
        var context = createInstallerContext(action);
        try {
            ServerInstallerManager
                    .getInstance()
                    .install(installerConfigurationWidget.getText(), context);
        } catch (Exception s) {
            Notification notification = new Notification(ServerMessageHandler.LSP_WINDOW_SHOW_MESSAGE_GROUP_ID,
                    "Install error",
                    s.getMessage(),
                    NotificationType.ERROR);
            Notifications.Bus.notify(notification, project);
        }
    }

    private @NotNull InstallerContext createInstallerContext(InstallerContext.@NotNull InstallerAction action) {
        var context = new InstallerContext(project, action)
                .setConsole(console);
        context.setFlushOnEachPrint(flushOnEachPrint);
        context.setCommandLineUpdater(commandLineUpdater);
        if (action == InstallerContext.InstallerAction.RUN || action == InstallerContext.InstallerAction.CHECK_AND_RUN) {
            context.setOnPreInstall(onPreInstall);
            context.setOnPostInstall(onPostInstall);
        }
        return context;
    }

    public void setCommandLineUpdater(@Nullable CommandLineUpdater commandLineUpdater) {
        this.commandLineUpdater = commandLineUpdater;
    }

    public void addPreInstallAction(@NotNull Runnable action) {
        if (onPreInstall == null) {
            onPreInstall = new HashSet<>();
        }
        onPreInstall.add(action);
    }

    public void addPostInstallAction(@NotNull Runnable action) {
        if (onPostInstall == null) {
            onPostInstall = new HashSet<>();
        }
        onPostInstall.add(action);
    }

    public @NotNull JsonTextField getInstallerConfigurationWidget() {
        return installerConfigurationWidget;
    }

    @Override
    public void dispose() {
        console.dispose();
    }
}
