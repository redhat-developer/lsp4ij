/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.diagnostics;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LSPPsiReference implements PsiReference {
    private PsiElement element;

    public LSPPsiReference(PsiElement element) {
        this.element = element;
    }

    @NotNull
    @Override
    public PsiElement getElement() {
        return element;
    }

    @NotNull
    @Override
    public TextRange getRangeInElement() {
        return new TextRange(0, element.getText().length());
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return element;
    }

    @NotNull
    @Override
    public String getCanonicalText() {
        return element.getText();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        return element;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        this.element = element;
        return element;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        return this.element == element;
    }

    @Override
    public boolean isSoft() {
        return false;
    }
}
