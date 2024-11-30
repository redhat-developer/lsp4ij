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

import org.jetbrains.annotations.NotNull;

/**
 * JSON schema file provider for the TypeScript language server.
 */
class TypeScriptLanguageServerConfigurationJsonSchemaFileProvider extends LSP4IJJsonSchemaFileProvider {
    private static final String TYPESCRIPT_LANGUAGE_SERVER_SETTINGS_SCHEMA_JSON_PATH = "/templates/typescript-language-server/settings.schema.json";
    private static final String TYPESCRIPT_LANGUAGE_SERVER_SETTINGS_JSON_FILENAME = "typescript-language-server-settings.json";

    private static final String TYPESCRIPT_LANGUAGE_SERVER_COMMAND = "typescript-language-server";

    TypeScriptLanguageServerConfigurationJsonSchemaFileProvider() {
        super(TYPESCRIPT_LANGUAGE_SERVER_SETTINGS_SCHEMA_JSON_PATH, TYPESCRIPT_LANGUAGE_SERVER_SETTINGS_JSON_FILENAME);
    }

    @Override
    public boolean supports(@NotNull String commandLine) {
        return commandLine.contains(TYPESCRIPT_LANGUAGE_SERVER_COMMAND);
    }
}
