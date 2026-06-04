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
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.codeAction.intention.LSPIntentionCodeActionSupport;
import com.redhat.devtools.lsp4ij.mcp.MCPJsonUtils;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBase;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MCP tool to get available code actions at a specific position or range.
 */
public class GetCodeActionsTool extends MCPToolBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCodeActionsTool.class);
    private static final int CODE_ACTION_TIMEOUT_SECONDS = 10;

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
                    "uri": {
                      "type": "string",
                      "description": "URI of the file"
                    }
                  },
                  "required": ["uri"]
                },
                "range": {
                  "type": "object",
                  "properties": {
                    "start": {
                      "type": "object",
                      "properties": {
                        "line": {"type": "integer", "description": "Line number (0-based)"},
                        "character": {"type": "integer", "description": "Character offset (0-based)"}
                      },
                      "required": ["line", "character"]
                    },
                    "end": {
                      "type": "object",
                      "properties": {
                        "line": {"type": "integer", "description": "Line number (0-based)"},
                        "character": {"type": "integer", "description": "Character offset (0-based)"}
                      },
                      "required": ["line", "character"]
                    }
                  },
                  "required": ["start", "end"]
                }
              },
              "required": ["textDocument", "range"],
              "description": "Get available code actions (quick fixes, refactorings) at a position or range. IMPORTANT: When you encounter LSP diagnostics/errors, ALWAYS check this tool FIRST for available quick fixes before attempting manual edits. Code actions provide IDE-validated fixes that respect language semantics. Uses LSP CodeActionParams format."
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
            // Parse LSP CodeActionParams directly from MCP arguments
            var params = MCPJsonUtils.parseParams(request.arguments(), CodeActionParams.class);

            String fileUri = params.getTextDocument().getUri();
            VirtualFile file = LSPIJUtils.findResourceFor(fileUri);
            if (file == null) {
                return errorResult("File not found");
            }

            PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
            Document document = LSPIJUtils.getDocument(file);
            if (psiFile == null || document == null) {
                return errorResult("Cannot process file");
            }

            // Ensure context exists and populate with diagnostics if not provided
            if (params.getContext() == null) {
                params.setContext(new CodeActionContext(new ArrayList<>()));
            }

            // If no diagnostics provided, fetch them from language servers at this range
            if (params.getContext().getDiagnostics() == null || params.getContext().getDiagnostics().isEmpty()) {
                List<Diagnostic> diagnosticsAtRange = new ArrayList<>();
                var servers = LanguageServiceAccessor.getInstance(project).getStartedServers();

                for (var ls : servers) {
                    URI uri = FileUriSupport.getFileUri(file, ls.getClientFeatures());
                    var openedDocument = ls.getOpenedDocument(uri);
                    if (openedDocument != null) {
                        for (var diagnostic : openedDocument.getDiagnosticsForServer().getDiagnostics()) {
                            // Check if diagnostic intersects with the requested range
                            if (rangesIntersect(diagnostic.getRange(), params.getRange())) {
                                diagnosticsAtRange.add(diagnostic);
                            }
                        }
                    }
                }

                if (!diagnosticsAtRange.isEmpty()) {
                    params.getContext().setDiagnostics(diagnosticsAtRange);
                }
            }

            params.getContext().setOnly(List.of(CodeActionKind.QuickFix, CodeActionKind.Refactor));

            // Get code actions via LSPIntentionCodeActionSupport
            var codeActionSupport = new LSPIntentionCodeActionSupport(psiFile);
            var codeActionsFuture = codeActionSupport.getCodeActions(params);

            var codeActions = codeActionsFuture.get(CODE_ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (codeActions == null || codeActions.isEmpty()) {
                return errorResult("No code actions available");
            }

            // Format code actions for MCP response - use LSP4J objects directly
            List<Map<String, Object>> actions = new ArrayList<>();
            for (var codeActionData : codeActions) {
                Map<String, Object> action = new HashMap<>();

                var either = codeActionData.codeAction();
                if (either.isLeft()) {
                    // Command
                    var command = either.getLeft();
                    action.put("title", command.getTitle());
                    action.put("type", "command");
                    action.put("command", command); // LSP4J Command object
                } else {
                    // CodeAction
                    var codeAction = either.getRight();
                    action.put("title", codeAction.getTitle());
                    action.put("type", "codeAction");
                    action.put("kind", codeAction.getKind());

                    if (codeAction.getDiagnostics() != null && !codeAction.getDiagnostics().isEmpty()) {
                        action.put("diagnostics", codeAction.getDiagnostics()); // LSP4J Diagnostic list
                    }

                    if (Boolean.TRUE.equals(codeAction.getIsPreferred())) {
                        action.put("preferred", true);
                    }

                    if (codeAction.getDisabled() != null) {
                        action.put("disabled", codeAction.getDisabled());
                    }

                    if (codeAction.getEdit() != null) {
                        action.put("edit", codeAction.getEdit()); // LSP4J WorkspaceEdit
                    }

                    if (codeAction.getCommand() != null) {
                        action.put("command", codeAction.getCommand()); // LSP4J Command
                    }
                }

                // Add server info
                action.put("server", codeActionData.languageServer().getServerDefinition().getId());

                actions.add(action);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("textDocument", params.getTextDocument()); // LSP4J TextDocumentIdentifier
            result.put("range", params.getRange()); // LSP4J Range object directly
            result.put("totalActions", actions.size());
            result.put("actions", actions);

            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(MCPJsonUtils.toJsonString(result))))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error getting code actions", e);
            return errorResult("Error: " + e.getMessage());
        }
    }

    /**
     * Check if two ranges intersect.
     */
    private boolean rangesIntersect(org.eclipse.lsp4j.Range r1, org.eclipse.lsp4j.Range r2) {
        // r1 ends before r2 starts
        if (comparePositions(r1.getEnd(), r2.getStart()) <= 0) {
            return false;
        }
        // r2 ends before r1 starts
        if (comparePositions(r2.getEnd(), r1.getStart()) <= 0) {
            return false;
        }
        return true;
    }

    /**
     * Compare two positions. Returns -1 if p1 < p2, 0 if equal, 1 if p1 > p2.
     */
    private int comparePositions(org.eclipse.lsp4j.Position p1, org.eclipse.lsp4j.Position p2) {
        if (p1.getLine() != p2.getLine()) {
            return Integer.compare(p1.getLine(), p2.getLine());
        }
        return Integer.compare(p1.getCharacter(), p2.getCharacter());
    }

}
