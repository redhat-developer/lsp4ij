/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.features.workspaceFolder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurable workspace folder strategy that can be customized via JSON.
 *
 * <p>This strategy supports lazy loading and marker-based discovery.
 * Configuration examples:
 * <pre>
 * // Project base directories, send all at initialization (default)
 * {}
 *
 * // Project base directories with lazy loading
 * { "lazy": true }
 *
 * // Source roots with lazy loading
 * { "rootType": "SOURCE_ROOTS", "lazy": true }
 *
 * // Marker-based discovery - find folders by walking up to marker files
 * { "markers": [".git", "pyproject.toml", "pom.xml"] }
 *
 * // No workspace folders
 * { "rootType": "NONE" }
 *
 * // One language server instance per folder that has a marker, with that folder as its workspace folder
 * { "serverInstances": { "markers": ["pyproject.toml"] } }
 *
 * // The same instances, and their source roots as their workspace folders
 * { "serverInstances": { "markers": ["pyproject.toml"] }, "rootType": "SOURCE_ROOTS" }
 * </pre>
 * </p>
 */
public class ConfigurableWorkspaceFolderStrategy extends BaseWorkspaceFolderStrategy {

    private static final String PROP_ROOT_TYPE = "rootType";
    private static final String PROP_LAZY = "lazy";
    private static final String PROP_MARKERS = "markers";
    private static final String PROP_SERVER_INSTANCES = "serverInstances";

    private final Gson gson = new Gson();

    /**
     * Decides which language server instance serves a file, and null if one instance serves all of them.
     */
    private @Nullable ConfigurableWorkspaceFolderStrategy serverInstanceRule;

    /**
     * True if the configuration describes the workspace folders inside an instance. Otherwise, the workspace folder
     * of a file is the root of its instance.
     */
    private boolean ownWorkspaceFolders;

    public void configure(@Nullable String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            // Reset to defaults
            setRootType(RootType.PROJECT_BASE);
            setLazy(false);
            setMarkers(null);
            serverInstanceRule = null;
            ownWorkspaceFolders = false;
            return;
        }

        try {
            JsonObject config = gson.fromJson(jsonContent, JsonObject.class);
            if (config != null) {
                serverInstanceRule = createServerInstanceRule(config);
                ownWorkspaceFolders = config.has(PROP_ROOT_TYPE) || config.has(PROP_MARKERS);

                // If markers are specified, force markers mode
                if (config.has(PROP_MARKERS)) {
                    List<String> markersList = new ArrayList<>();
                    config.getAsJsonArray(PROP_MARKERS).forEach(element -> markersList.add(element.getAsString()));
                    setMarkers(markersList);
                    setRootType(RootType.MARKERS);

                    // Configure lazy if specified, default to true for markers
                    if (config.has(PROP_LAZY)) {
                        setLazy(config.get(PROP_LAZY).getAsBoolean());
                    } else {
                        setLazy(true); // Default to lazy for markers
                    }
                } else {
                    // Not in markers mode, reset markers
                    setMarkers(null);

                    // Configure rootType if specified, otherwise reset to default
                    if (config.has(PROP_ROOT_TYPE)) {
                        String rootTypeStr = config.get(PROP_ROOT_TYPE).getAsString();
                        try {
                            setRootType(RootType.valueOf(rootTypeStr));
                        } catch (IllegalArgumentException e) {
                            // Invalid rootType, reset to default
                            setRootType(RootType.PROJECT_BASE);
                        }
                    } else {
                        // No rootType specified, reset to default
                        setRootType(RootType.PROJECT_BASE);
                    }

                    // Configure lazy if specified, otherwise reset to default
                    if (config.has(PROP_LAZY)) {
                        setLazy(config.get(PROP_LAZY).getAsBoolean());
                    } else {
                        // No lazy specified, reset to default
                        setLazy(false);
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            // Keep current configuration on error
        }
    }

    private static @Nullable ConfigurableWorkspaceFolderStrategy createServerInstanceRule(@NotNull JsonObject config) {
        if (!(config.get(PROP_SERVER_INSTANCES) instanceof JsonObject ruleConfig)) {
            return null;
        }
        var rule = new ConfigurableWorkspaceFolderStrategy() {
            @Override
            protected boolean isDirectoryItsOwnFolder() {
                return true;
            }
        };
        try {
            rule.configure(ruleConfig.toString());
            return rule;
        } catch (RuntimeException e) {
            // A rule with values of the wrong type counts as no rule
            return null;
        }
    }

    @Override
    public boolean hasServerInstances() {
        return serverInstanceRule != null;
    }

    @Override
    @Nullable
    public WorkspaceFolder getServerInstanceRoot(@NotNull VirtualFile file,
                                                 @NotNull Project project,
                                                 @NotNull FileUriSupport fileUriSupport) {
        return serverInstanceRule != null ?
                serverInstanceRule.getWorkspaceFolderForFile(file, project, fileUriSupport) :
                super.getServerInstanceRoot(file, project, fileUriSupport);
    }

    @Override
    @Nullable
    public WorkspaceFolder getWorkspaceFolderForFile(@NotNull VirtualFile file,
                                                     @NotNull Project project,
                                                     @NotNull FileUriSupport fileUriSupport) {
        return serverInstanceRule != null && !ownWorkspaceFolders ?
                serverInstanceRule.getWorkspaceFolderForFile(file, project, fileUriSupport) :
                super.getWorkspaceFolderForFile(file, project, fileUriSupport);
    }

    @Override
    @NotNull
    public List<VirtualFile> getRoots(@NotNull Project project) {
        return serverInstanceRule != null && !ownWorkspaceFolders ?
                serverInstanceRule.getRoots(project) :
                super.getRoots(project);
    }

    @NotNull
    public String getJsonConfiguration() {
        JsonObject config = new JsonObject();

        if (rootType == RootType.MARKERS && markers != null && !markers.isEmpty()) {
            // Markers mode
            config.add(PROP_MARKERS, gson.toJsonTree(markers));
        } else {
            // Standard mode
            if (rootType != RootType.PROJECT_BASE || (serverInstanceRule != null && ownWorkspaceFolders)) {
                config.addProperty(PROP_ROOT_TYPE, rootType.name());
            }
            if (lazy) {
                config.addProperty(PROP_LAZY, true);
            }
        }

        if (serverInstanceRule != null) {
            config.add(PROP_SERVER_INSTANCES, gson.fromJson(serverInstanceRule.getJsonConfiguration(), JsonObject.class));
        }

        return config.size() == 0 ? "{}" : gson.toJson(config);
    }
}
