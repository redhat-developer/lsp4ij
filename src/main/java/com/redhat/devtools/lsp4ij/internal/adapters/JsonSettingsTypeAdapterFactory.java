/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.adapters;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.adapters.InitializeParamsTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.json.adapters.JsonElementTypeAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Keeps the null members of settings given as JSON, like those of a user-defined language server, where LSP4J's
 * Gson omits nulls: in the initialization options of the initialize request, and in the settings of a
 * {@link ConfigurationResult}, the reply to workspace/configuration. Settings given as a map or an object, and the
 * other fields of these messages, are written as before.
 */
public class JsonSettingsTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (InitializeParams.class.isAssignableFrom(type.getRawType())) {
            var jsonAdapter = gson.getAdapter(JsonElement.class);
            return (TypeAdapter<T>) new InitializeParamsTypeAdapter(gson) {
                @Override
                protected void writeInitializationOptions(JsonWriter out, Object value) throws IOException {
                    if (value instanceof JsonElement options) {
                        writeWithNulls(out, options, jsonAdapter);
                    } else {
                        super.writeInitializationOptions(out, value);
                    }
                }
            };
        }
        if (type.getRawType() == ConfigurationResult.class) {
            var jsonAdapter = gson.getAdapter(JsonElement.class);
            // LSP4J's adapter for the items of a list of objects, for the settings not given as JSON
            var settingAdapter = new JsonElementTypeAdapter(gson);
            var delegate = gson.getDelegateAdapter(this, type);
            return new TypeAdapter<>() {
                @Override
                public T read(JsonReader in) throws IOException {
                    return delegate.read(in);
                }

                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    out.beginArray();
                    for (Object setting : (ConfigurationResult) value) {
                        if (setting instanceof JsonElement json) {
                            writeWithNulls(out, json, jsonAdapter);
                        } else {
                            settingAdapter.write(out, setting);
                        }
                    }
                    out.endArray();
                }
            };
        }
        return null;
    }

    private static void writeWithNulls(@NotNull JsonWriter out,
                                       @NotNull JsonElement json,
                                       @NotNull TypeAdapter<JsonElement> jsonAdapter) throws IOException {
        // Not with Gson.toJson, which would set the writer back to omitting nulls
        boolean previous = out.getSerializeNulls();
        out.setSerializeNulls(true);
        try {
            jsonAdapter.write(out, json);
        } finally {
            out.setSerializeNulls(previous);
        }
    }
}
