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
public class ServerInstallerJsonSchemaFileProvider extends AbstractLSPJsonSchemaFileSystemProvider {

    private static final String INSTALLER_SETTINGS_SCHEMA_JSON_PATH = "/jsonSchema/installerSettings.schema.json";
    public static final String INSTALLER_JSON_FILE_NAME = "installer.json";

    ServerInstallerJsonSchemaFileProvider() {
        super(INSTALLER_SETTINGS_SCHEMA_JSON_PATH, INSTALLER_JSON_FILE_NAME);
    }
}
