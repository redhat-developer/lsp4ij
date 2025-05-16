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
package com.redhat.devtools.lsp4ij;

import com.google.gson.*;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Utilities for working with JSON that has been converted to an Object using Gson.
 */
public class JSONUtils {

    private static final Gson LSP4J_GSON = new MessageJsonHandler(new HashMap<>()).getGson();
    private static final Gson EITHER_GSON = new GsonBuilder() //
            .registerTypeAdapterFactory(new EitherTypeAdapter.Factory()).create();
    private static final Map<ClassLoader, Either3<
            Boolean /* No Gson in the classpath of the plugin */,
            Method /* JsonElement parseString(String json) */ ,
            Method/* JsonElement parse(String json) */>> jsonParseMethodCache = new WeakHashMap<>();

    private JSONUtils() {
    }

    /**
     * Converts the given Object to the given class using lsp4j's GSON logic.
     *
     * @param <T>    the class to convert the Object to
     * @param object the object to convert
     * @param clazz  the class to convert the Object to
     * @return the given Object converted to the given class using lsp4j's GSON
     * logic
     */
    public static <T> T toModel(Object object, Class<T> clazz) {
        return toModel(getLsp4jGson(), object, clazz);
    }

    /**
     * Converts the given Object to the given class using the given GSON instance.
     *
     * @param <T>    the class to convert the Object to
     * @param gson   the gson instance to use to perform the conversion
     * @param object the object to convert
     * @param clazz  the class to convert the Object to
     * @return the given Object converted to the given class using the given GSON
     * instance
     */
    public static <T> T toModel(Gson gson, Object object, Class<T> clazz) {
        if (object == null) {
            return null;
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Class can not be null");
        }
        if (object instanceof JsonElement) {
            return gson.fromJson((JsonElement) object, clazz);
        }
        if (clazz.isInstance(object)) {
            return clazz.cast(object);
        }
        // if nothing else works, try serializing and deserializing again
        return gson.fromJson(gson.toJson(object), clazz);
    }

    /**
     * Returns a Gson instance configured similarly to the instance lsp4j uses.
     *
     * @return a Gson instance configured similarly to the instance lsp4j uses
     */
    public static Gson getLsp4jGson() {
        return LSP4J_GSON;
    }

    /**
     * Returns a Gson instance with most of the default options, but with the
     * ability to parse {@code org.eclipse.lsp4j.Either}.
     *
     * @return a Gson instance with most of the default options, but with the
     * ability to parse {@code org.eclipse.lsp4j.Either}
     */
    public static Gson getEitherGson() {
        return EITHER_GSON;
    }

    /**
     * Returns the JsonElement instance with the given class loader and the input elt otherwise.
     *
     * @param elt               the JsonElement with the lsp4ij plugin class loader.
     * @param actionClassLoader the IJ action class loader.
     * @return the JsonElement instance with the given class loader and the input elt otherwise.
     */
    public static Object getJsonElementFromClassloader(JsonElement elt, ClassLoader actionClassLoader) {
        if (elt.getClass().getClassLoader() == actionClassLoader || !(actionClassLoader instanceof PluginAwareClassLoader)) {
            // - the JsonElement class has the same class loader as the IJ Action class loader
            // - or the action class loader is not a PluginClassLoader
            // --> do nothing
            return elt;
        }
        try {

            var result = jsonParseMethodCache.get(actionClassLoader);
            if (result == null) {
                result = getJsonParseMethod(actionClassLoader);
            }
            if (result.isFirst()) {
                /* No Gson in the classpath of the plugin */
                return elt;
            }
            if (result.isSecond()) {
                // public static JsonElement parseString(String json) throws JsonSyntaxException {
                Method parseString = result.getSecond();
                return parseString.invoke(parseString.getDeclaringClass(), elt.toString());
            }
            if (result.isThird()) {
                // Old version of Gson
                // public JsonElement parse(String json) throws JsonSyntaxException {
                Method parse = result.getThird();
                return parse.invoke(parse.getDeclaringClass().getDeclaredConstructor().newInstance(), elt.toString());
            }
        } catch (Exception e) {

        }
        return elt;
    }

