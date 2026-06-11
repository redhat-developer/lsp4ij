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
package com.redhat.devtools.lsp4ij.mcp.tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBase;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * MCP tool to read the content of a file.
 *
 * <p><b>Use cases:</b></p>
 * <ul>
 *   <li>Read source files to understand code</li>
 *   <li>Get file content for analysis</li>
 * </ul>
 */
public class GetFileContentTool extends MCPToolBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetFileContentTool.class);

    @Override
    @NotNull
    protected String getToolInputSchema() {
        // language=json
        return """
            {
              "type": "object",
              "properties": {
                "fileUri": {
                  "type": "string",
                  "description": "URI of the file to read (e.g., 'file:///path/to/file.java')"
                }
              },
              "required": ["fileUri"],
              "description": "Read the content of a file from the project."
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
            if (arguments == null) {
                return errorResult("Missing arguments");
            }

            String fileUri = (String) arguments.get("fileUri");
            if (fileUri == null || fileUri.isEmpty()) {
                return errorResult("Missing required parameter: fileUri");
            }

            // Find file
            VirtualFile file = LSPIJUtils.findResourceFor(fileUri);
            if (file == null) {
                return errorResult("File not found: " + fileUri);
            }

            if (file.isDirectory()) {
                return errorResult("Path is a directory, not a file: " + fileUri);
            }

            // Read content
            String content = new String(file.contentsToByteArray(), file.getCharset());

            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(content)))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error reading file", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
}
