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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationHelper;
import com.redhat.devtools.lsp4ij.features.documentation.LSPHoverParams;
import com.redhat.devtools.lsp4ij.mcp.MCPJsonUtils;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBase;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool to get hover information at a position.
 */
public class GetHoverTool extends MCPToolBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetHoverTool.class);
    private static final int HOVER_TIMEOUT_SECONDS = 5;

    @Override
    @NotNull
    protected String getToolInputSchema() {
        // language=json
        return """
            {
              "type": "object",
              "properties": {
                "textDocument": {
                  "type": "object",
                  "properties": {
                    "uri": {"type": "string", "description": "URI of the file"}
                  },
                  "required": ["uri"]
                },
                "position": {
                  "type": "object",
                  "properties": {
                    "line": {"type": "integer", "description": "Line number (0-based)"},
                    "character": {"type": "integer", "description": "Character offset (0-based)"}
                  },
                  "required": ["line", "character"]
                }
              },
              "required": ["textDocument", "position"],
              "description": "Get hover information (type, documentation) at a position. Uses LSP TextDocumentPositionParams format."
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
            // Parse LSP TextDocumentPositionParams directly
            var posParams = MCPJsonUtils.parseParams(request.arguments(), org.eclipse.lsp4j.TextDocumentPositionParams.class);

            String fileUri = posParams.getTextDocument().getUri();
            VirtualFile file = LSPIJUtils.findResourceFor(fileUri);
            if (file == null) {
                return errorResult("File not found");
            }

            PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
            Document document = LSPIJUtils.getDocument(file);
            if (psiFile == null || document == null) {
                return errorResult("Cannot process file");
            }

            int offset = LSPIJUtils.toOffset(posParams.getPosition(), document);
            var params = new LSPHoverParams(posParams.getTextDocument(), posParams.getPosition(), offset);

            var hoverData = LSPFileSupport.getSupport(psiFile)
                    .getHoverSupport()
                    .getHover(params)
                    .get(HOVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (hoverData == null || hoverData.isEmpty()) {
                return errorResult("No hover information available");
            }

            // Use LSP4J MarkupContent objects directly
            List<Map<String, Object>> results = new ArrayList<>();
            for (var data : hoverData) {
                var markupContents = LSPDocumentationHelper.getValidMarkupContents(data.hover());
                if (!markupContents.isEmpty()) {
                    for (var markup : markupContents) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("content", markup); // LSP4J MarkupContent object
                        item.put("server", data.languageServer().getServerDefinition().getId());
                        results.add(item);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("textDocument", posParams.getTextDocument());
            result.put("position", posParams.getPosition());
            result.put("hover", results);

            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(MCPJsonUtils.toJsonString(result))))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error getting hover", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
}
