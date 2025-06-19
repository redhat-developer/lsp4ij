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
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import org.jetbrains.annotations.NotNull;

/**
 * User defined language client implementation used to track configuration changes
 * declared in a settings and call 'workspaceService/didChangeConfiguration" for the proper started language servers.
 */
public class UserDefinedLanguageClient extends LanguageClientImpl {
    private final UserDefinedLanguageServerDefinition serverDefinition;


    public UserDefinedLanguageClient(@NotNull UserDefinedLanguageServerDefinition serverDefinition,
                                     @NotNull Project project) {
        super(project);
        this.serverDefinition = serverDefinition;
    }

    @Override
    public void handleServerStatusChanged(@NotNull ServerStatus serverStatus) {
        if (serverStatus== ServerStatus.started) {
            // Case 1: Language server is started:
            // Try to get the user defined configuration and
            // push it with 'workspaceService/didChangeConfiguration' to the language server.
            triggerChangeConfiguration();
        }
    }

    @Override
    public @NotNull UserDefinedLanguageServerDefinition getServerDefinition() {
        return serverDefinition;
    }

}