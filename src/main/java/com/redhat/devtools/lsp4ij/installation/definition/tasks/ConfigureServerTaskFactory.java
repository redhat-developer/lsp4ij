/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.google.gson.JsonObject;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskFactoryBase;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <pre>
 " "configureServer": {
 *   "name": "Configure jdt-ls server command",
 *   "command": "\"${output.dir}/${output.file.name}\" -configuration \"USER_HOME$/.cache/jdtls\" -data \"$PROJECT_DIR$/jdtls-data\"",
 *   "update": true
 * }
 * </pre>
 *
 */
public class ConfigureServerTaskFactory extends InstallerTaskFactoryBase {

    private static final String COMMAND_JSON_PROPERTY = "command";

    @Override
    protected @NotNull InstallerTask create(@Nullable String id,
                                            @Nullable String name,
                                            @Nullable InstallerTask onFail,
                                            @Nullable InstallerTask onSuccess,
                                            @NotNull JsonObject json,
                                            @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        return new ConfigureServerTask(id, name, onFail, onSuccess, getCommand(json), serverInstallerDescriptor);
    }

    private static @NotNull String getCommand(@NotNull JsonObject json) {
        String command = getStringFromOs(json, COMMAND_JSON_PROPERTY);
        return command != null ? command : "";
    }

}
