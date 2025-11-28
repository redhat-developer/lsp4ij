/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.documentSymbol.DocumentSymbolData;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewModel;
import com.redhat.devtools.lsp4ij.server.capabilities.DocumentSymbolCapabilityRegistry;
import com.redhat.devtools.lsp4ij.ui.IconMapper;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * LSP documentSymbol feature.
 */
@ApiStatus.Experimental
public class LSPDocumentSymbolFeature extends AbstractLSPDocumentFeature {

    private DocumentSymbolCapabilityRegistry documentSymbolCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isDocumentSymbolSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support codelens and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support codelens and false otherwise.
     */
    public boolean isDocumentSymbolSupported(@NotNull PsiFile file) {
        return getDocumentSymbolCapabilityRegistry().isDocumentSymbolSupported(file);
    }

    public DocumentSymbolCapabilityRegistry getDocumentSymbolCapabilityRegistry() {
        if (documentSymbolCapabilityRegistry == null) {
            initDocumentSymbolCapabilityRegistry();
        }
        return documentSymbolCapabilityRegistry;
    }

    private synchronized void initDocumentSymbolCapabilityRegistry() {
        if (documentSymbolCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        documentSymbolCapabilityRegistry = new DocumentSymbolCapabilityRegistry(clientFeatures);
        documentSymbolCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (documentSymbolCapabilityRegistry != null) {
            documentSymbolCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Creates a StructureViewTreeElement for a given document symbol.
     *
     * @param documentSymbol the document symbol data
     * @return the tree element, or null if unavailable
     */
    @Nullable
    public StructureViewTreeElement getStructureViewTreeElement(@NotNull DocumentSymbolData documentSymbol) {
        return new LSPDocumentSymbolStructureViewModel.LSPDocumentSymbolViewElement(documentSymbol);
    }

    /**
     * Returns the display text for the given document symbol.
     *
     * @param documentSymbol the symbol
     * @param psiFile the associated PSI file
     * @return the presentable text, usually the symbol's name
     */
    public @Nullable String getPresentableText(@NotNull DocumentSymbol documentSymbol,
                                               @NotNull PsiFile psiFile) {
        return documentSymbol.getName();
    }

    /**
     * Returns the icon for a document symbol based on its kind.
     *
     * @param documentSymbol the symbol
     * @param psiFile the associated PSI file
     * @param unused unused parameter
     * @return the icon or null
     */
    public @Nullable Icon getIcon(@NotNull DocumentSymbol documentSymbol,
                                  @NotNull PsiFile psiFile,
                                  boolean unused) {
        return IconMapper.getIcon(documentSymbol.getKind());
    }

    /**
     * Returns the location string for a document symbol, typically used in Structure View.
     *
     * @param documentSymbol the symbol
     * @param psiFile the associated PSI file
     * @return the location string (detail) or null
     */
    public @Nullable String getLocationString(@NotNull DocumentSymbol documentSymbol,
                                              @NotNull PsiFile psiFile) {
        return documentSymbol.getDetail();
    }

    /**
     * Finds the text offset of the symbol as the start of its selection range if available and range if not.
     *
     * @param documentSymbol the document symbol
     * @param psiFile        the file
     * @return the start of the symbol's selection range if valid; otherwise 0
     */
    public int getTextOffset(@NotNull DocumentSymbol documentSymbol,
                             @NotNull PsiFile psiFile) {
        // NOTE: This works quite well without having to implement MethodNavigationOffsetProvider
        Range selectionRange = documentSymbol.getSelectionRange();
        Range range = documentSymbol.getRange();
        Position startPosition = selectionRange != null ? selectionRange.getStart() : range.getStart();
        Document document = LSPIJUtils.getDocument(psiFile.getVirtualFile());
        if ((startPosition != null) && (document != null)) {
            return LSPIJUtils.toOffset(startPosition, document);
        }
        return 0;
    }

    /**
     * Navigates to the symbol in the editor.
     *
     * @param documentSymbol the symbol
     * @param psiFile the associated PSI file
     * @param requestFocus whether to request editor focus
     */
    public void navigate(@NotNull DocumentSymbol documentSymbol,
                         @NotNull PsiFile psiFile,
                         boolean requestFocus) {
        var selectionRange = documentSymbol.getSelectionRange();
        LSPIJUtils.openInEditor(psiFile.getVirtualFile(), selectionRange.getStart(), requestFocus, psiFile.getProject());
    }

    /**
     * Checks if a symbol can be navigated to (has a valid selection start).
     *
     * @param documentSymbol the symbol
     * @param psiFile the associated PSI file
     * @return true if navigation is possible
     */
    public boolean canNavigate(@NotNull DocumentSymbol documentSymbol,
                               @NotNull PsiFile psiFile) {
        var selectionRange = documentSymbol.getSelectionRange();
        return selectionRange != null && selectionRange.getStart() != null;
    }

    /**
     * Checks if the given {@link DocumentSymbolData} corresponds to the specified {@link PsiElement}.
     *
     * <p>The comparison is performed using the text ranges:
     * it uses the selection range if available, otherwise the full symbol range.
     * If the ranges are equal, the document symbol is considered to match the PSI element.</p>
     *
     * @param documentSymbolData the document symbol data
     * @param psiElement the PSI element to compare with
     * @return true if the document symbol represents the given PSI element (text ranges match), false otherwise
     */
    public boolean matchesPsiElement(@NotNull DocumentSymbolData documentSymbolData,
                                     @NotNull PsiElement psiElement) {
        TextRange textRange = documentSymbolData.getSelectionTextRange() != null
                ? documentSymbolData.getSelectionTextRange()
                : documentSymbolData.getTextRange();
        return textRange.equals(psiElement.getTextRange());
    }
}
