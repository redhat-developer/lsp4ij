/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for LSP Psi element.
 */
public class LSPPsiElement extends FakePsiElement {

    private final @NotNull PsiFile file;

    private @NotNull TextRange textRange;
    private String name;

    public LSPPsiElement(@NotNull PsiFile file,
                         @NotNull TextRange textRange) {
        this.file = file;
        setTextRange(textRange);
    }

    @NotNull
    @Override
    public Project getProject() throws PsiInvalidElementAccessException {
        return file.getProject();
    }

    @Override
    public PsiFile getContainingFile() {
        return file;
    }

    @NotNull
    @Override
    public TextRange getTextRange() {
        return textRange;
    }

    public void setTextRange(@NotNull TextRange textRange) {
        this.textRange = textRange;
    }

    @Override
    public int getStartOffsetInParent() {
        return textRange.getStartOffset();
    }

    @Override
    public int getTextOffset() {
        return textRange.getStartOffset();
    }

    @Override
    public int getTextLength() {
        return textRange.getEndOffset() - textRange.getStartOffset();
    }

    @Override
    public String getName() {
        if (name != null) {
            return name;
        }
        name = file.getText().substring(textRange.getStartOffset(), textRange.getEndOffset());
        return name;
    }

    @Override
    public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
        this.name = name;
        return this;
    }

    @Override
    public char @NotNull [] textToCharArray() {
        return getName().toCharArray();
    }

    @Override
    public @Nullable @NonNls String getText() {
        return getName();
    }

    @Override
    public @NlsSafe @Nullable String getLocationString() {
        return file.getName();
    }

    @Override
    public PsiElement getParent() {
        return file;
    }

    @Override
    public boolean isValid() {
        return true;
    }

}
