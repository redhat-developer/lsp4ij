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

import com.intellij.openapi.project.Project;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Represents an MCP (Model Context Protocol) tool that can be exposed to AI assistants.
 *
 * <p>Implementations of this interface define custom tools that extend the capabilities
 * of the MCP server. Each tool has a unique name, schema, and execution logic.</p>
 *
 * <p><b>Example implementation:</b></p>
 * <pre>{@code
 * public class MyCustomTool implements MCPTool {
 *     @Override
 *     public String getName() {
 *         return "myPlugin-customAction";
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "Performs a custom action on the project";
 *     }
 *
 *     @Override
 *     public Map<String, Object> getInputSchema() {
 *         return Map.of(
 *             "type", "object",
 *             "properties", Map.of(
 *                 "filePath", Map.of("type", "string"),
 *                 "action", Map.of("type", "string")
 *             )
 *         );
 *     }
 *
 *     @Override
 *     public McpSchema.CallToolResult execute(
 *             @NotNull Project project,
 *             @NotNull McpSyncServerExchange exchange,
 *             @NotNull McpSchema.CallToolRequest request) {
 *         // Tool implementation
 *         return McpSchema.CallToolResult.builder()
 *             .content(List.of(new McpSchema.TextContent("Success")))
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @see MCPToolProvider
 */
public interface MCPTool {

    /**
     * Returns the JSON Schema defining the input parameters for this tool.
     *
     * <p>The schema follows JSON Schema Draft 2020-12 specification.</p>
     *
     * <p>Example:</p>
     * <pre>{@code
     * Map.of(
     *     "type", "object",
     *     "properties", Map.of(
     *         "serverId", Map.of(
     *             "type", "string",
     *             "description", "The language server ID"
     *         ),
     *         "command", Map.of(
     *             "type", "string",
     *             "description", "The command to execute"
     *         )
     *     ),
     *     "required", List.of("serverId", "command")
     * )
     * }</pre>
     *
     * @return the input schema as a Map
     */
    @NotNull
    Map<String, Object> getInputSchema();

    /**
     * Executes this tool with the given request parameters.
     *
     * <p>This method is called when an AI assistant invokes this tool.
     * The implementation should:</p>
     * <ul>
     *   <li>Extract parameters from {@code request.arguments()}</li>
     *   <li>Perform the requested action</li>
     *   <li>Return a result with {@link McpSchema.TextContent} or other content types</li>
     *   <li>Return an error result if the execution fails</li>
     * </ul>
     *
     * @param project  the IntelliJ project context
     * @param exchange the MCP server exchange (for advanced use cases)
     * @param request  the tool invocation request containing arguments
     * @return the tool execution result
     */
    @NotNull
    McpSchema.CallToolResult execute(
            @NotNull Project project,
            @NotNull McpSyncServerExchange exchange,
            @NotNull McpSchema.CallToolRequest request
    );
}
