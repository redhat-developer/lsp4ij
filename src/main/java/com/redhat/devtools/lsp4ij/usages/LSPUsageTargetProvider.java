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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import com.intellij.usages.UsageTargetUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.redhat.devtools.lsp4ij.LSPIJUtils.getWordRangeAt;

/**
 * LSP Usage target provider.
 */
public class LSPUsageTargetProvider implements UsageTargetProvider {

    private static final Key<Boolean> LSP_FIND_USAGES_SEARCHING_KEY = Key.create("lsp.find.usages.searching");

    @Override
    public UsageTarget @Nullable [] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
        if (!supportLSPFindUsages(editor, file)) {
            return null;
        }
        return getLSPTargets(editor, file);
    }

    private boolean supportLSPFindUsages(@NotNull Editor editor, @NotNull PsiFile file) {
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            // The file is not associated to a language server
            return false;
        }
        // Check if there are some UserTarget for the editor/file by excluding the LSPUsageTargetProvider.
        // If it exists some UserTarget (ex : 'Find Usages' for a language like JAVA),
        // the LSP Find Usages target is disabled
        // to avoid overriding the 'Find Usages' of the language (ex: JAVA)
        Boolean searching = editor.getUserData(LSP_FIND_USAGES_SEARCHING_KEY);
        if (searching != null) {
            return false;
        }
        try {
            editor.putUserData(LSP_FIND_USAGES_SEARCHING_KEY, Boolean.TRUE);
            return UsageTargetUtil.findUsageTargets(editor, file).length == 0;
        } finally {
            editor.putUserData(LSP_FIND_USAGES_SEARCHING_KEY, null);
        }
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
        TextRange tokenRange = getWordRangeAt(document, offset);
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
