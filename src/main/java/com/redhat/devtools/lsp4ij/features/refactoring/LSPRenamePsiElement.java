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
package com.redhat.devtools.lsp4ij.features.refactoring;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class LSPRenamePsiElement extends LSPPsiElement {
    private final TextEdit textEdit;

    public LSPRenamePsiElement(@NotNull PsiFile file, @NotNull TextRange textRange, TextEdit textEdit) {
        super(file, textRange);
        this.textEdit = textEdit;
    }

    public void rename() {
        Document document = LSPIJUtils.getDocument(getContainingFile().getVirtualFile());
        Editor[] editors = LSPIJUtils.editorsForFile(getContainingFile().getVirtualFile(), getContainingFile().getProject());
        LSPIJUtils.applyEdits(editors.length > 0 ? editors[0] : null, document, Arrays.asList(textEdit));
    }
}