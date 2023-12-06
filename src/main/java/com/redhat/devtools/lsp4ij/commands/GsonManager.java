/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Fraunhofer FOKUS
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.ide.plugins.cl.PluginClassLoader;

import java.lang.reflect.Method;

/**
 * Gson manager.
 */
public class GsonManager {

    private GsonManager() {

    }

    /**
     * Returns the JsonElement instance with the given class loader and null otherwise.
     *
     * @param elt               the JsonElement with the lsp4ij plugin class loader.
     * @param actionClassLoader the IJ action class loader.
     * @return the JsonElement instance with the given class loader and null otherwise.
     */
    public static Object getJsonElementFromClassloader(JsonElement elt, ClassLoader actionClassLoader) {
        if (elt.getClass().getClassLoader() == actionClassLoader || !(actionClassLoader instanceof PluginClassLoader)) {
            // - the JsonElement class has the same class loader as the IJ Action class loader
            // - or the action class loader is not a PluginClassLoader
            // --> do nothing
            return null;
        }
        try {
            Class<?> jsonParserClass = ((PluginClassLoader) actionClassLoader).tryLoadingClass(JsonParser.class.getName(), true);
            if (jsonParserClass != null) {
                try {
                    // Try to get static method JsonParser#parseString from the new version of Gson
                    // public static JsonElement parseString(String json) throws JsonSyntaxException {
                    Method parseString = jsonParserClass.getDeclaredMethod("parseString", String.class);
                    return parseString.invoke(jsonParserClass, elt.toString());
                } catch (Exception e) {
                    // Old version of Gson
                    try {
                        // public JsonElement parse(String json) throws JsonSyntaxException {
                        Method parse = jsonParserClass.getDeclaredMethod("parse", String.class);
                        return parse.invoke(jsonParserClass.getDeclaredConstructor().newInstance(), elt.toString());
                    } catch (Exception e1) {

                    }
                }
            }
        } catch (Exception e) {

        }
        return null;
    }
}
