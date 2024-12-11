/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.settings.jsonSchema;

/**
 * JSON schema file provider for language server client-side configuration.
 */
public class LSPClientConfigurationJsonSchemaFileProvider extends AbstractLSPJsonSchemaFileSystemProvider {
    private static final String CLIENT_SETTINGS_SCHEMA_JSON_PATH = "/jsonSchema/clientSettings.schema.json";
    public static final String CLIENT_SETTINGS_JSON_FILE_NAME = "clientSettings.json";

    LSPClientConfigurationJsonSchemaFileProvider() {
        super(CLIENT_SETTINGS_SCHEMA_JSON_PATH, CLIENT_SETTINGS_JSON_FILE_NAME);
    }
}
