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
 * </pre>
 * </p>
 */
public class ConfigurableWorkspaceFolderStrategy extends BaseWorkspaceFolderStrategy {

    private static final String PROP_ROOT_TYPE = "rootType";
    private static final String PROP_LAZY = "lazy";
    private static final String PROP_MARKERS = "markers";

    private final Gson gson = new Gson();

    public void configure(@Nullable String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            // Reset to defaults
            setRootType(RootType.PROJECT_BASE);
            setLazy(false);
            setMarkers(null);
            return;
        }

        try {
            JsonObject config = gson.fromJson(jsonContent, JsonObject.class);
            if (config != null) {
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

    @NotNull
    public String getJsonConfiguration() {
        JsonObject config = new JsonObject();

        if (rootType == RootType.MARKERS && markers != null && !markers.isEmpty()) {
            // Markers mode
            config.add(PROP_MARKERS, gson.toJsonTree(markers));
        } else {
            // Standard mode
            if (rootType != RootType.PROJECT_BASE) {
                config.addProperty(PROP_ROOT_TYPE, rootType.name());
            }
            if (lazy) {
                config.addProperty(PROP_LAZY, true);
            }
        }

        return config.size() == 0 ? "{}" : gson.toJson(config);
    }
}
