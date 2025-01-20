/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.redhat.devtools.lsp4ij.console.actions.AutoFoldingAction;
import com.redhat.devtools.lsp4ij.console.actions.ClearThisConsoleAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends {@link ConsoleViewImpl} to support custom DAP folding by using [Trace
 */
public class DAPConsoleView extends ConsoleViewImpl {

    public DAPConsoleView(@NotNull Project project,
                          @NotNull GlobalSearchScope searchScope,
                          boolean viewer,
                          boolean usePredefinedMessageFilter) {
        super(project, searchScope, viewer, usePredefinedMessageFilter);
    }

    @Override
    public AnAction @NotNull [] createConsoleActions() {
        // Don't call super.createConsoleActions() to avoid having some action like previous occurrence that we don't need.
        List<AnAction> consoleActions = new ArrayList<>();
        consoleActions.add(new AutoFoldingAction(getEditor()));
        consoleActions.add(new ScrollToTheEndToolbarAction(getEditor()));
        consoleActions.add(ActionManager.getInstance().getAction("Print"));
        consoleActions.add(new ClearThisConsoleAction(this));
        return consoleActions.toArray(AnAction.EMPTY_ARRAY);
    }

    @Override
    protected void updateFoldings(int startLine, int endLine) {
        super.updateFoldings(startLine, endLine);
        if (!canApplyFolding()) {
            return;
        }

        var editor = getEditor();
        editor.getFoldingModel().runBatchFoldingOperation(() -> {
            var document = editor.getDocument();
            int foldingStartOffset = -1;
            String foldingPaceholder = null;
            int lineNumber = 0;
            boolean expanded = AutoFoldingAction.shouldLSPTracesBeExpanded(editor);
            int nbEmptyLine = 0;
            for (int line = startLine; line <= endLine; line++) {
                var lineText = EditorHyperlinkSupport.getLineText(document, line, false);
                if (lineText.startsWith("[Trace")) {
                    foldingStartOffset = document.getLineStartOffset(line);
                    foldingPaceholder = lineText;
                    lineNumber = 0;
                } else {
                    if (foldingStartOffset != -1) {
                        // Folding...
                        if (lineText.isEmpty()) {
                            nbEmptyLine++;
                            if (nbEmptyLine == 2) {
                                var foldingEndOffset = document.getLineStartOffset(line);
                                // Fold the previous Trace
                                var region = editor.getFoldingModel().addFoldRegion(foldingStartOffset, foldingEndOffset, foldingPaceholder);
                                if (region != null) {
                                    region.setExpanded(expanded);
                                }
                                nbEmptyLine = 0;
                                foldingStartOffset = -1;
                                foldingPaceholder = null;
                            }
                        } else {
                            lineNumber++;
                        }
                    }
                }
            }
            if (foldingStartOffset != -1 && lineNumber > 0) {
                // Fold the previous end Trace
                var foldingEndOffset = document.getLineStartOffset(endLine) - 1;
                var region = editor.getFoldingModel().addFoldRegion(foldingStartOffset, foldingEndOffset, foldingPaceholder);
                if (region != null) {
                    region.setExpanded(expanded);
                }
            }
        });
    }

    /**
     * Returns true if language server settings is configured with "verbose" level trace for the language server and false otherwise.
     *
     * @return true if language server settings is configured with "verbose" level trace for the language server and false otherwise.
     */
    private boolean canApplyFolding() {
        /*UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = UserDefinedLanguageServerSettings.getInstance(getProject()).getLanguageServerSettings(serverDefinition.getId());
        if (settings == null) {
            return false;
        }
        return settings.getServerTrace() == ServerTrace.verbose;*/
        return true;
    }

}
