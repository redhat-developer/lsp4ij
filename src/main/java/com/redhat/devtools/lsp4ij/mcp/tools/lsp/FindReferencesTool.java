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
import com.redhat.devtools.lsp4ij.features.references.LSPReferenceParams;
import com.redhat.devtools.lsp4ij.mcp.MCPJsonUtils;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBase;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MCP tool to find all references to a symbol.
 */
public class FindReferencesTool extends MCPToolBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindReferencesTool.class);
    private static final int REFERENCES_TIMEOUT_SECONDS = 10;

    @Override
    @NotNull
    protected String getToolInputSchema() {
        // language=JSON
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
                },
                "context": {
                  "type": "object",
                  "properties": {
                    "includeDeclaration": {
                      "type": "boolean",
                      "description": "Include declaration (default: true)"
                    }
                  }
                }
              },
              "required": ["textDocument", "position"],
              "description": "Find all references to a symbol. Essential for refactoring and impact analysis. Uses LSP ReferenceParams format."
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
            // Parse LSP ReferenceParams directly
            var refParams = MCPJsonUtils.parseParams(request.arguments(), org.eclipse.lsp4j.ReferenceParams.class);

            String fileUri = refParams.getTextDocument().getUri();
            VirtualFile file = LSPIJUtils.findResourceFor(fileUri);
            if (file == null) {
                return errorResult("File not found");
            }

            PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
            Document document = LSPIJUtils.getDocument(file);
            if (psiFile == null || document == null) {
                return errorResult("Cannot process file");
            }

            int offset = LSPIJUtils.toOffset(refParams.getPosition(), document);
            var params = new LSPReferenceParams(refParams.getTextDocument(), refParams.getPosition(), offset);

            // Apply context if provided
            if (refParams.getContext() != null) {
                params.setContext(refParams.getContext());
            }

            var references = LSPFileSupport.getSupport(psiFile)
                    .getReferenceSupport()
                    .getFeatureData(params)
                    .get(REFERENCES_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (references == null || references.isEmpty()) {
                return errorResult("No references found");
            }

            // Use LSP4J Location objects directly
            List<Map<String, Object>> locations = new ArrayList<>();
            for (LocationData locationData : references) {
                var location = locationData.location();
                Map<String, Object> loc = new HashMap<>();
                loc.put("location", location); // LSP4J Location object
                locations.add(loc);
            }

            // Group by file URI
            Map<String, Long> byFile = references.stream()
                    .collect(Collectors.groupingBy(
                            loc -> loc.location().getUri(),
                            Collectors.counting()
                    ));

            Map<String, Object> result = new HashMap<>();
            result.put("textDocument", refParams.getTextDocument());
            result.put("position", refParams.getPosition());
            result.put("context", refParams.getContext());
            result.put("totalReferences", locations.size());
            result.put("referencesByFile", byFile);
            result.put("references", locations);

            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(MCPJsonUtils.toJsonString(result))))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error finding references", e);
            return errorResult("Error: " + e.getMessage());
        }
    }
}
