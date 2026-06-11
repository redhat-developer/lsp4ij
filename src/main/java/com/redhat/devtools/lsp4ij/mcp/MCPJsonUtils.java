/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.redhat.devtools.lsp4ij.JSONUtils;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;

import java.util.Collections;

/**
 * JSON utilities for MCP server.
 * Uses the same Gson configuration as LSP traces for consistent formatting.
 */
public class MCPJsonUtils {

    private static MessageJsonHandler toStringInstance;
    private static Gson parseGson;

    /**
     * Perform JSON serialization using the same configuration as LSP traces.
     * This ensures consistent formatting between LSP and MCP traces.
     *
     * Handles LSP4J types correctly (Either, Range, Position, etc.)
     */
    public static String toJsonString(Object object) {
        if (toStringInstance == null) {
            toStringInstance = new MessageJsonHandler(Collections.emptyMap(), gsonBuilder -> {
                JSONUtils.configureCompatibilityAdapters(gsonBuilder);
                gsonBuilder.setPrettyPrinting();
            });
        }
        return toStringInstance.getGson().toJson(object);
    }

    /**
     * Parse MCP arguments into LSP4J objects using the same Gson configuration.
     * This allows tools to receive LSP-standard parameters directly.
     *
     * @param arguments MCP request arguments map
     * @param clazz     LSP4J class to parse into (e.g., CodeActionParams.class)
     * @param <T>       LSP4J type
     * @return parsed LSP4J object
     */
    public static <T> T parseParams(Object arguments, Class<T> clazz) {
        if (parseGson == null) {
            GsonBuilder builder = new GsonBuilder();
            JSONUtils.configureCompatibilityAdapters(builder);
            parseGson = builder.create();
        }
        // Convert arguments Map to JSON string then parse to LSP4J object
        String json = parseGson.toJson(arguments);
        return parseGson.fromJson(json, clazz);
    }
}
