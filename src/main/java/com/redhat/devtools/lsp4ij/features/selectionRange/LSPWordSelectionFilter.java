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
import com.intellij.psi.PsiElement;

import static com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport.isSelectionRangesAvailable;

/**
 * Disable the word selection for Psi element which are associated
 * to a language server which supports LSP selectionRanges
 * to avoid using range computed by the standard word selection.
 */
public class LSPWordSelectionFilter implements Condition<PsiElement> {

    @Override
    public boolean value(PsiElement psiElement) {
        if (psiElement != null) {
            var psiFile = psiElement.getContainingFile();
            if (psiFile != null) {
                return !isSelectionRangesAvailable(psiFile);
            }
        }
        return false;
    }
}
