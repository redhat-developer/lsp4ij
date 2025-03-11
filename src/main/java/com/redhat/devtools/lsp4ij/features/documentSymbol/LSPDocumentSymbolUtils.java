/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewModel.LSPDocumentSymbolViewElement;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewModel.LSPFileStructureViewElement;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokensFileViewProvider;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * Utility class for working with document symbols.
 */
final class LSPDocumentSymbolUtils {

    private LSPDocumentSymbolUtils() {
        // Pure utility class
    }

    /**
     * Returns the structure view model for the provided element.
     *
     * @param element the element
     * @return the structure view model for the element, or null if none was found
     */
    @Nullable
    static LSPDocumentSymbolStructureViewModel getStructureViewModel(@NotNull PsiElement element) {
        Editor editor = LSPIJUtils.editorForElement(element);
        if (editor != null) {
            PsiFile file = element.getContainingFile();
            return new LSPDocumentSymbolStructureViewModel(file, editor);
        }

        return null;
    }

    /**
     * Returns the closest containing document symbol data for the element.
     *
     * @param element the element
     * @return the closest containing document symbol data, or null if none was found
     */
    @Nullable
    static DocumentSymbolData getDocumentSymbolData(@NotNull PsiElement element) {
        LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider = LSPSemanticTokensFileViewProvider.getInstance(element);
        int effectiveOffset = semanticTokensFileViewProvider != null ? semanticTokensFileViewProvider.getEffectiveOffset(element) : -1;
        int offset = effectiveOffset > -1 ? effectiveOffset : element.getTextOffset();
        return getDocumentSymbolData(element, offset);
    }

    @Nullable
    private static DocumentSymbolData getDocumentSymbolData(@NotNull PsiElement element, int offset) {
        if (element instanceof DocumentSymbolData documentSymbolData) {
            return documentSymbolData;
        }

        LSPDocumentSymbolStructureViewModel structureViewModel = getStructureViewModel(element);
        if (structureViewModel != null) {
            PsiFile file = element.getContainingFile();
            List<DocumentSymbolData> containingDocumentSymbolDatas = getContainingDocumentSymbolDatas(file, structureViewModel.getRoot(), offset);
            // Breadth-first search, so the last one is the closest one
            return ContainerUtil.getLastItem(containingDocumentSymbolDatas);
        }

        return null;
    }

    @NotNull
    private static List<DocumentSymbolData> getContainingDocumentSymbolDatas(@NotNull PsiFile file,
                                                                             @NotNull StructureViewTreeElement structureViewTreeElement,
                                                                             int offset) {
        List<DocumentSymbolData> containingDocumentSymbolDatas = new LinkedList<>();

        // If this is file-level, collect its children/descendants
        if (structureViewTreeElement instanceof LSPFileStructureViewElement fileStructureViewElement) {
            for (StructureViewTreeElement child : fileStructureViewElement.getChildren()) {
                ContainerUtil.addAllNotNull(containingDocumentSymbolDatas, getContainingDocumentSymbolDatas(file, child, offset));
            }
        }

        // Otherwise add document symbol datas that contain the offset in a breadth-first manner so that the last one is
        // the closest one for the offset
        else if (structureViewTreeElement instanceof LSPDocumentSymbolViewElement documentSymbolViewElement) {
            DocumentSymbolData documentSymbolData = documentSymbolViewElement.getElement();
            DocumentSymbol documentSymbol = documentSymbolData != null ? documentSymbolData.getDocumentSymbol() : null;
            Range documentSymbolRange = documentSymbol != null ? documentSymbol.getRange() : null;
            Document document = documentSymbolRange != null ? LSPIJUtils.getDocument(file) : null;
            TextRange textRange = document != null ? LSPIJUtils.toTextRange(documentSymbolRange, document) : null;
            if ((textRange != null) && textRange.containsOffset(offset)) {
                // Add this one
                containingDocumentSymbolDatas.add(documentSymbolData);

                // And all children/descendants that also contain the offset
                for (StructureViewTreeElement child : documentSymbolViewElement.getChildren()) {
                    ContainerUtil.addAllNotNull(containingDocumentSymbolDatas, getContainingDocumentSymbolDatas(file, child, offset));
                }
            }
        }

        return containingDocumentSymbolDatas;
    }
}
