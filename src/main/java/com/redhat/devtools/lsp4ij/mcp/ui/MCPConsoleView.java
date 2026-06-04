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
package com.redhat.devtools.lsp4ij.mcp.ui;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Console view for MCP traces with folding support.
 *
 * Similar to {@link com.redhat.devtools.lsp4ij.console.LSPConsoleView} but for MCP server traces.
 */
public class MCPConsoleView extends ConsoleViewImpl {

    public MCPConsoleView(@NotNull Project project) {
        super(project, GlobalSearchScope.allScope(project), false, true);
    }

    @Override
    protected void updateFoldings(int startLine, int endLine) {
        super.updateFoldings(startLine, endLine);

        var editor = getEditor();
        editor.getFoldingModel().runBatchFoldingOperation(() -> {
            var document = editor.getDocument();
            int foldingStartOffset = -1;
            String foldingPlaceholder = null;
            int lineNumber = 0;

            for (int line = startLine; line <= endLine; line++) {
                var lineText = EditorHyperlinkSupport.getLineText(document, line, false);
                if (lineText.startsWith("[Trace")) {
                    var foldingEndOffset = document.getLineStartOffset(line) - 1;
                    if (foldingStartOffset != -1 && lineNumber > 0) {
                        // Fold the previous Trace
                        var region = editor.getFoldingModel().addFoldRegion(foldingStartOffset, foldingEndOffset, foldingPlaceholder);
                        if (region != null) {
                            region.setExpanded(false); // Collapsed by default
                        }
                    }
                    foldingStartOffset = foldingEndOffset + 1;
                    foldingPlaceholder = lineText;
                    lineNumber = 0;
                } else {
                    if (foldingStartOffset != -1) {
                        lineNumber++;
                    }
                }
            }
            if (foldingStartOffset != -1 && lineNumber > 0) {
                // Fold the last Trace
                var foldingEndOffset = document.getLineStartOffset(endLine) - 1;
                var region = editor.getFoldingModel().addFoldRegion(foldingStartOffset, foldingEndOffset, foldingPlaceholder);
                if (region != null) {
                    region.setExpanded(false); // Collapsed by default
                }
            }
        });
    }
}
