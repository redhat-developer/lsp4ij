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
package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.redhat.devtools.lsp4ij.LSPNotificationConstants;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.console.explorer.actions.EditServerAction;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * UI command line updater.
 */
public class UICommandLineUpdater implements CommandLineUpdater {

    private final @NotNull UserDefinedLanguageServerDefinition definition;
    private final @Nullable Project project;

    public UICommandLineUpdater(@NotNull UserDefinedLanguageServerDefinition definition,
                                @Nullable Project project) {
        this.definition = definition;
        this.project = project;

    }

    @Override
    public String getCommandLine() {
        return definition.getCommandLine();
    }

    @Override
    public void setCommandLine(String commandLine) {
        // The installer's command is written for the standard input and output, unless it takes the port
        var previousTransport = definition.getTransport();
        var transport = previousTransport.forInstalledCommand(commandLine);
        if (Objects.equals(commandLine, getCommandLine()) && transport.equals(previousTransport)) {
            return;
        }
        definition.setCommandLine(commandLine);
        definition.setTransport(transport);

        // Update command line settings
        String languageServerId = definition.getId();
        UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings(languageServerId);
        if (settings != null) {
            settings.setCommandLine(commandLine);
            settings.setTransport(transport);
            UserDefinedLanguageServerSettings.getInstance().setUserDefinedLanguageServerSettings(languageServerId, settings);
        }

        if (project != null) {
            // Notifications
            sendNotification(project);
        } else {
            for (var project : ProjectManager.getInstance().getOpenProjects()) {
                sendNotification(project);
            }
        }
        if (previousTransport.mode() != UserDefinedTransport.Mode.STDIO && transport.mode() == UserDefinedTransport.Mode.STDIO) {
            warnOfTheSwitchToStandardInputAndOutput();
        }
    }

    /**
     * Tells the user that the installer's command doesn't take the port, so that the language server now runs over its
     * standard input and output instead of the socket it had.
     */
    private void warnOfTheSwitchToStandardInputAndOutput() {
        var notification = new Notification(LSPNotificationConstants.LSP4IJ_GENERAL_NOTIFICATIONS_ID,
                definition.getDisplayName(),
                LanguageServerBundle.message("language.server.installer.transport.stdio"),
                NotificationType.WARNING);
        notification.addAction(new EditServerAction(definition));
        Notifications.Bus.notify(notification, project);
    }

    private void sendNotification(@NotNull Project project) {
        LanguageServerDefinitionListener.LanguageServerChangedEvent event = new LanguageServerDefinitionListener.LanguageServerChangedEvent(
                LanguageServerDefinitionListener.LanguageServerDefinitionEvent.UpdatedBy.INSTALLER,
                project,
                definition,
                /* nameChanged */ false,
                /* commandChanged */ true,
                /* workingDirChanged */ false,
                /* userEnvironmentVariablesChanged */ false,
                /* includeSystemEnvironmentVariablesChanged */ false,
                /* mappingsChanged */ false,
                /* clientConfigurationContentChanged */ false,
                /* installerConfigurationContentChanged */ false,
                /* workspaceFolderStrategyConfigurationChanged*/ false);
        LanguageServersRegistry.getInstance().handleChangeEvent(event);
    }
}

