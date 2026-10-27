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
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsApplier;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsPassFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * LSP diagnostics feature to refresh diagnostics when server starts/stops.
 *
 * <p>Diagnostics are reactive (applied via {@link LSPDiagnosticsApplier} when
 * publishDiagnostics arrives). This feature clears the cache to trigger refresh.</p>
 */
@ApiStatus.Internal
public class LSPDiagnosticsEditorFeature implements EditorFeature {

    @Override
    public EditorFeatureType getFeatureType() {
        return EditorFeatureType.LSP_DIAGNOSTICS;
    }

    @Override
    public void clearEditorCache(@NotNull Editor editor, @NotNull Project project) {
    }

    @Override
    public void clearLSPCache(PsiFile file) {
        // Diagnostics cache is managed by OpenedDocument
        // Will be refreshed when server sends new publishDiagnostics
    }

    @Override
    public void collectUiRunnable(@NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull List<Runnable> runnableList) {
        // Trigger reactive refresh
        Runnable runnable = () -> {
            LSPDiagnosticsApplier.getInstance(file.getProject())
                    .scheduleRefresh(file, editor.getDocument());
        };
        runnableList.add(runnable);
    }
}
