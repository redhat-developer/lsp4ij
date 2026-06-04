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
package com.redhat.devtools.lsp4ij.mcp.tools.lsp;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBase;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool to execute a command on a language server.
 *
 * This tool allows AI assistants to invoke LSP workspace/executeCommand on any
 * started language server. The command is executed using the existing
 * LanguageServerWrapper and LanguageClientImpl, preserving all custom client
 * logic (e.g., Qute LS data model handling).
 *
 * Input parameters:
 * - serverId: The language server ID (e.g., "rust-analyzer", "typescript-language-server")
 * - command: The command name (e.g., "rust-analyzer.expandMacro")
 * - arguments: Optional list of command arguments (LSP Any type)
 */
public class ExecuteCommandTool extends MCPToolBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteCommandTool.class);
    private static final int COMMAND_TIMEOUT_SECONDS = 30;

    @Override
    @NotNull
    protected String getToolInputSchema() {
        // language=JSON
        return """
            {
              "type": "object",
              "properties": {
                "serverId": {
                  "type": "string",
                  "description": "The language server ID (e.g., 'rust-analyzer', 'typescript-language-server')"
                },
                "command": {
                  "type": "string",
                  "description": "The command name to execute (e.g., 'rust-analyzer.expandMacro')"
                },
                "arguments": {
                  "type": "array",
                  "description": "Optional command arguments (LSP Any type)",
                  "items": {
                    "type": "object"
                  }
                }
              },
              "required": ["serverId", "command"],
              "description": "Execute a command on a language server using LSP workspace/executeCommand."
            }
            """;
    }

    @Override
    public @NotNull McpSchema.CallToolResult execute(
            @NotNull Project project,
            @NotNull McpSyncServerExchange exchange,
            @NotNull McpSchema.CallToolRequest request) {
        try {
            Map<String, Object> result = executeInternal(project, request.arguments());
            String resultJson = tools.jackson.databind.json.JsonMapper.builder()
                    .build()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(resultJson)))
                    .build();
        } catch (IllegalArgumentException e) {
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .content(List.of(new McpSchema.TextContent("Invalid parameters: " + e.getMessage())))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error executing command", e);
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                    .build();
        }
    }

    /**
     * Execute a command on a language server.
     *
     * @param params Map containing:
     *               - serverId (String): The language server ID
     *               - command (String): The command name
     *               - arguments (List, optional): Command arguments
     * @return Result of the command execution
     * @throws IllegalArgumentException if serverId or command is missing
     * @throws IllegalStateException if server is not found or not started
     */
    @NotNull
    private Map<String, Object> executeInternal(Project project, @NotNull Map<String, Object> params) {
        // Validate input
        String serverId = (String) params.get("serverId");
        if (serverId == null || serverId.isEmpty()) {
            throw new IllegalArgumentException("serverId is required");
        }

        String command = (String) params.get("command");
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }

        @SuppressWarnings("unchecked")
        List<Object> arguments = (List<Object>) params.get("arguments");

        LOGGER.info("Executing command '{}' on server '{}'", command, serverId);

        // Find the language server wrapper
        LanguageServerWrapper wrapper = findServerWrapper(project, serverId);

        // Verify server is started
        if (wrapper.getServerStatus() != ServerStatus.started) {
            throw new IllegalStateException(
                    "Language server '" + serverId + "' is not started (status: " +
                            wrapper.getServerStatus() + ")"
            );
        }

        // Execute the command using the existing wrapper
        // CRITICAL: We use the wrapper's LanguageServer, which is connected to
        // the existing LanguageClientImpl with all its custom logic
        try {
            ExecuteCommandParams executeParams = new ExecuteCommandParams(command, arguments);

            Object result = wrapper.getLanguageServer()
                    .getWorkspaceService()
                    .executeCommand(executeParams)
                    .get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("result", result);
            return response;

        } catch (Exception e) {
            LOGGER.error("Failed to execute command '{}' on server '{}'", command, serverId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * Find a started language server wrapper by ID.
     *
     * @param project The project context
     * @param serverId The server ID to find
     * @return The language server wrapper
     * @throws IllegalStateException if server is not found
     */
    @NotNull
    private LanguageServerWrapper findServerWrapper(@NotNull Project project, @NotNull String serverId) {
        var accessor = LanguageServiceAccessor.getInstance(project);

        return accessor.getStartedServers().stream()
                .filter(wrapper -> wrapper.getServerDefinition().getId().equals(serverId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Language server '" + serverId + "' not found or not started"
                ));
    }
}
