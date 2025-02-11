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

import javax.swing.Icon;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;

import org.eclipse.lsp4j.DocumentSymbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * LSP document symbol data.
 */
public class DocumentSymbolData extends FakePsiElement {

    private static final DocumentSymbolData[] EMPTY_ARRAY = new DocumentSymbolData[0];

    private final @NotNull DocumentSymbol documentSymbol;
    private final @NotNull PsiFile psiFile;
    private final DocumentSymbolData parent;
    private final @NotNull LanguageServerItem languageServer;
    private TextRange textRange;
    private DocumentSymbolData[] cachedChildren;

    public DocumentSymbolData(@NotNull DocumentSymbol documentSymbol,
                              @NotNull PsiFile psiFile,
                              @NotNull LanguageServerItem languageServer) {
        this(documentSymbol, psiFile, languageServer, null);
    }

    public DocumentSymbolData(@NotNull DocumentSymbol documentSymbol,
                              @NotNull PsiFile psiFile,
                              @NotNull LanguageServerItem languageServer,
                              @Nullable DocumentSymbolData parent) {
        this.documentSymbol = documentSymbol;
        this.psiFile = psiFile;
        this.languageServer = languageServer;
        this.parent = parent;
    }

    public @NotNull DocumentSymbol getDocumentSymbol() {
        return documentSymbol;
    }

    @Override
    public @Nullable String getPresentableText() {
        return getClientFeatures().getDocumentSymbolFeature().getPresentableText(documentSymbol, psiFile);
    }

    @Override
    public @Nullable Icon getIcon(boolean unused) {
        return getClientFeatures().getDocumentSymbolFeature().getIcon(documentSymbol, psiFile, unused);
    }

    @Override
    public @Nullable String getLocationString() {
        return getClientFeatures().getDocumentSymbolFeature().getLocationString(documentSymbol, psiFile);
    }

    @Override
    public int getTextOffset() {
        return getClientFeatures().getDocumentSymbolFeature().getTextOffset(documentSymbol, psiFile);
    }

    @Override
    public TextRange getTextRange() {
        if (textRange == null) {
            this.textRange = LSPIJUtils.toTextRange(documentSymbol.getRange(), LSPIJUtils.getDocument(psiFile.getVirtualFile()));
        }
        return textRange;
    }

    @Override
    public void navigate(boolean requestFocus) {
        getClientFeatures().getDocumentSymbolFeature().navigate(documentSymbol, psiFile, requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return getClientFeatures().getDocumentSymbolFeature().canNavigate(documentSymbol, psiFile);
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
                    .map(child -> new DocumentSymbolData(child, psiFile,  languageServer,this))
                    .toArray(DocumentSymbolData[]::new);
        }
        return cachedChildren;
    }

    public @NotNull LSPClientFeatures getClientFeatures() {
        return languageServer.getClientFeatures();
    }

    @Override
    public @NotNull Project getProject() {
        return psiFile.getProject();
    }
}
