/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.redhat.devtools.intellij.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.intellij.lsp4ij.console.actions.AutoFoldingAction;
import com.redhat.devtools.intellij.lsp4ij.console.actions.ClearThisConsoleAction;
import com.redhat.devtools.intellij.lsp4ij.console.explorer.LanguageServerProcessTreeNode;
import com.redhat.devtools.intellij.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.intellij.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.redhat.devtools.intellij.lsp4ij.console.actions.AutoFoldingAction.shouldLSPTracesBeExpanded;

/**
 * Extends {@link ConsoleViewImpl} to support custom LSP folding by using [Trace
 */
public class LSPConsoleView extends ConsoleViewImpl {

    private final LanguageServersRegistry.LanguageServerDefinition serverDefinition;

    public LSPConsoleView(@NotNull LanguageServersRegistry.LanguageServerDefinition serverDefinition, @NotNull Project project,
                          @NotNull GlobalSearchScope searchScope,
                          boolean viewer,
                          boolean usePredefinedMessageFilter) {
        super(project, searchScope, viewer, usePredefinedMessageFilter);
        this.serverDefinition = serverDefinition;
    }

    @Override
    public AnAction @NotNull [] createConsoleActions() {
        // Don't call super.createConsoleActions() to avoid having some action action like previous occurrence that we don't need.
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
            boolean expanded = shouldLSPTracesBeExpanded(editor);
            for (int line = startLine; line <= endLine; line++) {
                var lineText = EditorHyperlinkSupport.getLineText(document, line, false);
                if (lineText.startsWith("[Trace")) {
                    var foldingEndOffset = document.getLineStartOffset(line) - 1;
                    if (foldingStartOffset != -1 && lineNumber > 0) {
                        // Fold the previous Trace
                        var region = editor.getFoldingModel().addFoldRegion(foldingStartOffset, foldingEndOffset, foldingPaceholder);
                        if (region != null) {
                            region.setExpanded(expanded);
                        }
                    }
                    foldingStartOffset = foldingEndOffset + 1;
                    foldingPaceholder = lineText;
                    lineNumber = 0;
                } else {
                    if (foldingStartOffset != -1) {
                        lineNumber++;
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
        UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = UserDefinedLanguageServerSettings.getInstance(getProject()).getLanguageServerSettings(serverDefinition.id);
        if (settings == null) {
            return false;
        }
        return settings.getServerTrace() == ServerTrace.verbose;
    }

}
