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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Semantic tokens editor feature.
 */
@ApiStatus.Internal
public class SemanticTokensEditorFeature implements EditorFeature {

    @Override
    public EditorFeatureType getFeatureType() {
        return EditorFeatureType.SEMANTIC_TOKENS;
    }

    @Override
    public void clearEditorCache(@NotNull Editor editor, @NotNull Project project) {
        // Semantic tokens don't use editor-specific cache
        // Refresh is handled by DaemonCodeAnalyzer
    }

    @Override
    public void clearLSPCache(PsiFile file) {
        // Evict the cache of LSP requests from semantic tokens support
        var fileSupport = LSPFileSupport.getSupport(file);
        fileSupport.getSemanticTokensSupport().cancel();
    }

    @Override
    public void collectUiRunnable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull List<Runnable> runnableList) {
        // Semantic tokens refresh is handled by DaemonCodeAnalyzer restart
        // No additional UI runnable needed
    }
}
