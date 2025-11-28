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
package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Objects;


/**
 * LSP document symbol data.
 */
public class DocumentSymbolData extends FakePsiElement {

    private static final DocumentSymbolData[] EMPTY_ARRAY = new DocumentSymbolData[0];

    private final @NotNull DocumentSymbol documentSymbol;
    private final @NotNull PsiFile psiFile;
    private final DocumentSymbolData parent;
    private final @NotNull LanguageServerItem languageServer;
    private volatile TextRange textRange = null;
    private volatile TextRange selectionTextRange = null;
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
            synchronized (this) {
                if (textRange == null) {
                    Range range = documentSymbol.getRange();
                    Document document = LSPIJUtils.getDocument(psiFile);
                    this.textRange = (range != null) && (document != null) ? LSPIJUtils.toTextRange(range, document) : psiFile.getTextRange();
                }
            }
        }
        return textRange;
    }

    public @Nullable TextRange getSelectionTextRange() {
        if (documentSymbol.getSelectionRange() == null) {
            return null;
        }
        if (selectionTextRange == null) {
            synchronized (this) {
                if (selectionTextRange == null) {
                    Range range = documentSymbol.getSelectionRange();
                    Document document = LSPIJUtils.getDocument(psiFile);
                    this.selectionTextRange = (document != null) ? LSPIJUtils.toTextRange(range, document) : null;
                }
            }
        }
        return selectionTextRange;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (getClass() == o.getClass()) {
            DocumentSymbolData that = (DocumentSymbolData) o;
            return Objects.equals(documentSymbol, that.documentSymbol);
        }
        if (o instanceof PsiElement psiElement) {
            return getClientFeatures().getDocumentSymbolFeature().matchesPsiElement(this, psiElement);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(documentSymbol);
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
