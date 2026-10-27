/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.adapters;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.eclipse.lsp4j.DidChangeConfigurationParams;

import java.io.IOException;

/**
 * Preserves explicit null settings, which servers can use to reset configuration. Scope
 * null serialization to the settings notification so absent LSP fields remain omitted.
 */
public class ConfigurationTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != DidChangeConfigurationParams.class) {
            return null;
        }
        // LSP4J's settings adapter calls Gson.toJson for Maps/POJOs, which resets the
        // writer flags to its Gson settings. The private delegate must preserve nulls too.
        var settingsGson = gson.newBuilder().serializeNulls().create();
        var delegate = settingsGson.getDelegateAdapter(this, type);
        return new TypeAdapter<>() {
            @Override
            public T read(JsonReader input) throws IOException {
                return delegate.read(input);
            }

            @Override
            public void write(JsonWriter output, T value) throws IOException {
                boolean previous = output.getSerializeNulls();
                output.setSerializeNulls(true);
                try {
                    delegate.write(output, value);
                } finally {
                    output.setSerializeNulls(previous);
                }
            }
        };
    }
}
