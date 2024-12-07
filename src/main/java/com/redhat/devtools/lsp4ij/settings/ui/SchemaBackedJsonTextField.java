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
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.settings.jsonSchema.LSPServerConfigurationJsonSchemaManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A JSON text field which can be associated with a dynamic JSON Schema.
 */
public class SchemaBackedJsonTextField extends JsonTextField {

    private @Nullable Integer index;

    public SchemaBackedJsonTextField(@NotNull Project project) {
        super(project);
    }

    /**
     * Associate a Json Schema to the editor.
     *
     * @param jsonSchemaContent the Json schema content to use.
     */
    public void associateWithJsonSchema(@NotNull String jsonSchemaContent) {
        // Get index from the pool of JsonSchemaFileProvider which is unused
        var manager = LSPServerConfigurationJsonSchemaManager.getInstance(getProject());
        if (index == null) {
            index = manager.getUnusedIndex();
        }
        if (index != null) {
            // A JsonSchemaFileProvider is free
            // 1. Update the content of the JsonSchemaFileProvider
            String jsonFileName = manager.setJsonSchemaContent(index, jsonSchemaContent);
            // 2. Associate the JsonSchemaFileProvider to the editor by using the proper file name.
            setJsonFilename(jsonFileName);
        }
    }

    /**
     * Remove the JSON Schema association.
     */
    public void resetJsonSchema() {
        setJsonFilename("lsp.server.settings.no.schema.json");
        if (index != null) {
            var manager = LSPServerConfigurationJsonSchemaManager.getInstance(getProject());
            manager.reset(index);
        }
    }

}
