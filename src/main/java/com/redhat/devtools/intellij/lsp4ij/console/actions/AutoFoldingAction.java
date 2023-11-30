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
package com.redhat.devtools.intellij.lsp4ij.console.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.project.DumbAware;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Collapse / Expand all LSP Traces action.
 */
public class AutoFoldingAction extends ToggleAction implements DumbAware {

    private boolean initialExpanded;

    private final Editor myEditor;

    public AutoFoldingAction(@NotNull final Editor editor) {
        super();
        myEditor = editor;
        final String message = LanguageServerBundle.message("action.lsp.console.folding.text");
        getTemplatePresentation().setDescription(message);
        getTemplatePresentation().setText(message);
        getTemplatePresentation().setIcon(AllIcons.Actions.Expandall);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return shouldLSPTracesBeExpanded(myEditor);
    }

    /**
     * Returns true if LSP traces from the console editor should be expanded and false otherwise.
     *
     * @param editor the console editor.
     * @return true if LSP traces from the console editor should be expanded and false otherwise.
     */
    public static boolean shouldLSPTracesBeExpanded(Editor editor) {
        // Takes the last fold region and returns the expanded state
        FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
        FoldRegion lastRegion = allRegions.length > 0 ? allRegions[allRegions.length - 1] : null;
        return lastRegion != null ? lastRegion.isExpanded() : false;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        boolean expanded = state;
        initialExpanded = expanded;
        myEditor.getFoldingModel().runBatchFoldingOperation(() -> {
            FoldRegion[] allRegions = myEditor.getFoldingModel().getAllFoldRegions();
            for (FoldRegion region : allRegions) {
                region.setExpanded(expanded);
            }
        });
    }
}
