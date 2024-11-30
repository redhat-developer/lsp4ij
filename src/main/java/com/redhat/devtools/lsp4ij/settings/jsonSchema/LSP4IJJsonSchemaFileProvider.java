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
 * Abstract base class for bundled LSP JSON schema file providers.
 */
abstract class LSP4IJJsonSchemaFileProvider implements JsonSchemaFileProvider {
    private final String jsonSchemaPath;
    private final String jsonFilename;
    private VirtualFile jsonSchemaFile = null;

    /**
     * Creates the JSON schema file provider with the specified JSON schema path and JSON file name.
     *
     * @param jsonSchemaPath the classpath-relative path of the JSON schema file
     * @param jsonFilename   the name of the JSON file for which the JSON schema file provider should be enabled
     */
    protected LSP4IJJsonSchemaFileProvider(@NotNull String jsonSchemaPath, @NotNull String jsonFilename) {
        this.jsonSchemaPath = jsonSchemaPath;
        this.jsonFilename = jsonFilename;
    }

    /**
     * Returns the name of the JSON file for which this JSON schema file provider should be enabled.
     *
     * @return the JSON file name
     */
    @NotNull
    public String getJsonFilename() {
        return jsonFilename;
    }

    /**
     * Determines whether this JSON schema file provider supports the language server with the specified command-line.
     *
     * @param commandLine the language server command-line
     * @return true if this file provider supports the language server with the specified command-line; otherwise false
     */
    public abstract boolean supports(@NotNull String commandLine);

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