    private static synchronized Either3<Boolean, Method, Method> getJsonParseMethod(ClassLoader actionClassLoader) {
        var result = jsonParseMethodCache.get(actionClassLoader);
        if (result != null) {
            return result;
        }
        result = loadJsonParseMethod(actionClassLoader);
        jsonParseMethodCache.put(actionClassLoader, result);
        return result;
    }

    private static Either3<Boolean, Method, Method> loadJsonParseMethod(ClassLoader actionClassLoader) {
        try {
            Class<?> jsonParserClass = actionClassLoader.loadClass(JsonParser.class.getName());
            try {
                // Try to get static method JsonParser#parseString from the new version of Gson
                // public static JsonElement parseString(String json) throws JsonSyntaxException {
                Method parseString = jsonParserClass.getDeclaredMethod("parseString", String.class);
                return Either3.forSecond(parseString);
            } catch (Exception e) {
                // Old version of Gson
                try {
                    // public JsonElement parse(String json) throws JsonSyntaxException {
                    Method parse = jsonParserClass.getDeclaredMethod("parse", String.class);
                    return Either3.forThird(parse);
                } catch (Exception e1) {

                }
            }
        } catch (Exception e) {

        }
        return Either3.forFirst(Boolean.FALSE);
    }

    /**
     * Returns the Json element by given paths and null otherwise.
     *
     * @param root  the Json root object.
     * @param paths the paths.
     * @return the Json element by given paths and null otherwise.
     */
    @Nullable
    public static JsonElement findByPath(JsonObject root, String[] paths) {
        if (paths.length == 0) {
            return root;
        }
        JsonObject current = root;
        for (int i = 0; i < paths.length - 1; i++) {
            Object result = current.get(paths[i]);
            if (!(result instanceof JsonObject json)) {
                return null;
            }
            current = json;
        }
        if (current != null) {
            return current.get(paths[paths.length - 1]);
        }
        return null;
    }

    /**
     * Retrieves a JSON object with the specified name from the given parent JSON object.
     *
     * @param json The source {@link JsonObject} to search in.
     * @param name The name of the field to extract.
     * @return The {@link JsonObject} if the field exists and is a JSON object, or {@code null} otherwise.
     */
    public static @Nullable JsonObject getJsonObject(@NotNull JsonObject json,
                                                     @NotNull String name) {
        if (!json.has(name)) {
            return null;
        }
        var element = json.get(name);
        if (!element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    /**
     * Retrieves a JSON array with the specified name from the given parent JSON object.
     *
     * @param json The source {@link JsonObject} to search in.
     * @param name The name of the field to extract.
     * @return The {@link JsonArray} if the field exists and is a JSON array, or {@code null} otherwise.
     */
    public static @Nullable JsonArray getJsonArray(@NotNull JsonObject json,
                                                   @NotNull String name) {
        if (!json.has(name)) {
            return null;
        }
        var element = json.get(name);
        if (!element.isJsonArray()) {
            return null;
        }
        return element.getAsJsonArray();
    }

    /**
     * Retrieves a string value with the specified name from the given parent JSON object.
     *
     * @param json The source {@link JsonObject} to search in.
     * @param name The name of the field to extract.
     * @return The string value if the field exists and is a JSON string, or {@code null} otherwise.
     */
    public static @Nullable String getString(@NotNull JsonObject json,
                                             @NotNull String name) {
        if (!json.has(name)) {
            return null;
        }
        var element = json.get(name);
        if (!element.isJsonPrimitive()) {
            return null;
        }
        var primitive = element.getAsJsonPrimitive();
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        return null;
    }

    /**
     * Retrieves a boolean value with the specified name from the given parent JSON object.
     * <p>
     * Note: If the field is not present, not a primitive, or not a string representing a boolean,
     * this method returns {@code false}.
     *
     * @param json The source {@link JsonObject} to search in.
     * @param name The name of the field to extract.
     * @return The boolean value if the field exists and is a valid boolean string, otherwise {@code false}.
     */
    public static boolean getBoolean(@NotNull JsonObject json,
                                     @NotNull String name) {
        if (!json.has(name)) {
            return false;
        }
        var element = json.get(name);
        if (!element.isJsonPrimitive()) {
            return false;
        }
        var primitive = element.getAsJsonPrimitive();
        if (primitive.isString()) {
            return primitive.getAsBoolean();
        }
        return false;
    }

}