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
package com.redhat.devtools.lsp4ij.settings.jsonSchema;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of JsonSchema.ProviderFactory for all bundled language server configuration JSON schema definitions.
 */
public class LSPSettingsJsonSchemaProviderFactory implements JsonSchemaProviderFactory {
    private static final LSP4IJJsonSchemaFileProvider[] JSON_SCHEMA_FILE_PROVIDERS = new LSP4IJJsonSchemaFileProvider[]{
            new CssLanguageServerConfigurationJsonSchemaFileProvider(),
            new TypeScriptLanguageServerConfigurationJsonSchemaFileProvider()
    };

    @NotNull
    @Override
    public List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
        List<JsonSchemaFileProvider> providers = new LinkedList<>();
        ContainerUtil.addAllNotNull(providers, JSON_SCHEMA_FILE_PROVIDERS);
        return providers;
    }

    /**
     * Returns the JSON filename for the specified language server command-line. If one is found, it can be used to
     * set the filename of the {@link com.redhat.devtools.lsp4ij.settings.ui.JsonTextField} so that it uses the
     * corresponding JSON schema.
     *
     * @param commandLine the language server command-line
     * @return the JSON filename for the language server, or null if no matching JSON schema file provider is found
     */
    @Nullable
    public static String getJsonFilename(@NotNull String commandLine) {
        for (LSP4IJJsonSchemaFileProvider jsonSchemaFileProvider : JSON_SCHEMA_FILE_PROVIDERS) {
            if (jsonSchemaFileProvider.supports(commandLine)) {
                return jsonSchemaFileProvider.getJsonFilename();
            }
        }
        return null;
    }
}
