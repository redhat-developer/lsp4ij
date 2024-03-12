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
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import org.eclipse.lsp4j.ConfigurationParams;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * User defined language client implementation used to track configuration changes
 * declared in a settings and call 'workspaceService/didChangeConfiguration" for the proper started language servers.
 */
public class UserDefinedLanguageClient extends LanguageClientImpl {

    private final UserDefinedLanguageListener languageServerStartedListener;

    public UserDefinedLanguageClient(@NotNull UserDefinedLanguageServerDefinition serverDefinition, @NotNull Project project) {
        super(project);
        this.languageServerStartedListener = new UserDefinedLanguageListener(serverDefinition, project);
        LanguageServerLifecycleManager.getInstance(project).addLanguageServerLifecycleListener(languageServerStartedListener);
        LanguageServersRegistry.getInstance().addLanguageServerDefinitionListener(languageServerStartedListener);
    }

    @Override
    public void dispose() {
        super.dispose();
        LanguageServerLifecycleManager.getInstance(getProject()).removeLanguageServerLifecycleListener(languageServerStartedListener);
        LanguageServersRegistry.getInstance().removeLanguageServerDefinitionListener(languageServerStartedListener);
    }

    //Hack so that LS requesting workspace/configuration without checking client capability don't cause an error (looking at you rust-analyzer)
    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}