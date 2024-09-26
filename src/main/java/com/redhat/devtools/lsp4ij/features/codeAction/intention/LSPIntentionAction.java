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
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.features.codeAction.LSPLazyCodeActionIntentionAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeActionTriggerKind;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * LSP {@link com.intellij.codeInsight.intention.IntentionAction} at given index.
 */
public abstract class LSPIntentionAction extends LSPLazyCodeActionIntentionAction {
    public LSPIntentionAction(int index) {
        super(index);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            return false;
        }
        LSPIntentionCodeActionSupport intentionCodeActionSupport = LSPFileSupport.getSupport(file).getIntentionCodeActionSupport();
        super.setLazyCodeActions(intentionCodeActionSupport);
        CodeActionParams params = createCodeActionParams(editor, file);
        intentionCodeActionSupport.getCodeActions(params);
        return super.isAvailable(project, editor, file);
    }

    /**
     * Create the LSP code action parameters for the given diagnostic and file.
     *
     * @param  editor the editor.
     * @param file the file.
     * @return the LSP code action parameters for the given diagnostic and file.
     */
    private static CodeActionParams createCodeActionParams(@NotNull Editor editor,
                                                           @NotNull PsiFile file) {
        Document document = LSPIJUtils.getDocument(file.getVirtualFile());
        Caret caret = editor.getCaretModel().getPrimaryCaret();
        Range range = LSPIJUtils.toRange(caret.getSelectionRange(), document);
        CodeActionContext context = new CodeActionContext(Collections.emptyList());
        context.setTriggerKind(CodeActionTriggerKind.Automatic);
        return new CodeActionParams(LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile()), range, context);
    }
}
