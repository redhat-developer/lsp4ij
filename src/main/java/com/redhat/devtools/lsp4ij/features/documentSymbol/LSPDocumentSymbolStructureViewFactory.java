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

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.ProjectIndexingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP document symbol structure view factory.
 */
public class LSPDocumentSymbolStructureViewFactory implements PsiStructureViewFactory {

    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        if (ProjectIndexingManager.canExecuteLSPFeature(psiFile) != ExecuteLSPFeatureStatus.NOW) {
            return null;
        }
        return new LSPDocumentSymbolStructureViewBuilder(psiFile);
    }

    private static class LSPDocumentSymbolStructureViewBuilder extends TreeBasedStructureViewBuilder {
        private final PsiFile psiFile;

        public LSPDocumentSymbolStructureViewBuilder(@NotNull PsiFile psiFile) {
            this.psiFile = psiFile;
        }

        @Override
        public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
            return new LSPDocumentSymbolStructureViewModel(psiFile, editor);
        }
    }
}
