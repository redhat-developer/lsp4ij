/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.redhat.devtools.lsp4ij.JSONUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Installer task registry.
 */
public class InstallerTaskRegistry {

    private static final @NotNull String INSTALLER_NAME_JSON_PROPERTY = "name";
    private static final @NotNull String INSTALLER_EXECUTE_ON_START_SERVER_JSON_PROPERTY = "executeOnStartServer";
    private static final @NotNull String INSTALLER_CHECK_JSON_PROPERTY = "check";
    private static final @NotNull String INSTALLER_RUN_JSON_PROPERTY = "run";
    private static final @NotNull String TASK_REF_JSON_PROPERTY = "ref";


    private final @NotNull Map<String, InstallerTaskFactory> factories;

    public InstallerTaskRegistry() {
        factories = new HashMap<>();
    }

    public void registerFactory(String type, InstallerTaskFactory factory) {
        factories.put(type, factory);
    }

    public @NotNull ServerInstallerDescriptor loadInstaller(@NotNull String json) {
        JsonObject installer = loadJson(json);
        return loadInstaller(installer);
    }

    public @NotNull ServerInstallerDescriptor loadInstaller(@NotNull JsonObject json) {
        String name = JSONUtils.getString(json, INSTALLER_NAME_JSON_PROPERTY);
        boolean executeOnStartServer = JSONUtils.getBoolean(json, INSTALLER_EXECUTE_ON_START_SERVER_JSON_PROPERTY);
        ServerInstallerDescriptor serverInstallerDescriptor = new ServerInstallerDescriptor(name != null ? name : "Untitled", executeOnStartServer,this);
        loadTask(json, INSTALLER_CHECK_JSON_PROPERTY, serverInstallerDescriptor);
        loadTask(json, INSTALLER_RUN_JSON_PROPERTY, serverInstallerDescriptor);
        return serverInstallerDescriptor;
    }

    private JsonObject loadJson(@NotNull String installerConfigurationContent) {
        JsonElement installerConfiguration = JsonParser.parseReader(new StringReader(installerConfigurationContent));
        if (installerConfiguration.isJsonObject()) {
            return installerConfiguration.getAsJsonObject();
        } else {
            throw new RuntimeException("Invalid Json object");
        }
    }

    private void loadTask(@NotNull JsonObject json,
                          @NotNull String name,
                          @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        JsonObject taskObject = JSONUtils.getJsonObject(json, name);
        if (taskObject != null) {
            InstallerTask installerTask = null;
            String ref = JSONUtils.getString(taskObject, TASK_REF_JSON_PROPERTY);
            if (ref != null) {

            } else {
                installerTask = loadTask(taskObject, serverInstallerDescriptor);
            }
            if (installerTask != null) {
                if (INSTALLER_RUN_JSON_PROPERTY.equals(name)) {
                    serverInstallerDescriptor.setRun(installerTask);
                } else {
                    serverInstallerDescriptor.setCheck(installerTask);
                }
            }
        }
    }

    public InstallerTask loadTask(@Nullable JsonObject stepObject,
                                  @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        if (stepObject == null) {
            return null;
        }
        String type = getType(stepObject);
        if (type != null) {
            var factory = factories.get(type);
            if (factory != null) {
                return factory.create(stepObject.get(type).getAsJsonObject(), serverInstallerDeclaration);
            }
        }
        return null;
    }

    private @Nullable String getType(@NotNull JsonObject json) {
        var keys = json.keySet();
        if (keys.isEmpty()) {
            return null;
        }
        return keys.iterator().next();
    }
}
