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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
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
        LSPDocumentSymbolSupport documentSymbolSupport = LSPFileSupport.getSupport(psiFile).getDocumentSymbolSupport();
        documentSymbolSupport.cancel();
    }

    static class LSPFileStructureViewElement extends PsiTreeElementBase<PsiFile> {

        public LSPFileStructureViewElement(@NotNull PsiFile psiFile) {
            super(psiFile);
        }

        @Override
        public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
            return collectElements(getElement());
        }

        private @NotNull Collection<StructureViewTreeElement> collectElements(@NotNull PsiFile psiFile) {
            LSPDocumentSymbolSupport documentSymbolSupport = LSPFileSupport.getSupport(psiFile).getDocumentSymbolSupport();
            var params = new DocumentSymbolParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()));
            var documentSymbolFuture = documentSymbolSupport.getDocumentSymbols(params);
            try {
                waitUntilDone(documentSymbolFuture, psiFile);
            } catch (
                    ProcessCanceledException e) { //Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
                documentSymbolSupport.cancel();
                throw e;
            } catch (CancellationException e) {
                documentSymbolSupport.cancel();
                return Collections.emptyList();
            } catch (ExecutionException e) {
                return Collections.emptyList();
            }

            if (isDoneNormally(documentSymbolFuture)) {
                var documentSymbols = documentSymbolFuture.getNow(null);
                if (documentSymbols == null) {
                    return Collections.emptyList();
                }
                return documentSymbols.stream()
                        .map(documentSymbol -> getStructureViewTreeElement(documentSymbol))
                        .filter(Objects::nonNull)
                        .toList();
            }
            return Collections.emptyList();
        }

        @Override
        public @Nullable String getPresentableText() {
            return getElement().getName();
        }

        @Override
        public @Nullable String getLocationString() {
            return getElement().getVirtualFile().getCanonicalPath();
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
            return getElement().getFileType().getIcon();
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
            var children = documentSymbolData.getChildren();
            if (children.length == 0) {
                return Collections.emptyList();
            }
            return Stream.of(children)
                    .map(child -> getStructureViewTreeElement(child))
                    .filter(Objects::nonNull)
                    .toList();
        }

        @Override
        public @Nullable String getPresentableText() {
            return getElement().getPresentableText();
        }
    }

    private static @Nullable StructureViewTreeElement getStructureViewTreeElement(DocumentSymbolData documentSymbol) {
        return documentSymbol.getClientFeatures().getDocumentSymbolFeature().getStructureViewTreeElement(documentSymbol);
    }

}