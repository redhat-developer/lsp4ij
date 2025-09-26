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
package com.redhat.devtools.lsp4ij.dap.client;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Custom Gson deserializer for Map<String, Object> that preserves integer values.
 *
 * <p>By default, when deserializing JSON into a Map using Gson,
 * all numeric values are treated as Double, even when they are whole numbers.
 * This deserializer recursively parses the JSON and:
 * <ul>
 *   <li>Converts whole numbers into Integer or Long (depending on size)</li>
 *   <li>Keeps floating point numbers as Double</li>
 *   <li>Preserves nested objects and arrays</li>
 * </ul>
 *
 * <p>This is useful when dealing with dynamic or unknown JSON structures
 * where number types should be preserved as accurately as possible.
 *
 * <p>Usage:
 * <pre>{@code
 * Gson gson = new GsonBuilder()
 *     .registerTypeAdapter(new TypeToken<Map<String, Object>>(){}.getType(),
 *                          new IntegerPreservingMapDeserializer())
 *     .create();
 *
 * Map<String, Object> map = gson.fromJson(jsonString, new TypeToken<Map<String, Object>>(){}.getType());
 * }</pre>
 */
public class IntegerPreservingMapDeserializer implements JsonDeserializer<Map<String, Object>> {

    @Override
    public Map<String, Object> deserialize(JsonElement json, Type typeOfT,
                                           JsonDeserializationContext context) throws JsonParseException {
        return readObject(json.getAsJsonObject());
    }

    private Map<String, Object> readObject(JsonObject jsonObject) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            map.put(entry.getKey(), readValue(entry.getValue()));
        }
        return map;
    }

    private Object readValue(JsonElement json) {
        if (json.isJsonNull()) {
            return null;
        } else if (json.isJsonPrimitive()) {
            JsonPrimitive prim = json.getAsJsonPrimitive();
            if (prim.isBoolean()) return prim.getAsBoolean();
            if (prim.isString()) return prim.getAsString();
            if (prim.isNumber()) {
                Number num = prim.getAsNumber();
                double doubleValue = num.doubleValue();
                long longValue = num.longValue();
                // If the number has no fractional part, treat as Integer or Long
                if (doubleValue == longValue) {
                    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                        return (int) longValue;
                    } else {
                        return longValue;
                    }
                }
                // Otherwise treat as Double
                return doubleValue;
            }
        } else if (json.isJsonArray()) {
            return readArray(json.getAsJsonArray());
        } else if (json.isJsonObject()) {
            return readObject(json.getAsJsonObject());
        }
        return null;
    }

    private List<Object> readArray(JsonArray array) {
        List<Object> list = new ArrayList<>();
        for (JsonElement element : array) {
            list.add(readValue(element));
        }
        return list;
    }
}
