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
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating {@link LSPDiagnosticsPass} instances.
 *
 * <p>In normal editor mode, diagnostics are applied ONLY via {@link LSPDiagnosticsApplier}
 * (reactive when server sends publishDiagnostics). The pass is used ONLY for batch analysis
 * ("Inspect Code") to avoid flicker when typing.</p>
 */
public class LSPDiagnosticsPassFactory implements MainHighlightingPassFactory,
        TextEditorHighlightingPassFactory,
        TextEditorHighlightingPassFactoryRegistrar,
        DumbAware {

    @Override
    public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar,
                                                @NotNull Project project) {
        int passId = registrar.registerTextEditorHighlightingPass(
                this,
                null,
                new int[]{Pass.UPDATE_ALL},
                false,
                -1
        );
        LSPDiagnosticsApplier.GROUP_ID = passId;
    }

    @Nullable
    @Override
    public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
        // In normal editor mode, diagnostics are applied by LSPDiagnosticsApplier (reactive)
        return null;
    }

    @Nullable
    @Override
    public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                                 @NotNull Document document,
                                                                 @NotNull HighlightInfoProcessor highlightInfoProcessor) {
        // For batch analysis (e.g., "Inspect Code")
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
            return null;
        }
        return new LSPDiagnosticsPass(file, document);
    }

}
