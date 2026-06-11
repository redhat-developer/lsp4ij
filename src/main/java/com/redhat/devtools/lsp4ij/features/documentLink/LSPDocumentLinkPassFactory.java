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
package com.redhat.devtools.lsp4ij.features.documentLink;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating {@link LSPDocumentLinkPass} instances.
 *
 * <p>Document links are applied via this pass in normal mode. If the future takes too long (>500ms),
 * the pass registers a callback with {@link LSPDocumentLinkApplier} to apply them when ready.</p>
 */
public class LSPDocumentLinkPassFactory implements MainHighlightingPassFactory,
                                                    TextEditorHighlightingPassFactory,
                                                    TextEditorHighlightingPassFactoryRegistrar,
                                                    DumbAware {

    /**
     * Key to store PSI modification stamp in editor user data.
     */
    public static final Key<Long> PSI_MODIFICATION_STAMP = Key.create("lsp.documentLink.psi.modification.stamp");

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
        LSPDocumentLinkApplier.GROUP_ID = passId;
    }

    @Nullable
    @Override
    public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
        // In normal editor mode, document links are applied by LSPDocumentLinkApplier (reactive)
        // Skip if PSI hasn't changed to avoid unnecessary pass execution
        long currentStamp = getCurrentModificationStamp(file);
        Long lastStamp = editor.getUserData(PSI_MODIFICATION_STAMP);
        if (lastStamp != null && lastStamp == currentStamp) {
            return null;
        }

        return new LSPDocumentLinkPass(file.getProject(), editor, file.getVirtualFile(), file);
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
        return new LSPDocumentLinkPass(file.getProject(), null, virtualFile, file);
    }

    public static long getCurrentModificationStamp(@NotNull PsiFile file) {
        return file.getManager().getModificationTracker().getModificationCount();
    }

    public static void resetModificationStamp(@NotNull Editor editor) {
        editor.putUserData(PSI_MODIFICATION_STAMP, null);
    }
}
