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

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

/**
 * Abstract base class for JSON schema file providers that are based on JSON schema files bundled in the plugin distribution.
 */
abstract class AbstractLSPJsonSchemaFileSystemProvider extends AbstractLSPJsonSchemaFileProvider {
    private final String jsonSchemaPath;
    private VirtualFile jsonSchemaFile = null;

    protected AbstractLSPJsonSchemaFileSystemProvider(@NotNull String jsonSchemaPath, @NotNull String jsonFilename) {
        super(jsonFilename);
        this.jsonSchemaPath = jsonSchemaPath;
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

}
