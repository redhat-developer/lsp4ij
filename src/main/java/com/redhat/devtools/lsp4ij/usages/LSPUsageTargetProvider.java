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
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.internal.SimpleLanguageUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP Usage target provider.
 */
public class LSPUsageTargetProvider implements UsageTargetProvider {

    @Override
    public UsageTarget @Nullable [] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
        if (!SimpleLanguageUtils.isSupported(file.getLanguage())) {
            return null;
        }
        return getLSPTargets(editor, file);
    }

    @NotNull
    private static UsageTarget[] getLSPTargets(@NotNull Editor editor, @NotNull PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        LSPUsageTriggeredPsiElement triggeredElement = new LSPUsageTriggeredPsiElement(file, new TextRange(offset > 0 ? offset - 1 : offset, offset));
        // Try to compute a proper name for the target
        Document document = editor.getDocument();
        if (offset > 0 && offset >= document.getTextLength()) {
            offset = document.getTextLength() - 1;
        }
        int start = getLeftOffsetOfPart(document, offset);
        int end = getRightOffsetOfPart(document, offset);
        if (start < end) {
            String name = document.getText(new TextRange(start, end));
            triggeredElement.setName(name);
        }
        UsageTarget target = new PsiElement2UsageTargetAdapter(triggeredElement, true);
        return new UsageTarget[]{target};
    }

    private static int getLeftOffsetOfPart(Document document, int offset) {
        int i = offset;
        for (; i > 0; i--) {
            char c = document.getCharsSequence().charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                return i == offset ? offset : i + 1;
            }
        }
        return i;
    }

    private static int getRightOffsetOfPart(Document document, int offset) {
        int i = offset;
        for (; i < document.getTextLength(); i++) {
            char c = document.getCharsSequence().charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                return i == offset ? offset : i;
            }
        }
        return i;
    }

    @Override
    public UsageTarget @Nullable [] getTargets(@NotNull PsiElement psiElement) {
        PsiFile file = psiElement.getContainingFile();
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            return null;
        }
        Editor editor = LSPIJUtils.editorForElement(psiElement);
        return editor != null ? getLSPTargets(editor, file) : null;
    }
}
