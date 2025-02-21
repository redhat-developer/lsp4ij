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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.navigation.LSPGotoDeclarationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A PSI reference to a semantic token declaration that defers uses
 * {@link LSPGotoDeclarationHandler#getGotoDeclarationTargets(PsiElement, int, Editor)} for deferred resolution.
 */
class LSPSemanticTokenPsiReference extends PsiReferenceBase<LSPSemanticTokenPsiElement> {

    private final LSPSemanticToken semanticToken;

    /**
     * Creates a reference for the provided reference semantic token.
     *
     * @param semanticToken the semantic token
     */
    LSPSemanticTokenPsiReference(@NotNull LSPSemanticToken semanticToken) {
        super(semanticToken.getElement());
        this.semanticToken = semanticToken;
    }

    @Override
    @Nullable
    public PsiElement resolve() {
        LSPSemanticTokenPsiElement element = getElement();
        LSPSemanticTokenElementType elementType = semanticToken.getElementType();
        if (elementType == LSPSemanticTokenElementType.REFERENCE) {
            Editor editor = LSPIJUtils.editorForElement(element);
            if (editor != null) {
                // Cache the resolved reference so that it only has to happen once until/unless the source/target file(s) change
                return CachedValuesManager.getCachedValue(element, new CachedValueProvider<>() {
                    @Override
                    @Nullable
                    public Result<PsiElement> compute() {
                        PsiElement[] targets = LSPGotoDeclarationHandler.getGotoDeclarationTargets(element, element.getTextOffset());

                        // Add both source and target files as cache dependencies
                        Set<PsiElement> dependencies = new HashSet<>();
                        dependencies.add(element.getContainingFile());
                        if (!ArrayUtil.isEmpty(targets)) {
                            for (PsiElement target : targets) {
                                PsiFile targetFile = target != null ? target.getContainingFile() : null;
                                ContainerUtil.addIfNotNull(dependencies, targetFile);
                            }
                        }

                        PsiElement target = ArrayUtil.getFirstElement(targets);
                        return target != null ? Result.create(target, dependencies.toArray()) : null;
                    }
                });
            }
        }

        return null;
    }

    @Override
    @NotNull
    public TextRange getAbsoluteRange() {
        //noinspection DataFlowIssue
        return getElement().getTextRange();
    }

    @Override
    public Object @NotNull [] getVariants() {
        // We don't have enough information to return variants here
        return EMPTY_ARRAY;
    }

    @Override
    public boolean equals(Object o) {
        if ((o == null) || (getClass() != o.getClass())) return false;
        LSPSemanticTokenPsiReference that = (LSPSemanticTokenPsiReference) o;
        return Objects.equals(semanticToken, that.semanticToken);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(semanticToken);
    }
}
