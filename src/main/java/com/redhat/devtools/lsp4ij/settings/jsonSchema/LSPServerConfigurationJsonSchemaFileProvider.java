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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Json Schema provider used in the Server / Configuration editor.
 */
public class LSPServerConfigurationJsonSchemaFileProvider extends AbstractLSPJsonSchemaFileProvider {

    private final int index;
    private final @NotNull Project project;
    private final @NotNull LSPJsonSchemaLightVirtualFile file;
    private boolean unused;

    /**
     * LSP Server / Configuration {@link com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider} constructor.
     *
     * @param index   the index where the provider instance is stored in the pool of providers managed by {@link LSPServerConfigurationJsonSchemaManager}.
     * @param project the project.
     */
    public LSPServerConfigurationJsonSchemaFileProvider(int index, @NotNull Project project) {
        super(generateJsonFileName(index));
        this.index = index;
        this.project = project;
        this.file = new LSPJsonSchemaLightVirtualFile(generateJsonSchemaFileName(index), "");
        this.unused = true;
    }

    private static String generateJsonFileName(int index) {
        return "lsp.server." + index + ".json";
    }

    private static String generateJsonSchemaFileName(int index) {
        return "lsp.server." + index + ".schema.json";
    }

    /**
     * Returns the index of the provider stored in the provider pool.
     *
     * @return the index of the provider stored in the provider pool.
     */
    public int getIndex() {
        return index;
    }

    @Override
    public @Nullable VirtualFile getSchemaFile() {
        return file;
    }

    /**
     * Free the Json Schema provider which can be used by a Server / Configuration editor.
     */
    public void reset() {
        try {
            updateFileContent("", file, project);
        } finally {
            unused = true;
        }
    }

    /**
     * Update the file with the given json schema content.
     *
     * @param jsonSchemaContent the Json schema content.
     */
    public void setSchemaContent(@NotNull String jsonSchemaContent) {
        try {
            updateFileContent(jsonSchemaContent, file, project);
        } finally {
            unused = false;
        }
    }

    /**
     * Returns true if the Json Schema provider is unused (by a Server / Configuration editor) and false otherwise.
     *
     * @return true if the Json Schema provider is unused (by a Server / Configuration editor) and false otherwise.
     */
    public boolean isUnused() {
        return unused;
    }

    /**
     * Update file content.
     *
     * @param content the new content.
     * @param file    the file to update.
     * @param project the project.
     */
    private static void updateFileContent(@NotNull String content,
                                          @NotNull LSPJsonSchemaLightVirtualFile file,
                                          @NotNull Project project) {
        if (Objects.equals(content, file.getContent())) {
            // No changes, don't update the file.
            return;
        }
        // Update the virtual file content and the modification stamp (used by Json Schema cache)
        file.setContent(content);
        // Synchronize the Psi file from the new content of the virtual file and the modification stamp (used by Json Schema cache)
        reloadPsi(file, project);
    }

}
