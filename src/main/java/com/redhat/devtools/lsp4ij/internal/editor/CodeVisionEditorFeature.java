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

import com.intellij.codeInsight.codeVision.CodeVisionHost;
import com.intellij.codeInsight.hints.codeVision.ModificationStampUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.features.codeLens.DummyCodeVisionProviderFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Code vision feature to refresh IntelliJ code vision (even if Psi file doesn't change).
 */
@ApiStatus.Internal
public class CodeVisionEditorFeature implements EditorFeature {

    @Override
    public EditorFeatureType getFeatureType() {
        return EditorFeatureType.CODE_VISION;
    }

    @Override
    public void clearEditorCache(@NotNull Editor editor, @NotNull Project project) {
        // Clear the modification stamp stored in the editor user-data
        // (with the key PSI_MODIFICATION_STAMP)
        // to refresh the code vision even if Psi file has not changed
        // see https://github.com/JetBrains/intellij-community/blob/7c8933354e46a99e1f41022aaa6552d2c0455eec/platform/lang-impl/src/com/intellij/codeInsight/hints/codeVision/CodeVisionPassFactory.kt#L32
        ModificationStampUtil.INSTANCE.clearModificationStamp(editor);
    }

    @Override
    public void clearLSPCache(PsiFile file) {
        // Evict the cache of LSP requests from codeLens support
        var fileSupport = LSPFileSupport.getSupport(file);
        fileSupport.getCodeLensSupport().cancel();
    }

    @Override
    public void collectUiRunnable(@NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull List<Runnable> runnableList) {
        Runnable runnable = () -> {
            // Get the IntelliJ code vision host and fire an event to refresh only LSP code vision
            var codeVisionHost = file.getProject().getService(CodeVisionHost.class);
            codeVisionHost.getInvalidateProviderSignal().fire(new CodeVisionHost.LensInvalidateSignal(editor, DummyCodeVisionProviderFactory.LSP_CODE_VISION_PROVIDER_IDS));
        };
        runnableList.add(runnable);
    }
}
