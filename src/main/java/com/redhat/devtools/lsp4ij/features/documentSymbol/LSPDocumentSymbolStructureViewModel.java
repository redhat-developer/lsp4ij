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

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.internal.PsiFileChangedException;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP document symbol structure view model.
 */
public class LSPDocumentSymbolStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {

    private final PsiFile psiFile;

    public LSPDocumentSymbolStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
        super(psiFile, editor, new LSPFileStructureViewElement(psiFile));
        this.psiFile = psiFile;
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

            LSPFileSupport fileSupport = LSPFileSupport.getSupport(psiFile);
            LSPDocumentSymbolSupport documentSymbolSupport = fileSupport.getDocumentSymbolSupport();
            var params = new DocumentSymbolParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()));
            var documentSymbolFuture = documentSymbolSupport.getDocumentSymbols(params);
            try {
                waitUntilDone(documentSymbolFuture, psiFile);
            } catch (
                    PsiFileChangedException e) { //Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
                documentSymbolSupport.cancel();
                throw e;
            } catch (CancellationException e) {
                //documentSymbolSupport.cancel();
                throw e;
            } catch (ExecutionException e) {
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