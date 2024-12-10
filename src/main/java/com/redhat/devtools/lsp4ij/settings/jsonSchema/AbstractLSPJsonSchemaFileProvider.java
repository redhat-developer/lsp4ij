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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

/**
 * Abstract base class for JSON schema file providers that are based on JSON schema files bundled in the plugin distribution.
 */
abstract class AbstractLSPJsonSchemaFileProvider implements JsonSchemaFileProvider {
    private final String jsonSchemaPath;
    private final String jsonFilename;
    private VirtualFile jsonSchemaFile = null;

    protected AbstractLSPJsonSchemaFileProvider(@NotNull String jsonSchemaPath, @NotNull String jsonFilename) {
        this.jsonSchemaPath = jsonSchemaPath;
        this.jsonFilename = jsonFilename;
    }

    @Nullable
    @Override
    public final VirtualFile getSchemaFile() {
        if (jsonSchemaFile == null) {
            URL jsonSchemaUrl = getClass().getResource(jsonSchemaPath);
            String jsonSchemaFileUrl = jsonSchemaUrl != null ? VfsUtil.convertFromUrl(jsonSchemaUrl) : null;
            jsonSchemaFile = jsonSchemaFileUrl != null ? VirtualFileManager.getInstance().findFileByUrl(jsonSchemaFileUrl) : null;
            // Make sure that the IDE is using the absolute latest version of the JSON schema
            if (jsonSchemaFile != null) {
                jsonSchemaFile.refresh(true, false);
            }
        }
        return jsonSchemaFile;
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
        return StringUtil.equalsIgnoreCase(jsonFilename, file.getName());
    }

    @NotNull
    @Override
    public final String getName() {
        return jsonFilename;
    }

    @NotNull
    @Override
    public final SchemaType getSchemaType() {
        return SchemaType.schema;
    }

    @Override
    public final JsonSchemaVersion getSchemaVersion() {
        return JsonSchemaVersion.SCHEMA_7;
    }

    @NotNull
    @Override
    public final String getPresentableName() {
        return getName();
    }
}
