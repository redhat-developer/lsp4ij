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
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.jetbrains.annotations.NotNull;

/**
 * Listener used to push the user defined configuration to language server with 'workspaceService/didChangeConfiguration' by tracking :
 *
 * <ul>
 *     <li>when a language server is started</li>
 *     <li>when a configuration changes</li>
 * </ul>
 */
public class UserDefinedLanguageListener implements LanguageServerLifecycleListener, LanguageServerDefinitionListener {


    private final @NotNull UserDefinedLanguageServerDefinition serverDefinition;
    private final @NotNull Project project;

    public UserDefinedLanguageListener(@NotNull UserDefinedLanguageServerDefinition serverDefinition, @NotNull Project project) {
        this.serverDefinition = serverDefinition;
        this.project = project;
    }

    @Override
    public void handleStatusChanged(LanguageServerWrapper languageServer) {
        if (languageServer.getServerStatus() == ServerStatus.started && languageServer.serverDefinition == serverDefinition) {
            // Case 1: Language server is started:
            // Try to get the user defined configuration and
            // push it with 'workspaceService/didChangeConfiguration' to the language server.
            didChangeConfiguration(languageServer);
        }
    }


    @Override
    public void handleChanged(@NotNull LanguageServerChangedEvent event) {
        if (event.serverDefinition == serverDefinition) {
            if (event.initializationOptionsContentChanged) {
                // initializationOption has changed:
                // Try to get the user defined initializationOption and
                // restart all started language servers
                // which matches the server definition.
                LanguageServiceAccessor.getInstance(project)
                        .getStartedServers()
                        .forEach(ls -> {
                            if (ls.serverDefinition == serverDefinition) {
                                ls.restart();
                            }
                        });
            } else if (event.configurationChanged) {
                // Case 2: configuration has changed:
                // Try to get the user defined configuration and
                // push it with 'workspaceService/didChangeConfiguration' to the all started language servers
                // which matches the server definition.
                LanguageServiceAccessor.getInstance(project)
                        .getStartedServers()
                        .forEach(ls -> {
                            if (ls.serverDefinition == serverDefinition) {
                                didChangeConfiguration(ls);
                            }
                        });
            }
        }
    }

    private void didChangeConfiguration(LanguageServerWrapper languageServer) {
        Object params = serverDefinition.getLanguageServerConfiguration();
        if (params != null) {
            languageServer.getInitializedServer()
                    .thenAccept(ls -> {
                        ls.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(params));
                    });
        }
    }

    @Override
    public void handleLSPMessage(Message message, MessageConsumer consumer, LanguageServerWrapper languageServer) {
        // Do nothing
    }

    @Override
    public void handleError(LanguageServerWrapper languageServer, Throwable exception) {
        // Do nothing
    }

    @Override
    public void dispose() {
        // Do nothing
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
