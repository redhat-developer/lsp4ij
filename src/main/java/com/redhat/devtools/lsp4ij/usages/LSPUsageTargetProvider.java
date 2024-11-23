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
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.ProjectIndexingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.redhat.devtools.lsp4ij.LSPIJUtils.getWordRangeAt;

/**
 * LSP Usage target provider.
 */
public class LSPUsageTargetProvider implements UsageTargetProvider {

    @Override
    public UsageTarget @Nullable [] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
        if (LanguageServersRegistry.getInstance().hasCustomLanguageFindUsages(file.getLanguage())) {
            // the file language is associated to a custom FindUsagesProvider (ex: Java FindUsagesProvider)
            // don't create LSP UsageTarget to avoid breaking the "Find Usages" from Java, etc files
            return null;
        }
        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            // The file is not associated to a language server
            return null;
        }
        // Get LSP usage targets
        return getLSPTargets(editor, file);
    }

    @Override
    public UsageTarget @Nullable [] getTargets(@NotNull PsiElement psiElement) {
        if (LanguageServersRegistry.getInstance().hasCustomLanguageFindUsages(psiElement.getLanguage())) {
            // the Psi element language is associated to a custom FindUsagesProvider (ex: Java FindUsagesProvider)
            // don't create LSP UsageTarget to avoid breaking the "Find Usages" from Java, etc files
            return null;
        }
        PsiFile file = psiElement.getContainingFile();
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            // The file is not associated to a language server
            return null;
        }
        // Get LSP usage targets
        Editor editor = LSPIJUtils.editorForElement(psiElement);
        return editor != null ? getLSPTargets(editor, file) : null;
    }

    @NotNull
    private static UsageTarget[] getLSPTargets(@NotNull Editor editor, @NotNull PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();
        if (offset < 0 ||
                offset > document.getTextLength() ||
                offset > file.getTextLength() /* PsiFile and document could have different contents */) {
            return UsageTarget.EMPTY_ARRAY;
        }
        // Try to get the token range (ex : foo.ba|r() --> foo.[bar]())
        TextRange tokenRange = getWordRangeAt(document, file, offset);
        if (tokenRange == null) {
            // Get range only for the offset
            tokenRange = new TextRange(offset > 0 ? offset - 1 : offset, offset);
        }
        LSPUsageTriggeredPsiElement triggeredElement = new LSPUsageTriggeredPsiElement(file, tokenRange);
        // force to compute of the name by using token range
        triggeredElement.getName();
        UsageTarget target = new PsiElement2UsageTargetAdapter(triggeredElement, true);
        return new UsageTarget[]{target};
    }

}
