/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction.intention;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.codeAction.LSPLazyCodeActionIntentionAction;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * LSP {@link com.intellij.codeInsight.intention.IntentionAction} at given index.
 */
public abstract class LSPIntentionAction extends LSPLazyCodeActionIntentionAction {
    public LSPIntentionAction(int index) {
        super(index);
    }

    /**
     * Called by IntelliJ to check if this intention action should be available (show the light bulb).
     * This method is invoked frequently as the user moves the caret or edits the file.
     *
     * @param project the current project
     * @param editor the current editor
     * @param file the current PSI file
     * @return true if the intention is available at the current caret position
     */
    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        // Don't execute LSP features during indexing
        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            return false;
        }

        // Get the code action support for this file
        LSPIntentionCodeActionSupport intentionCodeActionSupport = LSPFileSupport.getSupport(file).getIntentionCodeActionSupport();

        // Create parameters for the current caret position/selection
        CodeActionParams params = createCodeActionParams(editor, file);

        // Only trigger a new textDocument/codeAction request if the position changed
        // This optimization avoids redundant LSP calls when IntelliJ polls isAvailable() multiple times
        if (intentionCodeActionSupport.hasChanged(params)) {
            super.setLazyCodeActions(intentionCodeActionSupport);
            intentionCodeActionSupport.getCodeActions(params);
        }

        // Delegate to parent to check if the specific code action at this index is available
        return super.isAvailable(project, editor, file);
    }

    /**
     * Create the LSP code action parameters for the current editor position.
     * These parameters are used for the textDocument/codeAction request.
     *
     * @param editor the editor containing the caret position
     * @param file the PSI file being edited
     * @return the LSP code action parameters with range, context, and document identifier
     */
    private static CodeActionParams createCodeActionParams(@NotNull Editor editor,
                                                           @NotNull PsiFile file) {
        // Get the current document
        Document document = LSPIJUtils.getDocument(file.getVirtualFile());

        // Get the caret position or selection range
        Caret caret = editor.getCaretModel().getPrimaryCaret();
        Range range = LSPIJUtils.toRange(caret.getSelectionRange(), document);

        // Create the context - empty diagnostics list since this is for intentions, not quick fixes
        CodeActionContext context = new CodeActionContext(Collections.emptyList());
        context.setTriggerKind(CodeActionTriggerKind.Automatic);

        // Create the text document identifier with the file URI
        // The URI is required for the LSP server to identify which file we're requesting code actions for
        TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
        textDocument.setUri(FileUriSupport.toString(file.getVirtualFile(), null));

        return new CodeActionParams(textDocument, range, context);
    }
}
