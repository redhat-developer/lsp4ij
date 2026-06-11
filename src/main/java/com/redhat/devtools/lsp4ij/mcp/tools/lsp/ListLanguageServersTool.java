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
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP tool to list all started language servers in the project.
 *
 * Returns information about each server including:
 * - Server ID
 * - Server status (started, starting, stopped)
 * - Available capabilities (documentSymbol, codeAction, executeCommand, etc.)
 * - Supported commands (if executeCommand is available)
 */
public class ListLanguageServersTool extends MCPToolBase {

    @Override
    @NotNull
    protected String getToolInputSchema() {
        // language=JSON
        return """
            {
              "type": "object",
              "properties": {},
              "description": "List all started language servers in the project with their capabilities and available commands."
            }
            """;
    }

    @Override
    public @NotNull McpSchema.CallToolResult execute(
            @NotNull Project project,
            @NotNull McpSyncServerExchange exchange,
            @NotNull McpSchema.CallToolRequest request) {
        try {
            Map<String, Object> result = executeInternal(project);
            String resultJson = tools.jackson.databind.json.JsonMapper.builder()
                    .build()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result);

            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(resultJson)))
                    .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                    .build();
        }
    }

    /**
     * Execute the listServers tool.
     *
     * @return A map containing the list of servers with their capabilities
     */
    @NotNull
    private Map<String, Object> executeInternal(Project project) {
        var accessor = LanguageServiceAccessor.getInstance(project);
        var servers = accessor.getStartedServers();

        List<Map<String, Object>> serverList = servers.stream()
                .map(this::serializeServer)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("servers", serverList);
        result.put("count", serverList.size());
        return result;
    }

    /**
     * Serialize a language server wrapper to a map for MCP response.
     */
    @NotNull
    private Map<String, Object> serializeServer(@NotNull LanguageServerWrapper wrapper) {
        Map<String, Object> serverInfo = new HashMap<>();

        // Basic info
        serverInfo.put("id", wrapper.getServerDefinition().getId());
        serverInfo.put("displayName", wrapper.getServerDefinition().getDisplayName());
        serverInfo.put("status", wrapper.getServerStatus().name());

        // Capabilities
        ServerCapabilities capabilities = wrapper.getServerCapabilitiesSync();
        if (capabilities != null) {
            serverInfo.put("capabilities", extractCapabilities(capabilities));
        }

        return serverInfo;
    }

    /**
     * Extract relevant capabilities from server capabilities.
     */
    @NotNull
    private Map<String, Object> extractCapabilities(@NotNull ServerCapabilities caps) {
        Map<String, Object> capabilities = new HashMap<>();

        // Document capabilities
        capabilities.put("documentSymbol", caps.getDocumentSymbolProvider() != null);
        capabilities.put("codeAction", caps.getCodeActionProvider() != null);
        capabilities.put("completion", caps.getCompletionProvider() != null);
        capabilities.put("hover", caps.getHoverProvider() != null);
        capabilities.put("definition", caps.getDefinitionProvider() != null);
        capabilities.put("references", caps.getReferencesProvider() != null);
        capabilities.put("rename", caps.getRenameProvider() != null);
        capabilities.put("formatting", caps.getDocumentFormattingProvider() != null);

        // Workspace capabilities
        capabilities.put("workspaceSymbol", caps.getWorkspaceSymbolProvider() != null);

        // Execute command
        if (caps.getExecuteCommandProvider() != null) {
            capabilities.put("executeCommand", true);
            ExecuteCommandOptions cmdOptions = caps.getExecuteCommandProvider();
            if (cmdOptions.getCommands() != null && !cmdOptions.getCommands().isEmpty()) {
                capabilities.put("commands", cmdOptions.getCommands());
            }
        } else {
            capabilities.put("executeCommand", false);
        }

        return capabilities;
    }
}
