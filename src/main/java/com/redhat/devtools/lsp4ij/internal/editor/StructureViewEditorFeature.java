/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.editor;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Structure view editor feature to refresh IntelliJ structure view when language servers start/stop.
 */
@ApiStatus.Internal
public class StructureViewEditorFeature implements EditorFeature {

    @Override
    public EditorFeatureType getFeatureType() {
        return EditorFeatureType.STRUCTURE_VIEW;
    }

    @Override
    public boolean shouldProcess(@NotNull PsiFile file) {
        // Structure view should only be refreshed for the currently selected file
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
            VirtualFile[] selectedFiles = FileEditorManager.getInstance(file.getProject()).getSelectedFiles();
            return selectedFiles.length > 0 && selectedFiles[0].equals(virtualFile);
        }
        return false;
    }

    @Override
    public void clearEditorCache(@NotNull Editor editor, @NotNull Project project) {
        // Nothing to clear on editor level for structure view
    }

    @Override
    public void clearLSPCache(PsiFile file) {
        // Invalidate document symbol cache
        // clearCaches() changes the modification stamp which invalidates the LSP future cache
        if (LSPFileSupport.hasSupport(file)) {
            file.clearCaches();
        }
    }

    @Override
    public void collectUiRunnable(@NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull List<Runnable> runnableList) {
        Runnable runnable = () -> {
            // Refresh the structure view
            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED)
                    .run();
        };
        runnableList.add(runnable);
    }
}
