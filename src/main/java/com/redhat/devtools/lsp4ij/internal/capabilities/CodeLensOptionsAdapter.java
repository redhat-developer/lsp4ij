/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.capabilities;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.eclipse.lsp4j.CodeLensOptions;

import java.io.IOException;

/**
 * CodeLens adapter to support old language server which declares "codeLensProvider"
 * with a boolean:
 *
 * <p>
 *     "codeLensProvider": true
 * </p>
 *
 * instead of declaring :
 *
 * <p>
 *  *     "codeLensProvider": {}
 *  * </p>
 */
public class CodeLensOptionsAdapter extends TypeAdapter<CodeLensOptions> {

    @Override
    public void write(JsonWriter out, CodeLensOptions value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            new Gson().toJson(value, CodeLensOptions.class, out);
        }
    }

    @Override
    public CodeLensOptions read(JsonReader in) throws IOException {
        JsonElement element = JsonParser.parseReader(in);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            // "codeLensProvider": true
            boolean enabled = element.getAsBoolean();
            if (!enabled) {
                return null;
            } else {
                // Activate codeLens
                return new CodeLensOptions();
            }
        }

        // "codeLensProvider": {}
        return new Gson().fromJson(element, CodeLensOptions.class);
    }
}
