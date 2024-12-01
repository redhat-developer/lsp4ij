package com.redhat.devtools.lsp4ij.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class SettingsHelper {

    private SettingsHelper() {
    }

    public static JsonElement findSettings(String[] sections, JsonObject jsonObject) {
        JsonElement current = jsonObject;
        for (String section : sections) {
            if (current instanceof JsonObject object) {
                current = object.get(section);
                if (current == null) {
                    return null;
                }
            }
        }
        return current;
    }
}
