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
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import org.jetbrains.annotations.NotNull;

/**
 * Listener used to push the user defined configuration to language server with 'workspaceService/didChangeConfiguration' by tracking :
 *
 * <ul>
 *     <li>when a configuration changes</li>
 * </ul>
 */
public class UserDefinedLanguageListener implements LanguageServerDefinitionListener {


    private final @NotNull UserDefinedLanguageClient client;

    private final @NotNull Project project;

    public UserDefinedLanguageListener(UserDefinedLanguageClient client, @NotNull Project project) {
        this.client = client;
        this.project = project;
    }

    @Override
    public void handleChanged(@NotNull LanguageServerChangedEvent event) {
        if (event.serverDefinition == client.getServerDefinition()) {
            if (event.initializationOptionsContentChanged || event.experimentalContentChanged)  {
                // initializationOption has changed:
                // Try to get the user defined initializationOption and
                // restart all started language servers
                // which matches the server definition.
                LanguageServiceAccessor.getInstance(project)
                        .getStartedServers()
                        .forEach(ls -> {
                            if (ls.getServerDefinition() == client.getServerDefinition()) {
                                ls.restart();
                            }
                        });
            } else if (event.configurationChanged) {
                // Case 2: configuration has changed:
                // Try to get the user defined configuration and
                // push it with 'workspaceService/didChangeConfiguration' to the all started language servers
                // which matches the server definition.
                if (event.serverDefinition == client.getServerDefinition()) {
                    client.triggerChangeConfiguration();
                }
            }
        }
    }

    @Override
    public void handleAdded(@NotNull LanguageServerAddedEvent event) {
        // Do nothing
    }

    @Override
    public void handleRemoved(@NotNull LanguageServerRemovedEvent event) {
        // Do nothing
    }

}
