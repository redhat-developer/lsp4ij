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
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Helpers for extracting nested settings from json
 */
public class SettingsHelper {

    private SettingsHelper() {
    }

    /**
     * Extract nested settings from json.
     *
     * @param section path to the json element to retrieve
     * @param parent  Json to look for settings in
     * @return the settings retrieved in the specified section and null if the section was not found.
     */
    public static @Nullable JsonElement findSettings(@Nullable String section, @Nullable JsonObject parent) {
        if (section == null || parent == null) {
            return null;
        }

        if (parent.has(section)) {
            return parent.get(section);
        }

        final var sections = section.split("[.]");
        JsonElement current = parent;
        for (var split : sections) {
            if (current instanceof JsonObject currentObject) {
                current = currentObject.get(split);
            }
        }

        if (current != null) {
            return current;
        }

        for (var key : Set.copyOf(parent.keySet())) {
            var keySplit = key.split("[.]");
            if (sections.length > keySplit.length) {
                parent.remove(key);
                continue;
            }
            for (int i = 0; i < sections.length; i++) {
                if (!sections[i].equals(keySplit[i])) {
                    parent.remove(key);
                    break;
                }
            }
        }
        return parent.isEmpty() ? null : parent;
    }
}
