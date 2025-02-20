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

    @Nullable
    public StructureViewTreeElement getStructureViewTreeElement(@NotNull DocumentSymbolData documentSymbol) {
        return new LSPDocumentSymbolStructureViewModel.LSPDocumentSymbolViewElement(documentSymbol);
    }

    public @Nullable String getPresentableText(@NotNull DocumentSymbol documentSymbol,
                                               @NotNull PsiFile psiFile) {
        return documentSymbol.getName();
    }

    public @Nullable Icon getIcon(@NotNull DocumentSymbol documentSymbol,
                                  @NotNull PsiFile psiFile,
                                  boolean unused) {
        return IconMapper.getIcon(documentSymbol.getKind());
    }

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

    public void navigate(@NotNull DocumentSymbol documentSymbol,
                         @NotNull PsiFile psiFile,
                         boolean requestFocus) {
        var selectionRange = documentSymbol.getSelectionRange();
        LSPIJUtils.openInEditor(psiFile.getVirtualFile(), selectionRange.getStart(), requestFocus, psiFile.getProject());
    }

    public boolean canNavigate(@NotNull DocumentSymbol documentSymbol,
                               @NotNull PsiFile psiFile) {
        var selectionRange = documentSymbol.getSelectionRange();
        return selectionRange != null && selectionRange.getStart() != null;
    }
}
