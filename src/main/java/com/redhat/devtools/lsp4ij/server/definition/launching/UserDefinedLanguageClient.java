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

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * User defined language client implementation used to track configuration changes
 * declared in a settings and call 'workspaceService/didChangeConfiguration" for the proper started language servers.
 */
public class UserDefinedLanguageClient extends LanguageClientImpl {
    private final UserDefinedLanguageServerDefinition serverDefinition;

    private final UserDefinedLanguageListener languageServerStartedListener;

    public UserDefinedLanguageClient(@NotNull UserDefinedLanguageServerDefinition serverDefinition, @NotNull Project project) {
        super(project);
        this.serverDefinition = serverDefinition;
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

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams params) {
        return CompletableFuture.supplyAsync(() -> {
            // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_configuration
            List<Object> settings = new ArrayList<>();
            for (ConfigurationItem item : params.getItems()) {
                String section = item.getSection();

                Object result = findSettings(section.split("[.]"));
                // The result is the configuration setting  or null, according to the spec:
                //  - If a scope URI is provided the client should return the setting scoped to the provided resource.
                //  - If the client can’t provide a configuration setting for a given scope then null needs to be present in the returned array.
                settings.add(result);
            }
            return settings;
        });
    }


    private Object findSettings(String[] sections) {
        var config = serverDefinition.getLanguageServerConfiguration();
        if (config instanceof JsonObject json) {
            return findSettings(sections, json);
        }
        return null;
    }

    private static Object findSettings(String[] sections, JsonObject jsonObject) {
        JsonObject current = jsonObject;
        for (String section : sections) {
            Object result = current.get(section);
            if (!(result instanceof JsonObject json)) {
                return null;
            }
            current = json;
        }
        return current;
    }
}