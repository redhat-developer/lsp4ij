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
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.mcp.MCPJsonUtils;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBase;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

/**
 * MCP tool to get diagnostics (errors, warnings) for a file.
 *
 * <p>Aggregates diagnostics from all language servers associated with the file.</p>
 */
public class GetDiagnosticsTool extends MCPToolBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDiagnosticsTool.class);

    @Override
    @NotNull
    protected String getToolInputSchema() {
        // language=JSON
        return """
            {
              "type": "object",
              "properties": {
                "uri": {
                  "type": "string",
                  "description": "URI of the file (e.g., 'file:///path/to/file.java')"
                }
              },
              "required": ["uri"],
              "description": "Get all diagnostics (errors, warnings, info, hints) for a file from all associated language servers. Uses LSP TextDocumentIdentifier format."
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
            if (fileUri == null || fileUri.isEmpty()) {
                return errorResult("Missing required parameter: uri");
            }

            // Find file
            VirtualFile file = LSPIJUtils.findResourceFor(fileUri);
            if (file == null) {
                return errorResult("File not found: " + fileUri);
            }

            // Get all started language servers
            var servers = LanguageServiceAccessor.getInstance(project).getStartedServers();

            // Aggregate diagnostics from all servers - use LSP4J Diagnostic objects directly
            List<Map<String, Object>> diagnostics = new ArrayList<>();
            for (var ls : servers) {
                URI uri = FileUriSupport.getFileUri(file, ls.getClientFeatures());
                var openedDocument = ls.getOpenedDocument(uri);
                if (openedDocument != null) {
                    for (var diagnostic : openedDocument.getDiagnostics()) {
                        Map<String, Object> diagMap = new HashMap<>();
                        diagMap.put("diagnostic", diagnostic); // LSP4J Diagnostic object
                        diagMap.put("server", ls.getServerDefinition().getId());
                        diagnostics.add(diagMap);
                    }
                }
            }

            // Build result
            Map<String, Object> result = new HashMap<>();
            result.put("textDocument", textDocument);
            result.put("totalDiagnostics", diagnostics.size());
            result.put("diagnostics", diagnostics);

            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(MCPJsonUtils.toJsonString(result))))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error getting diagnostics", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

}
