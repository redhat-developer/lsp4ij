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

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.mcp.MCPJsonUtils;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBase;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.redhat.devtools.lsp4ij.internal.ApplicationUtils.invokeLaterIfNeeded;

/**
 * MCP tool to open a file in the editor (triggers LSP textDocument/didOpen).
 */
public class OpenFileTool extends MCPToolBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenFileTool.class);

    @Override
    @NotNull
    protected String getToolInputSchema() {
        // language=json
        return """
            {
              "type": "object",
              "properties": {
                "uri": {
                  "type": "string",
                  "description": "URI of the file to open"
                }
              },
              "required": ["uri"],
              "description": "Open a file in the editor (triggers LSP textDocument/didOpen notification). Uses LSP TextDocumentIdentifier format."
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
            // Parse LSP TextDocumentIdentifier directly
            var textDocument = MCPJsonUtils.parseParams(request.arguments(), org.eclipse.lsp4j.TextDocumentIdentifier.class);
            String fileUri = textDocument.getUri();

            VirtualFile file = LSPIJUtils.findResourceFor(fileUri);
            if (file == null) {
                return errorResult("File not found: " + fileUri);
            }

            // Open the file in the editor on EDT - this will trigger textDocument/didOpen
            CompletableFuture<Boolean> openFuture = new CompletableFuture<>();
            invokeLaterIfNeeded(() -> {
                try {
                    FileEditorManager.getInstance(project).openFile(file, true);
                    openFuture.complete(true);
                } catch (Exception e) {
                    openFuture.completeExceptionally(e);
                }
            });

            // Wait for the file to be opened (with timeout)
            try {
                openFuture.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.error("Error waiting for file to open", e);
                return errorResult("Failed to open file: " + e.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("textDocument", textDocument);
            result.put("status", "opened");

            return McpSchema.CallToolResult.builder()
                    .content(java.util.List.of(new McpSchema.TextContent(MCPJsonUtils.toJsonString(result))))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error opening file", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
}
