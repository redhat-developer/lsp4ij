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
import org.jetbrains.annotations.ApiStatus;
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
        return getStringFromOs(json, name, LanguageServerTemplate.OS_KEY, CpuArch.CURRENT, System.getProperty("os.arch"));
    }

    /**
     * Returns the value of the given property for the given OS and CPU, as {@link #getStringFromOs(JsonObject, String)}
     * does for the OS and the CPU of this IDE: the tests of the bundled installers resolve them for each platform.
     *
     * @param json    the JSON object which has the property.
     * @param name    the name of the property.
     * @param os      the key of the OS, as {@code windows}, {@code mac} or {@code unix}.
     * @param cpuArch the CPU.
     * @param osArch  the {@code os.arch} system property of the JVM, as {@code amd64} or {@code aarch64}.
     * @return the value of the given property for the given OS and CPU, or null.
     */
    @ApiStatus.Internal
    public static @Nullable String getStringFromOs(@NotNull JsonObject json,
                                                   @NotNull String name,
                                                   @Nullable String os,
                                                   @NotNull CpuArch cpuArch,
                                                   @Nullable String osArch) {
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
            if (jsonObj.has(os)) {
                // ex: "windows": "",
                jsonElement = jsonObj.get(os);
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
            String arch = cpuArch.name().toLowerCase();
            if (platformObj.has(arch)) {
                // ex: "windows": {
                //        "x86_64":
                jsonElement = platformObj.get(arch);
            } else {
                arch = osArch;
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
        return serverInstallerDeclaration.getStepActionRegistry().loadTask(onFail, serverInstallerDeclaration);
    }

    private @Nullable InstallerTask loadOnSuccess(@NotNull JsonObject json,
                                                  @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        JsonObject onFail = JSONUtils.getJsonObject(json, ON_SUCCESS_JSON_PROPERTY);
        if (onFail == null) {
            return null;
        }
        return serverInstallerDeclaration.getStepActionRegistry().loadTask(onFail, serverInstallerDeclaration);
    }

    protected abstract @NotNull InstallerTask create(@Nullable String id,
                                                     @Nullable String name,
                                                     @Nullable InstallerTask onFail,
                                                     @Nullable InstallerTask onSuccess,
                                                     @NotNull JsonObject json,
                                                     @NotNull ServerInstallerDescriptor serverInstallerDeclaration);

}
