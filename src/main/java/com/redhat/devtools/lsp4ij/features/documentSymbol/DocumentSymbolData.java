/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * CppCXY
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.eclipse.lsp4j.DocumentSymbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


/**
 * LSP document symbol data.
 */
class DocumentSymbolData extends FakePsiElement {

    private static final DocumentSymbolData[] EMPTY_ARRAY = new DocumentSymbolData[0];

    private final @NotNull DocumentSymbol documentSymbol;
    private final @NotNull PsiFile psiFile;
    private final DocumentSymbolData parent;
    private DocumentSymbolData[] cachedChildren;

    public DocumentSymbolData(@NotNull DocumentSymbol documentSymbol,
                              @NotNull PsiFile psiFile) {
        this(documentSymbol, psiFile,null);
    }

    public DocumentSymbolData(@NotNull DocumentSymbol documentSymbol,
                              @NotNull PsiFile psiFile,
                              @Nullable DocumentSymbolData parent) {
        this.documentSymbol = documentSymbol;
        this.psiFile = psiFile;
        this.parent = parent;
    }

    private @NotNull DocumentSymbol getDocumentSymbol() {
        return documentSymbol;
    }

    @Override
    public @Nullable String getPresentableText() {
        return documentSymbol.getName();
    }

    @Override
    public @Nullable Icon getIcon(boolean unused) {
        return IconMapper.getIcon(documentSymbol.getKind());
    }

    @Override
    public @Nullable String getLocationString() {
        return documentSymbol.getDetail();
    }

    @Override
    public void navigate(boolean requestFocus) {
        var selectionRange = getDocumentSymbol().getSelectionRange();
        LSPIJUtils.openInEditor(psiFile.getVirtualFile(), selectionRange.getStart(), psiFile.getProject());
    }

    @Override
    public boolean canNavigate() {
        var selectionRange = getDocumentSymbol().getSelectionRange();
        return selectionRange != null && selectionRange.getStart() != null;
    }

    @Override
    public PsiElement /* PsiFile || DocumentSymbolData */ getParent() {
        return parent != null ? parent : psiFile;
    }

    @Override
    public DocumentSymbolData @NotNull [] getChildren() {
        var children = getDocumentSymbol().getChildren();
        if (children == null || children.isEmpty()) {
            return DocumentSymbolData.EMPTY_ARRAY;
        }
        if (cachedChildren == null) {
            cachedChildren = children.stream()
                    .map(child -> new DocumentSymbolData(child, psiFile, this))
                    .toArray(DocumentSymbolData[]::new);
        }
        return cachedChildren;
    }

    @Override
    public @NotNull Project getProject() {
        return psiFile.getProject();
    }
}
