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
import com.intellij.util.system.CpuArch;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for installer task factory.
 */
public abstract class InstallerTaskFactoryBase implements InstallerTaskFactory {

    private static final String ID_JSON_PROPERTY = "id";
    private static final String NAME_JSON_PROPERTY = "name";
    private static final String ON_FAIL_JSON_PROPERTY = "onFail";
    private static final String ON_SUCCESS_JSON_PROPERTY = "onSuccess";

    protected static @Nullable String getStringFromOs(@NotNull JsonObject json,
                                                      @NotNull String name) {
        if (!json.has(name)) {
            return null;
        }
        JsonElement jsonElement = json.get(name);
        if (jsonElement.isJsonObject()) {
            // ex:
            // "url": {
            //   "windows": "",
            //   "default": ""
            // }

            JsonObject jsonObj = jsonElement.getAsJsonObject();
            if (jsonObj.has(LanguageServerTemplate.OS_KEY)) {
                // ex: "windows": "",
                jsonElement = jsonObj.get(LanguageServerTemplate.OS_KEY);
            } else if (jsonObj.has(LanguageServerTemplate.DEFAULT_KEY)) {
                // ex: "default": ""
                jsonElement = jsonObj.get(LanguageServerTemplate.DEFAULT_KEY);
            }
        }

        if (jsonElement.isJsonObject()) {
            // ex:
            // "url": {
            //   "windows": {
            //     "x86_64": "https://github.com/rust-lang/rust-analyzer/releases/download/2025-05-12/rust-analyzer-x86_64-pc-windows-msvc.zip",
            //     "x86": "https://github.com/rust-lang/rust-analyzer/releases/download/2025-05-12/rust-analyzer-i686-pc-windows-msvc.zip",
            //     "arm64": "https://github.com/rust-lang/rust-analyzer/releases/download/2025-05-12/rust-analyzer-aarch64-pc-windows-msvc.zip"
            //   },
            //   "linux": {
            // ...
            JsonObject platformObj = jsonElement.getAsJsonObject();
            String arch = CpuArch.CURRENT.name().toLowerCase();
            if (platformObj.has(arch)) {
                // ex: "windows": {
                //        "x86_64":
                jsonElement = platformObj.get(arch);
            } else {
                arch = System.getProperty("os.arch");
                if (platformObj.has(arch)) {
                    // ex: "windows": {
                    //        "x86_64":
                    jsonElement = platformObj.get(arch);
                } else if (platformObj.has(LanguageServerTemplate.DEFAULT_KEY)) {
                    // ex: "windows": {
                    //        "default:
                    jsonElement = platformObj.get(LanguageServerTemplate.DEFAULT_KEY);
                } else {
                    return null;
                }
            }
        }

        if (jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString()) {
            // ex:
            // "url": ""
            return jsonElement.getAsJsonPrimitive().getAsString();
        }
        return null;
    }

    @Override
    public final @NotNull InstallerTask create(@NotNull JsonObject json,
                                               @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        @Nullable String id = JSONUtils.getString(json, ID_JSON_PROPERTY);
        @Nullable String name = JSONUtils.getString(json, NAME_JSON_PROPERTY);
        @Nullable InstallerTask onFail = loadOnFail(json, serverInstallerDeclaration);
        @Nullable InstallerTask onSuccess = loadOnSuccess(json, serverInstallerDeclaration);
        return create(id, name, onFail, onSuccess, json, serverInstallerDeclaration);
    }

    private @Nullable InstallerTask loadOnFail(@NotNull JsonObject json,
                                               @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        JsonObject onFail = JSONUtils.getJsonObject(json, ON_FAIL_JSON_PROPERTY);
        if (onFail == null) {
            return null;
        }
        return serverInstallerDeclaration.getStepActionRegistry().loadStep(onFail, serverInstallerDeclaration);
    }

    private @Nullable InstallerTask loadOnSuccess(@NotNull JsonObject json,
                                                  @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        JsonObject onFail = JSONUtils.getJsonObject(json, ON_SUCCESS_JSON_PROPERTY);
        if (onFail == null) {
            return null;
        }
        return serverInstallerDeclaration.getStepActionRegistry().loadStep(onFail, serverInstallerDeclaration);
    }

    protected abstract @NotNull InstallerTask create(@Nullable String id,
                                                     @Nullable String name,
                                                     @Nullable InstallerTask onFail,
                                                     @Nullable InstallerTask onSuccess,
                                                     @NotNull JsonObject json,
                                                     @NotNull ServerInstallerDescriptor serverInstallerDeclaration);

}
