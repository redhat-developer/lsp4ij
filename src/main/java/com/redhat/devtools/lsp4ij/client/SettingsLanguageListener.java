/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettingsListener;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Listener used to push the user defined configuration to language server with 'workspaceService/didChangeConfiguration' by tracking :
 *
 * <ul>
 *     <li>when a configuration changes</li>
 * </ul>
 */
public class SettingsLanguageListener implements LanguageServerSettingsListener {

    private final @NotNull LanguageClientImpl client;

    private final @NotNull Project project;

    public SettingsLanguageListener(@NotNull LanguageClientImpl client,
                                    @NotNull Project project) {
        this.client = client;
        this.project = project;
    }

    @Override
    public void handleChanged(@NotNull LanguageServerSettingsChangedEvent event) {
        if (Objects.equals(event.languageServerId(), client.getServerDefinition().getId())) {
            if (event.initializationOptionsContentChanged() || event.experimentalContentChanged()) {
                // initializationOption has changed:
                // Try to get the user defined initializationOption and
                // restart all started language servers
                // which matches the server definition.
                restartLanguageServer();
            } else if (event.configurationContentChanged()) {
                // Case 2: configuration has changed:
                // Try to get the user defined configuration and
                // push it with 'workspaceService/didChangeConfiguration' to the all started language servers
                // which matches the server definition.
                var configurationFeature = client.getClientFeatures().getConfigurationFeature();
                switch (configurationFeature.getOnConfigurationChanged()) {
                    case RESTART_LANGUAGE_SERVER -> restartLanguageServer();
                    case CALL_DID_CHANGE_CONFIGURATION -> client.triggerChangeConfiguration();
                    case IGNORE -> {
                        // Do nothing
                    }
                }
            }
        }
    }

    private void restartLanguageServer() {
        LanguageServiceAccessor.getInstance(project)
                .getStartedServers()
                .forEach(ls -> {
                    if (ls.getServerDefinition() == client.getServerDefinition()) {
                        ls.restart();
                    }
                });
    }

}
