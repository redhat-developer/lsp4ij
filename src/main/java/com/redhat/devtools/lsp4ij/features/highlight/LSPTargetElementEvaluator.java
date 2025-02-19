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

package com.redhat.devtools.lsp4ij.features.highlight;

import com.intellij.codeInsight.TargetElementEvaluatorEx2;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokensFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the name identifier at the specified coordinates as the target PSI element.
 */
public class LSPTargetElementEvaluator extends TargetElementEvaluatorEx2 {
    @Override
    @Nullable
    public PsiElement adjustReferenceOrReferencedElement(@NotNull PsiFile file,
                                                         @NotNull Editor editor,
                                                         int offset,
                                                         int flags,
                                                         @Nullable PsiElement refElement) {
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            return null;
        }

        if (ProjectIndexingManager.isIndexingAll()) {
            return null;
        }

        // See if the view provider can provide an element
        LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider = LSPSemanticTokensFileViewProvider.getInstance(file);
        if (semanticTokensFileViewProvider != null) {
            return semanticTokensFileViewProvider.findElementAt(offset);
        }

        // Nope. Try to find the word at the caret and return a fake PSI element for it
        TextRange targetTextRange = LSPIJUtils.getWordRangeAt(editor.getDocument(), file, offset);
        return targetTextRange != null ? new LSPPsiElement(file, targetTextRange) : null;
    }
}
