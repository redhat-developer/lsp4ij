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

import com.google.gson.JsonObject;
import com.redhat.devtools.lsp4ij.JSONUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Installer task registry.
 */
public class InstallerTaskRegistry {

    private static final String INSTALLER_NAME_JSON_PROPERTY = "name";
    private static final String INSTALLER_CHECK_JSON_PROPERTY = "check";
    private static final String INSTALLER_RUN_JSON_PROPERTY = "run";
    private static final String TASK_REF_JSON_PROPERTY = "ref";

    private final @NotNull Map<String, InstallerTaskFactory> factories;

    public InstallerTaskRegistry() {
        factories = new HashMap<>();
    }

    public void registerFactory(String type, InstallerTaskFactory factory) {
        factories.put(type, factory);
    }

    public @NotNull ServerInstallerDescriptor loadInstaller(@NotNull JsonObject json) {
        String name = JSONUtils.getString(json, INSTALLER_NAME_JSON_PROPERTY);
        ServerInstallerDescriptor serverInstallerDeclaration = new ServerInstallerDescriptor(name != null ? name : "Untitled", this);
        loadStep(json, INSTALLER_CHECK_JSON_PROPERTY, serverInstallerDeclaration);
        loadStep(json, INSTALLER_RUN_JSON_PROPERTY, serverInstallerDeclaration);
        return serverInstallerDeclaration;
    }

    private void loadStep(@NotNull JsonObject json,
                          @NotNull String name,
                          @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        JsonObject stepObject = JSONUtils.getJsonObject(json, name);
        if (stepObject != null) {
            InstallerTask installerTask = null;
            String ref = JSONUtils.getString(stepObject, TASK_REF_JSON_PROPERTY);
            if (ref != null) {

            } else {
                installerTask = loadStep(stepObject, serverInstallerDeclaration);
            }
            if (installerTask != null) {
                if (INSTALLER_RUN_JSON_PROPERTY.equals(name)) {
                    serverInstallerDeclaration.setRun(installerTask);
                } else {
                    serverInstallerDeclaration.setCheck(installerTask);
                }
            }
        }
    }

    public InstallerTask loadStep(@Nullable JsonObject stepObject,
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
