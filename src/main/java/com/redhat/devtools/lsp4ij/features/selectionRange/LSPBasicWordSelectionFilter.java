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
package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;

/**
 * Prevents {@link com.intellij.codeInsight.editorActions.wordSelection.WordSelectioner} from adding selections to LSP files.
 */
public class LSPBasicWordSelectionFilter implements Condition<PsiElement> {
    @Override
    public boolean value(PsiElement element) {
        if ((element == null) || !element.isValid()) {
            return false;
        }

        PsiFile file = element.getContainingFile();
        if ((file == null) || !file.isValid()) {
            return false;
        }

        return LSPFileSupport.getSupport(file).getSelectionRangeSupport() == null;
    }
}
