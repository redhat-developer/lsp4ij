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
package com.redhat.devtools.lsp4ij.mcp.toolProvider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Base class for MCP tools that simplifies input schema definition.
 *
 * <p>Instead of creating nested Maps, subclasses can define their schema as a JSON string
 * via {@link #getToolInputSchema()}. This base class automatically:</p>
 * <ul>
 *   <li>Parses the JSON string to a Map</li>
 *   <li>Adds common project resolution parameters (cwd, projectPath, projectName)</li>
 * </ul>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * public class MyTool extends MCPToolBase {
 *     @Override
 *     protected String getToolInputSchema() {
 *         return """
 *             {
 *               "type": "object",
 *               "properties": {
 *                 "serverId": {
 *                   "type": "string",
 *                   "description": "The language server ID"
 *                 }
 *               },
 *               "required": ["serverId"]
 *             }
 *             """;
 *     }
 *
 *     @Override
 *     public CallToolResult execute(...) {
 *         // Tool implementation
 *     }
 * }
 * }</pre>
 */
public abstract class MCPToolBase implements MCPTool {

    private static final Gson GSON = new Gson();

    /**
     * Define the tool-specific input schema as a JSON string.
     *
     * <p>The schema should be a valid JSON Schema object (Draft 2020-12).
     * Common project resolution parameters (cwd, projectPath, projectName) are
     * automatically added to the properties.</p>
     *
     * @return JSON Schema as a string
     */
    @NotNull
    protected abstract String getToolInputSchema();

    /**
     * Creates an error result.
     *
     * @param message The error message
     * @return A CallToolResult with isError=true
     */
    protected io.modelcontextprotocol.spec.McpSchema.CallToolResult errorResult(String message) {
        return io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder()
                .isError(true)
                .content(java.util.List.of(new io.modelcontextprotocol.spec.McpSchema.TextContent(message)))
                .build();
    }

    @Override
    @NotNull
    public final Map<String, Object> getInputSchema() {
        // Parse the tool-specific schema
        String toolSchemaJson = getToolInputSchema();
        JsonObject schema = JsonParser.parseString(toolSchemaJson).getAsJsonObject();

        // Add common project resolution parameters
        if (!schema.has("properties")) {
            schema.add("properties", new JsonObject());
        }

        JsonObject properties = schema.getAsJsonObject("properties");

        // Add cwd parameter if not already present
        if (!properties.has("cwd")) {
            JsonObject cwdParam = new JsonObject();
            cwdParam.addProperty("type", "string");
            cwdParam.addProperty("description",
                "Optional: Current working directory. The server will find the IntelliJ project containing this path.");
            properties.add("cwd", cwdParam);
        }

        // Add projectPath parameter if not already present
        if (!properties.has("projectPath")) {
            JsonObject projectPathParam = new JsonObject();
            projectPathParam.addProperty("type", "string");
            projectPathParam.addProperty("description",
                "Optional: The absolute path to the IntelliJ project root.");
            properties.add("projectPath", projectPathParam);
        }

        // Add projectName parameter if not already present
        if (!properties.has("projectName")) {
            JsonObject projectNameParam = new JsonObject();
            projectNameParam.addProperty("type", "string");
            projectNameParam.addProperty("description",
                "Optional: The name of the IntelliJ project.");
            properties.add("projectName", projectNameParam);
        }

        // Add description if not present
        if (!schema.has("description")) {
            schema.addProperty("description",
                "If no project parameters (cwd, projectPath, projectName) are provided, uses the first open project.");
        }

        // Convert to Map
        return GSON.fromJson(schema, Map.class);
    }
}
