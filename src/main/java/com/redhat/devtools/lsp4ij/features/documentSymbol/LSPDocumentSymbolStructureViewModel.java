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

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.documentSymbol.filter.*;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Stream;

import static com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewFactory.isSymbolsSupportedByLanguageServer;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP document symbol structure view model.
 */
public class LSPDocumentSymbolStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {

    public LSPDocumentSymbolStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
        super(psiFile, editor, new LSPFileStructureViewElement(psiFile));
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        return element.getChildren().length > 0;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        return element.getChildren().length == 0;
    }

    @Override
    protected Class @NotNull [] getSuitableClasses() {
        // Any PSI element
        return new Class[]{PsiElement.class};
    }

    /**
     * Finds the acceptable element in the structure view for a given PSI element.
     * <p>
     * This method is called by {@link #getCurrentEditorElement()} to determine which element
     * should be selected in the Structure View based on the cursor position.
     * Unlike the default implementation that walks up the PSI tree, this method performs
     * a breadth-first search through the DocumentSymbol hierarchy to find the closest
     * containing parent symbol.
     * </p>
     * <p>
     * This enables "Always Select Opened Element" to work properly even when the cursor
     * is positioned at an offset that doesn't correspond exactly to a DocumentSymbol
     * (e.g., whitespace or text between symbols).
     * </p>
     *
     * @param element the PSI element at the cursor position
     * @return the DocumentSymbolData for the closest containing symbol, or null if none found
     */
    @Override
    protected @Nullable Object findAcceptableElement(PsiElement element) {
        if (element == null) {
            return null;
        }

        // If this is already a DocumentSymbolData, return it directly
        if (element instanceof DocumentSymbolData) {
            return element;
        }

        // Find the closest containing DocumentSymbol using breadth-first search
        // This ensures that even if the cursor is not exactly on a symbol boundary,
        // the parent symbol that contains the cursor position will be selected
        return LSPDocumentSymbolUtils.getDocumentSymbolData(element);
    }

    @Override
    public Filter @NotNull [] getFilters() {
        return new Filter[]{
                new HideArraysFilter(),
                new HideBooleansFilter(),
                new HideClassesFilter(),
                new HideConstantsFilter(),
                new HideConstructorsFilter(),
                new HideEnumMembersFilter(),
                new HideEnumsFilter(),
                new HideEventsFilter(),
                new HideFieldsFilter(),
                new HideFilesFilter(),
                new HideFunctionsFilter(),
                new HideInterfacesFilter(),
                new HideKeysFilter(),
                new HideMethodsFilter(),
                new HideModulesFilter(),
                new HideNamespacesFilter(),
                new HideNullsFilter(),
                new HideNumbersFilter(),
                new HideObjectsFilter(),
                new HideOperatorsFilter(),
                new HidePackagesFilter(),
                new HidePropertiesFilter(),
                new HideStringsFilter(),
                new HideStructsFilter(),
                new HideTypeParametersFilter(),
                new HideVariablesFilter()
        };
    }

    @Override
    public void dispose() {
        super.dispose();
        // Do not clear the document symbol cache when the view is disposed.
        // This method is called when switching to another editor.
        // Clearing the cache at this point would cause the document symbols to be reloaded every time the editor is reopened, which is not performance-efficient.
        // The document symbol cache should only be invalidated when the file content changes, not when switching between editors.
        // LSPDocumentSymbolSupport documentSymbolSupport = LSPFileSupport.getSupport(psiFile).getDocumentSymbolSupport();
        // documentSymbolSupport.cancel();
    }

    static class LSPFileStructureViewElement extends PsiTreeElementBase<PsiFile> {

        public LSPFileStructureViewElement(@NotNull PsiFile psiFile) {
            super(psiFile);
        }

        @Override
        public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
            PsiFile file = getElement();
            return file != null ? collectElements(file) : Collections.emptyList();
        }

        private @NotNull Collection<StructureViewTreeElement> collectElements(@NotNull PsiFile psiFile) {
            if (ProjectIndexingManager.isIndexingAll()) {
                return Collections.emptyList();
            }

            if (!LSPFileSupport.hasSupport(psiFile) || !isSymbolsSupportedByLanguageServer(psiFile)) {
                // Don't force file support creation
                // Ex:
                // 1. when file is closed, document symbol must return an empty list.
                // 2. when language servers are stopped, document symbol must return an empty list.
                return Collections.emptyList();
            }


            LSPFileSupport fileSupport = LSPFileSupport.getSupport(psiFile);
            LSPDocumentSymbolSupport documentSymbolSupport = fileSupport.getDocumentSymbolSupport();
            var params = new DocumentSymbolParams(new TextDocumentIdentifier());
            var documentSymbolFuture = documentSymbolSupport.getDocumentSymbols(params);
            try {
                waitUntilDone(documentSymbolFuture, psiFile);
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {
                return Collections.emptyList();
            }

            if (isDoneNormally(documentSymbolFuture)) {
                var documentSymbols = documentSymbolFuture.getNow(null);
                if (documentSymbols == null) {
                    return Collections.emptyList();
                }
                return documentSymbols.stream()
                        .map(LSPDocumentSymbolStructureViewModel::getStructureViewTreeElement)
                        .filter(Objects::nonNull)
                        .toList();
            }
            return Collections.emptyList();
        }

        @Override
        public @Nullable String getPresentableText() {
            PsiFile file = getElement();
            return file != null ? file.getName() : null;
        }

        @Override
        public @Nullable String getLocationString() {
            PsiFile file = getElement();
            return file != null ? file.getVirtualFile().getCanonicalPath() : null;
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
            PsiFile file = getElement();
            return file != null ? file.getFileType().getIcon() : null;
        }
    }

    public static class LSPDocumentSymbolViewElement extends PsiTreeElementBase<DocumentSymbolData> {

        public LSPDocumentSymbolViewElement(DocumentSymbolData documentSymbolData) {
            super(documentSymbolData);
        }

        @Override
        public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
            return collectElements(getElement());
        }

        private @NotNull Collection<StructureViewTreeElement> collectElements(@Nullable DocumentSymbolData documentSymbolData) {
            var children = documentSymbolData != null ? documentSymbolData.getChildren() : null;
            if (ArrayUtil.isEmpty(children)) {
                return Collections.emptyList();
            }
            return Stream.of(children)
                    .map(LSPDocumentSymbolStructureViewModel::getStructureViewTreeElement)
                    .filter(Objects::nonNull)
                    .toList();
        }

        @Override
        public @Nullable String getPresentableText() {
            DocumentSymbolData documentSymbolData = getElement();
            return documentSymbolData != null ? documentSymbolData.getPresentableText() : null;
        }
    }

    private static @Nullable StructureViewTreeElement getStructureViewTreeElement(DocumentSymbolData documentSymbol) {
        return documentSymbol.getClientFeatures().getDocumentSymbolFeature().getStructureViewTreeElement(documentSymbol);
    }
}