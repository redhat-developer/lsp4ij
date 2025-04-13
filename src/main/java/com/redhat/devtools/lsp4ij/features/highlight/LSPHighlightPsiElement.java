/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.features.highlight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.jetbrains.annotations.NotNull;

/**
 * Implement a fake {@link PsiElement} which stores the required text edit (coming from Language server) to highlight.
 * <p>
 * This class provides the capability to highlight part of code by using Language server TextEdit and not by using the PsiElement.
 */
public class LSPHighlightPsiElement extends LSPPsiElement {

    private final @NotNull DocumentHighlightKind kind;

    public LSPHighlightPsiElement(@NotNull PsiFile file, @NotNull TextRange textRange, @NotNull DocumentHighlightKind kind) {
        super(file, textRange);
        this.kind = kind;
    }

    public @NotNull DocumentHighlightKind getKind() {
        return kind;
    }

}
