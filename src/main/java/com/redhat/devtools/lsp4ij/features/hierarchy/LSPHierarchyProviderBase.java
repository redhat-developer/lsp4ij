/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.hierarchy;

import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP hierarchy provider base.
 */
public abstract class LSPHierarchyProviderBase implements HierarchyProvider {

    @Override
    public final @Nullable PsiElement getTarget(@NotNull DataContext dataContext) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project == null) {
            return null;
        }

        PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            return null;
        }

        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (editor == null) {
            return null;
        }

        if (ProjectIndexingManager.isIndexingAll()) {
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        TextRange textRange = LSPIJUtils.getWordRangeAt(editor.getDocument(), file, offset);
        if(textRange == null) {
            textRange = new TextRange(offset, offset + 1);
        }
        return new LSPPsiElement(file, textRange);
    }
}
