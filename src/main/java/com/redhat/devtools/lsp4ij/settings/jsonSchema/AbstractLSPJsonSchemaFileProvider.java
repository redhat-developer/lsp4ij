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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.util.ModalityUiUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class for JSON schema file providers that are based on JSON schema files bundled in the plugin distribution.
 */
abstract class AbstractLSPJsonSchemaFileProvider implements JsonSchemaFileProvider {

    private final String jsonFilename;

    protected AbstractLSPJsonSchemaFileProvider(@NotNull String jsonFilename) {
        this.jsonFilename = jsonFilename;
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

    @Override
    public boolean isUserVisible() {
        return false;
    }

    protected static void reloadPsi(@NotNull VirtualFile file,
                                  @NotNull Project project) {
        final FileManagerImpl fileManager = (FileManagerImpl) PsiManagerEx.getInstanceEx(project).getFileManager();
        if (fileManager.findCachedViewProvider(file) != null) {
            ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), project.getDisposed(),
                    () -> WriteAction.run(() -> fileManager.forceReload(file))
            );
        }
    }
}
