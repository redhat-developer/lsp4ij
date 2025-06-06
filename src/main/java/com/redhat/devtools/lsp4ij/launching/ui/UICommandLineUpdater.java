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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
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
        if (Objects.equals(commandLine, getCommandLine())) {
            return;
        }
        definition.setCommandLine(commandLine);

        // Update command line settings
        String languageServerId = definition.getId();
        UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings = UserDefinedLanguageServerSettings.getInstance().getLaunchConfigSettings(languageServerId);
        if (settings != null) {
            settings.setCommandLine(commandLine);
            UserDefinedLanguageServerSettings.getInstance().setLaunchConfigSettings(languageServerId, settings);
        }

        if (project != null) {
            // Notifications
            sendNotification(project);
        } else {
            for (var project : ProjectManager.getInstance().getOpenProjects()) {
                sendNotification(project);
            }
        }
    }

    private void sendNotification(@NotNull Project project) {
        LanguageServerDefinitionListener.LanguageServerChangedEvent event = new LanguageServerDefinitionListener.LanguageServerChangedEvent(
                project,
                definition,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
        LanguageServersRegistry.getInstance().handleChangeEvent(event);
    }
}

