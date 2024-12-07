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

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pooling of JsonSchemaProvider used by Server / Configuration editors.
 * We need this pooling because there are no way to register {@link com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider}
 * dynamically with {@link LSPJsonSchemaProviderFactory}.
 */
public class LSPServerConfigurationJsonSchemaManager {

    private static final int JSON_SCHEMA_FILE_PROVIDER_POOL_SIZE = 100;

    public static LSPServerConfigurationJsonSchemaManager getInstance(@NotNull Project project) {
        return project.getService(LSPServerConfigurationJsonSchemaManager.class);
    }

    private final List<LSPServerConfigurationJsonSchemaFileProvider> providers;

    public LSPServerConfigurationJsonSchemaManager(@NotNull Project project) {
        providers = new ArrayList<>();
        // Create 100 dummy LSPServerConfigurationJsonSchemaFileProvider
        for (int i = 0; i < JSON_SCHEMA_FILE_PROVIDER_POOL_SIZE; i++) {
            providers.add(new LSPServerConfigurationJsonSchemaFileProvider(i, project));
        }
    }

    public List<LSPServerConfigurationJsonSchemaFileProvider> getProviders() {
        return providers;
    }

    /**
     * Returns the first index of a {@link LSPServerConfigurationJsonSchemaFileProvider} which is not used by
     * a Server / Configuration editor and null otherwise.
     *
     * @return the first index of a {@link LSPServerConfigurationJsonSchemaFileProvider} which is not used by
     * * a Server / Configuration editor and null otherwise.
     */
    @Nullable
    public Integer getUnusedIndex() {
        var result = getProviders()
                .stream()
                .filter(LSPServerConfigurationJsonSchemaFileProvider::isUnused)
                .map(LSPServerConfigurationJsonSchemaFileProvider::getIndex)
                .findFirst();
        return result.isPresent() ? result.get() : null;
    }

    /**
     * Update the Json schema content of the {@link LSPServerConfigurationJsonSchemaFileProvider} stored in the given index.
     *
     * @param index             the index of {@link LSPServerConfigurationJsonSchemaFileProvider} instance to update.
     * @param jsonSchemaContent the new Json schema content.
     * @return the file name to use to associate with the  {@link LSPServerConfigurationJsonSchemaFileProvider} instance updated.
     */
    public String setJsonSchemaContent(@NotNull Integer index,
                                       @Nullable String jsonSchemaContent) {
        LSPServerConfigurationJsonSchemaFileProvider provider = getProviders().get(index);
        provider.setSchemaContent(jsonSchemaContent);
        return provider.getName();
    }

    /**
     * Free the {@link LSPServerConfigurationJsonSchemaFileProvider} stored at the given index from the pool
     * (when a Server / Configuration editor is disposed).
     *
     * @param index the index of {@link LSPServerConfigurationJsonSchemaFileProvider}.
     */
    public void reset(@NotNull Integer index) {
        LSPServerConfigurationJsonSchemaFileProvider provider = getProviders().get(index);
        provider.reset();
    }
}
