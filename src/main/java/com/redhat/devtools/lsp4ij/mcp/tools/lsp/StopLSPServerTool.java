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
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.mcp.MCPJsonUtils;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBase;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP tool to stop a language server.
 */
public class StopLSPServerTool extends MCPToolBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(StopLSPServerTool.class);

    @Override
    @NotNull
    protected String getToolInputSchema() {
        // language=json
        return """
            {
              "type": "object",
              "properties": {
                "serverId": {
                  "type": "string",
                  "description": "The language server ID"
                }
              },
              "required": ["serverId"],
              "description": "Stop a language server by its ID"
            }
            """;
    }

    @Override
    @NotNull
    public McpSchema.CallToolResult execute(
            @NotNull Project project,
            @NotNull McpSyncServerExchange exchange,
            @NotNull McpSchema.CallToolRequest request) {
        try {
            Map<String, Object> arguments = request.arguments();
            String serverId = (String) arguments.get("serverId");

            LanguageServerManager.getInstance(project).stop(serverId);

            Map<String, Object> result = new HashMap<>();
            result.put("serverId", serverId);
            result.put("status", "stopped");
            result.put("message", "Language server stop initiated");

            return McpSchema.CallToolResult.builder()
                    .content(java.util.List.of(new McpSchema.TextContent(MCPJsonUtils.toJsonString(result))))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error stopping language server", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
}
