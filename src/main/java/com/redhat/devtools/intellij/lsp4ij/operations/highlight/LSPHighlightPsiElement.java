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
package com.redhat.devtools.intellij.lsp4ij.operations.highlight;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiElementBase;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement a fake {@link PsiElement} which stores the required text edit (coming from Language server) to highlight.
 *
 * This class provides the capability to highlight part of code by using Language server TextEdit and not by using the PsiElement.
 */
public class LSPHighlightPsiElement extends PsiElementBase  {

    private final TextRange textRange;
    private final DocumentHighlightKind kind;

    public LSPHighlightPsiElement(TextRange textRange, DocumentHighlightKind kind) {
        this.textRange = textRange;
        this.kind = kind;
    }

    public DocumentHighlightKind getKind() {
        return kind;
    }

    @Override
    public @NotNull Language getLanguage() {
        return null;
    }

    @Override
    public PsiElement @NotNull [] getChildren() {
        return new PsiElement[0];
    }

    @Override
    public PsiElement getParent() {
        return null;
    }

    @Override
    public TextRange getTextRange() {
        return textRange;
    }

    @Override
    public int getStartOffsetInParent() {
        return 0;
    }

    @Override
    public int getTextLength() {
        return 0;
    }

    @Override
    public @Nullable PsiElement findElementAt(int offset) {
        return null;
    }

    @Override
    public int getTextOffset() {
        return 0;
    }

    @Override
    public @NlsSafe String getText() {
        return null;
    }

    @Override
    public char @NotNull [] textToCharArray() {
        return new char[0];
    }

    @Override
    public ASTNode getNode() {
        return null;
    }
}
