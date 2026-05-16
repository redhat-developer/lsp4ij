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
package com.redhat.devtools.lsp4ij.internal.editor;

import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inlay hints feature to refresh IntelliJ inlay hints (even if Psi file doesn't change).
 */
@ApiStatus.Internal
public class InlayHintsEditorFeature implements EditorFeature {

    @Override
    public EditorFeatureType getFeatureType() {
        return EditorFeatureType.INLAY_HINT;
    }

    @Override
    public void clearEditorCache(@NotNull Editor editor, @NotNull Project project) {
        InlayHintsPassFactoryInternal.Companion.clearModificationStamp(editor);
    }

    @Override
    public void clearLSPCache(PsiFile file) {
        // Evict the cache of LSP requests from color support
        var fileSupport = LSPFileSupport.getSupport(file);
        fileSupport.getColorSupport().cancel();
    }

    @Override
    public void collectUiRunnable(@NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull List<Runnable> runnableList) {
        // Do nothing
    }


}
