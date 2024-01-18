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
package com.redhat.devtools.lsp4ij.operations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for LSP Psi element.
 */
public class LSPPsiElement extends FakePsiElement {

    private final @NotNull PsiFile file;

    private @NotNull TextRange textRange;

    public LSPPsiElement(@NotNull PsiFile file, @NotNull TextRange textRange) {
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

    @Nullable
    @Override
    public TextRange getTextRange() {
        return textRange;
    }

    public void setTextRange(@NotNull TextRange textRange) {
        this.textRange = textRange;
    }

    @Override
    public PsiElement getParent() {
        return null;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
