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
package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport.isSelectionRangesAvailable;

/**
 * Disable the word selection for Psi element which are associated
 * to a language server which supports LSP selectionRanges
 * to avoid using range computed by the standard word selection.
 */
public class LSPWordSelectionFilter implements Condition<PsiElement> {

    private static final Key<Boolean> ENABLE_KEY = Key.create("lsp.word.selection.filter.enable");

    @Override
    public boolean value(PsiElement psiElement) {
        if (psiElement != null) {
            if (psiElement.getUserData(ENABLE_KEY) != null) {
                // Enable the word selectioner without checking the selection ranges capability
                return true;
            }
            var psiFile = psiElement.getContainingFile();
            if (psiFile != null) {
                // If there is a language server which have LSP selection ranges capability
                // the word selectioner must be disabled
                return !isSelectionRangesAvailable(psiFile);
            }
        }
        return false;
    }

    /**
     * Enable the {@link com.intellij.codeInsight.editorActions.wordSelection.WordSelectioner}
     * @param e the current {@link PsiElement}
     */
    static void enable(@NotNull PsiElement e) {
        e.putUserData(ENABLE_KEY, true);
    }

    /**
     * Disable the {@link com.intellij.codeInsight.editorActions.wordSelection.WordSelectioner}
     * @param e the current {@link PsiElement}
     */
    static void disable(@NotNull PsiElement e) {
        e.putUserData(ENABLE_KEY, null);
    }
}
