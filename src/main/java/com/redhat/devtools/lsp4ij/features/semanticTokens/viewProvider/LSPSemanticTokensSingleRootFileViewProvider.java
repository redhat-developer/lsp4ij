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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Implementation of {@link LSPSemanticTokensFileViewProvider} where PSI files are based on {@link SingleRootFileViewProvider}.
 */
public class LSPSemanticTokensSingleRootFileViewProvider
        extends SingleRootFileViewProvider
        implements LSPSemanticTokensFileViewProvider {

    private final LSPSemanticTokensFileViewProviderHelper helper;

    /**
     * Creates a new semantic tokens-based file view provider for a file with a specific language.
     *
     * @param manager            the PSI manager
     * @param virtualFile        the virtual file
     * @param eventSystemEnabled whether or not the event system is enabled
     * @param language           the file's language
     */
    protected LSPSemanticTokensSingleRootFileViewProvider(@NotNull PsiManager manager,
                                                          @NotNull VirtualFile virtualFile,
                                                          boolean eventSystemEnabled,
                                                          @NotNull Language language) {
        super(manager, virtualFile, eventSystemEnabled, language);
        this.helper = new LSPSemanticTokensFileViewProviderHelper(this);
    }

    /**
     * Creates a new semantic tokens-based file view provider for a file without a specific language.
     *
     * @param manager            the PSI manager
     * @param virtualFile        the virtual file
     * @param eventSystemEnabled whether or not the event system is enabled
     */
    protected LSPSemanticTokensSingleRootFileViewProvider(@NotNull PsiManager manager,
                                                          @NotNull VirtualFile virtualFile,
                                                          boolean eventSystemEnabled) {
        super(manager, virtualFile, eventSystemEnabled);
        this.helper = new LSPSemanticTokensFileViewProviderHelper(this);
    }

    @Override
    public boolean isEnabled() {
        return helper.isEnabled();
    }

    @Override
    public boolean isKeyword(int offset) {
        return helper.isKeyword(offset);
    }

    @Override
    public boolean isOperator(int offset) {
        return helper.isOperator(offset);
    }

    @Override
    public boolean isDeclaration(int offset) {
        return helper.isDeclaration(offset);
    }

    @Override
    public boolean isReference(int offset) {
        return helper.isReference(offset);
    }

    @Override
    public boolean isStringLiteral(int offset) {
        return helper.isStringLiteral(offset);
    }

    @Override
    public boolean isNumericLiteral(int offset) {
        return helper.isNumericLiteral(offset);
    }

    @Override
    public boolean isRegularExpression(int offset) {
        return helper.isRegularExpression(offset);
    }

    @Override
    public boolean isComment(int offset) {
        return helper.isComment(offset);
    }

    @Override
    @NotNull
    public ThreeState isType(int offset) {
        return helper.isType(offset);
    }

    @Override
    @Nullable
    public String getElementDescription(@NotNull PsiElement element, @Nullable PsiElement referenceElement) {
        return helper.getElementDescription(element, referenceElement);
    }

    @Override
    @Nullable
    public TextRange getSemanticTokenTextRange(int offset) {
        return helper.getSemanticTokenTextRange(offset);
    }

    @Override
    public void addSemanticToken(@NotNull TextRange textRange,
                                 @Nullable String tokenType,
                                 @Nullable List<String> tokenModifiers) {
        helper.addSemanticToken(textRange, tokenType, tokenModifiers);
    }

    /**
     * Returns the semantic token for the offset.
     *
     * @param offset the offset
     * @return the semantic token or null if no semantic token exists at the offset
     */
    @Nullable
    LSPSemanticToken getSemanticToken(int offset) {
        return helper.getSemanticToken(offset);
    }
}
