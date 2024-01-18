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
package com.redhat.devtools.lsp4ij.operations.signatureHelp;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.operations.LSPPsiElement;
import org.eclipse.lsp4j.SignatureHelp;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * LSP {@link PsiElement} implementation which stores the file
 * and the offset where the signature help has been invoked in an editor.
 */
public class LSPSignatureHelperPsiElement extends LSPPsiElement {

    private SignatureHelp activeSignatureHelp;
    public LSPSignatureHelperPsiElement(@NotNull PsiFile file, @NotNull TextRange textRange) {
        super(file, textRange);
    }

    public SignatureHelp getActiveSignatureHelp() {
        return activeSignatureHelp;
    }

    public void setActiveSignatureHelp(SignatureHelp activeSignatureHelp) {
        this.activeSignatureHelp = activeSignatureHelp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LSPSignatureHelperPsiElement that = (LSPSignatureHelperPsiElement) o;
        return Objects.equals(getContainingFile(), that.getContainingFile());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getContainingFile());
    }
}