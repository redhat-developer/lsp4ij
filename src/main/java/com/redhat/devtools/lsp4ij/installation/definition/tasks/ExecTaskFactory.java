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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskFactoryBase;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <pre>
 " exec": {
 *     "name": "Check typescript-language-server",*
 *     "command": {
 *       "windows": "where typescript-language-server",
 *       "default": "which typescript-language-server"
 *     },
 *     "onFail": { ...
 *     }
 * }
 * </pre>
 *
 */
public class ExecTaskFactory extends InstallerTaskFactoryBase {

    private static final String COMMAND_JSON_PROPERTY = "command";
    private static final String TIMEOUT_JSON_PROPERTY = "timeout";

    @Override
    protected @NotNull InstallerTask create(@Nullable String id,
                                            @Nullable String name,
                                            @Nullable InstallerTask onFail,
                                            @Nullable InstallerTask onSuccess,
                                            @NotNull JsonObject json,
                                            @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        return new ExecTask(id, name, onFail, onSuccess, getCommand(json), getTimeout(json), serverInstallerDeclaration);
    }

    private static @NotNull List<String> getCommand(@NotNull JsonObject json) {
        if (!json.has(COMMAND_JSON_PROPERTY)) {
            return Collections.emptyList();
        }
        List<String> commands = new ArrayList<>();
        JsonElement commandElement = json.get(COMMAND_JSON_PROPERTY);
        if (commandElement.isJsonObject()) {
            // ex:
            // "command": {
            //   "windows": "where typescript-language-server",
            //   "default": "which typescript-language-server"
            // }

            // or
            // "command": {
            //   "default": ["npm", "install", "-g", "typescript-language-server"]
            // }

            JsonElement element = null;
            JsonObject commandObj = commandElement.getAsJsonObject();
            if (commandObj.has(LanguageServerTemplate.OS_KEY)) {
                // ex: "windows": "where typescript-language-server",
                element = commandObj.get(LanguageServerTemplate.OS_KEY);
            } else if (commandObj.has(LanguageServerTemplate.DEFAULT_KEY)) {
                // ex: "default": "which typescript-language-server"
                element = commandObj.get(LanguageServerTemplate.DEFAULT_KEY);
            }
            if (element != null) {
                fillCommand(element, commands);
            }
        } else {
            fillCommand(commandElement, commands);
        }
        return commands;
    }

    private static void fillCommand(@NotNull JsonElement command,
                             @NotNull List<String> commands) {
        if (command.isJsonArray()) {
            // ex "default": ["npm", "install", "-g", "typescript-language-server"]
            var array = command.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                var element = array.get(i);
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    commands.add(element.getAsString());
                }
            }
        } else if (command.isJsonPrimitive() && command.getAsJsonPrimitive().isString()) {
            // ex: "windows": "where typescript-language-server",
            var array = command.getAsString().split(" ");
            commands.addAll(Arrays.asList(array));
        }
    }


    private @Nullable Integer getTimeout(@NotNull JsonObject json) {
        return JSONUtils.getInteger(json, TIMEOUT_JSON_PROPERTY);
    }

}
