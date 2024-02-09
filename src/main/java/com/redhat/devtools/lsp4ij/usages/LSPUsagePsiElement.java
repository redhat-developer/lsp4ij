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
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.operations.LSPPsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * LSP usage Psi element.
 */
public class LSPUsagePsiElement extends LSPPsiElement {

    private final UsageKind kind;

    public static enum UsageKind {
        declarations,
        definitions,
        typeDefinitions,
        references,
        implementations;
    }

    public LSPUsagePsiElement(@NotNull PsiFile file, @NotNull TextRange textRange, @NotNull UsageKind kind) {
        super(file, textRange);
        this.kind = kind;
    }

    /**
     * Returns the usage kind (references, implementations, etc)
     *
     * @return the usage kind (references, implementations, etc)
     */
    public UsageKind getKind() {
        return kind;
    }
}
