/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Helpers for extracting nested settings from json.
 */
public class SettingsHelper {

    private SettingsHelper() {
    }

    /**
     * Extract nested settings from JSON.
     *
     * @param section path to the JSON element to retrieve (e.g., "a.b.c")
     * @param parent  JSON object to search
     * @return the JSON element found at the specified path, or null if not found
     */
    public static @Nullable JsonElement findSettings(@Nullable String section,
                                                     @Nullable JsonObject parent) {
        if (section == null || parent == null) {
            return null;
        }

        if (parent.has(section)) {
            return parent.get(section);
        }

        // Traverse nested structure using dot-separated keys
        final var sections = section.split("[.]");
        boolean found = false;
        JsonElement current = parent;
        for (var split : sections) {
            if (current instanceof JsonObject currentObject) {
                if (currentObject.has(split)) {
                    current = currentObject.get(split);
                    found = true;
                } else {
                    found = false;
                    break;
                }
            } else {
                found = false;
                break;
            }
        }

        if (found) {
            return current;
        }

        // Attempt fallback matching by removing keys that don't match the path
        JsonObject clonedParent = parent.deepCopy();
        for (var key : Set.copyOf(clonedParent.keySet())) {
            var keySplit = key.split("[.]");
            if (sections.length > keySplit.length) {
                clonedParent.remove(key);
                continue;
            }
            for (int i = 0; i < sections.length; i++) {
                if (!sections[i].equals(keySplit[i])) {
                    clonedParent.remove(key);
                    break;
                }
            }
        }
        return clonedParent.isEmpty() ? null : clonedParent;
    }

    /**
     * Parses the given JSON content and optionally expands keys with dot notation
     * into nested objects (e.g., {"a.b": 1} => {"a": {"b": 1}}).
     *
     * @param content JSON string
     * @param expand  true to expand dotted keys into nested objects
     * @return parsed JSON element
     */
    public static JsonElement parseJson(@NotNull String content, boolean expand) {
        JsonElement parsed = JsonParser.parseString(content);
        if (!expand || !parsed.isJsonObject() || !containsDottedKey(parsed.getAsJsonObject())) {
            return parsed;
        }
        Map<String, JsonElement> flat = flatten(parsed);
        return expand(flat);
    }

    private static boolean containsDottedKey(@NotNull JsonObject obj) {
        for (String key : obj.keySet()) {
            if (key.contains(".")) return true;
        }
        return false;
    }

    private static Map<String, JsonElement> flatten(@NotNull JsonElement element) {
        Map<String, JsonElement> map = new LinkedHashMap<>();
        flattenRecursive("", element, map);
        return map;
    }

    private static void flattenRecursive(@NotNull String prefix,
                                         @NotNull JsonElement element,
                                         @NotNull Map<String, JsonElement> map) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String newKey = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenRecursive(newKey, entry.getValue(), map);
            }
        } else {
            map.put(prefix, element);
        }
    }

    /**
     * Expands a map of dot-notated keys into a nested JSON structure.
     *
     * @param flatMap flat map of key => value, where keys may use dot notation
     * @return nested JsonObject
     */
    private static JsonObject expand(@NotNull Map<String, JsonElement> flatMap) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : flatMap.entrySet()) {
            insert(root, entry.getKey(), entry.getValue());
        }
        return root;
    }

    private static void insert(@NotNull JsonObject root,
                               @NotNull String dottedKey,
                               @Nullable JsonElement value) {
        JsonObject current = root;
        int start = 0;
        int end;
        while ((end = dottedKey.indexOf('.', start)) != -1) {
            String part = dottedKey.substring(start, end);
            JsonElement next = current.get(part);
            if (!(next instanceof JsonObject)) {
                JsonObject newObj = new JsonObject();
                current.add(part, newObj);
                next = newObj;
            }
            current = next.getAsJsonObject();
            start = end + 1;
        }
        String finalKey = dottedKey.substring(start);
        current.add(finalKey, value);
    }
}
