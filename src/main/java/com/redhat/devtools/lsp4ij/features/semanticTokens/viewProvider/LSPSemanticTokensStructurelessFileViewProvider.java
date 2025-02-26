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

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Semantic tokens-based file view provider for files with no inherent PSI tree where PSI element should correspond
 * exactly to semantic tokens, e.g., plain text and TextMate files.
 */
final class LSPSemanticTokensStructurelessFileViewProvider extends LSPSemanticTokensSingleRootFileViewProvider {

    LSPSemanticTokensStructurelessFileViewProvider(@NotNull PsiManager psiManager,
                                                   @NotNull VirtualFile virtualFile,
                                                   boolean eventSystemEnabled,
                                                   @NotNull Language language) {
        super(psiManager, virtualFile, eventSystemEnabled, language);
    }

    LSPSemanticTokensStructurelessFileViewProvider(@NotNull PsiManager psiManager,
                                                   @NotNull VirtualFile virtualFile,
                                                   boolean eventSystemEnabled) {
        super(psiManager, virtualFile, eventSystemEnabled);
    }

    @Override
    public boolean supportsIncrementalReparse(@NotNull Language rootLanguage) {
        // These files do not support incremental reparse
        if (isEnabled()) {
            return false;
        }
        return super.supportsIncrementalReparse(rootLanguage);
    }

    // NOTE: These are really the core of what makes this all work. Basically when any external caller needs to
    // find an element or reference for a given offset in the file, we use the semantic token information that
    // was populated the last time that semantic tokens were returned by the language server to return an element
    // at that offset (or not). In all cases, we take great care to delegate to the inherited behavior if we
    // don't know for a fact that we can/should respond ourselves. This ensures that non-LSP4IJ TextMate files
    // see no change in behavior.

    @Override
    public PsiElement findElementAt(int offset) {
        if (isEnabled()) {
            LSPSemanticToken semanticToken = getSemanticToken(offset);
            return semanticToken != null ? semanticToken.getElement() : super.findElementAt(offset);
        }
        return super.findElementAt(offset);
    }

    @Override
    public PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang) {
        return isEnabled() ? findElementAt(offset) : super.findElementAt(offset, lang);
    }

    @Override
    public PsiElement findElementAt(int offset, @NotNull Language language) {
        return isEnabled() ? findElementAt(offset) : super.findElementAt(offset, language);
    }

    @Override
    public PsiReference findReferenceAt(int offset) {
        if (isEnabled()) {
            LSPSemanticToken semanticToken = getSemanticToken(offset);
            LSPSemanticTokenElementType elementType = semanticToken != null ? semanticToken.getElementType() : null;
            return elementType == LSPSemanticTokenElementType.REFERENCE ? new LSPSemanticTokenPsiReference(semanticToken) : null;
        }
        return super.findReferenceAt(offset);
    }

    @Override
    @Nullable
    public PsiReference findReferenceAt(int offset, @NotNull Language language) {
        return isEnabled() ? findReferenceAt(offset) : super.findReferenceAt(offset, language);
    }
}
