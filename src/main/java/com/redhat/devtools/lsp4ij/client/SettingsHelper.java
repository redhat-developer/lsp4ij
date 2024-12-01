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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helpers for extracting nested settings from json
 */
public class SettingsHelper {

    private SettingsHelper() {
    }

    /**
     * Extract nested settings from json.
     * @param sections path to the json element to retrieve
     * @param parent Json to look for settings in
     * @return the settings retrieved in the specified section and null if the section was not found.
     */
    public static @Nullable JsonElement findSettings(@NotNull String[] sections, @Nullable JsonObject parent) {
        JsonElement current = parent;
        for (String section : sections) {
            if (current instanceof JsonObject currentObject) {
                current = currentObject.get(section);
                if (current == null) {
                    return null;
                }
            }
        }
        return current;
    }
}
