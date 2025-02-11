/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base class for 'extendWordSelectionHandler' implementations for LSP4IJ files.
 */
abstract class AbstractLSPExtendWordSelectionHandler implements ExtendWordSelectionHandler {

    @Override
    public boolean canSelect(@NotNull PsiElement element) {
        if (!element.isValid()) {
            return false;
        }

        PsiFile file = element.getContainingFile();
        if ((file == null) || !file.isValid()) {
            return false;
        }

        Project project = file.getProject();
        if (project.isDisposed()) {
            return false;
        }

        VirtualFile virtualFile = file.getVirtualFile();
        if ((virtualFile == null) || !virtualFile.isValid()) {
            return false;
        }

        return LanguageServersRegistry.getInstance().isFileSupported(file);
    }
}
