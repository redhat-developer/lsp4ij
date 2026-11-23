/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.google.gson.JsonObject;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskFactoryBase;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating {@link ReplaceTemplateCommandTask} instances from JSON configuration.
 *
 * <pre>
 * "replaceTemplateCommand": {
 *   "name": "Configure clangd server command",
 *   "templateCommand": "clangd",
 *   "command": "\"${check.file}\""
 * }
 * </pre>
 */
public class ReplaceTemplateCommandTaskFactory extends InstallerTaskFactoryBase {

    private static final String TEMPLATE_COMMAND_JSON_PROPERTY = "templateCommand";
    private static final String COMMAND_JSON_PROPERTY = "command";

    @Override
    protected @NotNull InstallerTask create(@Nullable String id,
                                            @Nullable String name,
                                            @Nullable InstallerTask onFail,
                                            @Nullable InstallerTask onSuccess,
                                            @NotNull JsonObject json,
                                            @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        String command = getStringFromOs(json, COMMAND_JSON_PROPERTY);
        return new ReplaceTemplateCommandTask(id, name, onFail, onSuccess,
                getStringFromOs(json, TEMPLATE_COMMAND_JSON_PROPERTY), command != null ? command : "",
                serverInstallerDescriptor);
    }
}
