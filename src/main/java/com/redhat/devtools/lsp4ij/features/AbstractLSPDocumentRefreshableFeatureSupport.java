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
package com.redhat.devtools.lsp4ij.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.PsiFileCancelChecker;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Base class to consume LSP requests (ex : textDocument/codeLens) from all language servers applying to a given Psi file
 * and refresh editor feature type.
 *
 * @param <Params> the LSP requests parameters (ex : CodelensParams).
 * @param <Result> the LSP response results (ex : List<CodeLensData>).
 */
public abstract class AbstractLSPDocumentRefreshableFeatureSupport<Params, Result> extends AbstractLSPDocumentFeatureSupport<Params, Result> {

    private final @NotNull EditorFeatureType featureType;

    private @Nullable CompletableFuture<Void> refreshEditorFeatureFuture;

    public AbstractLSPDocumentRefreshableFeatureSupport(@NotNull PsiFile file,
                                                        @NotNull EditorFeatureType featureType) {
        super(file);
        this.featureType = featureType;
    }

    /**
     * Refresh the editor feature (ex: IJ InlayHint, declarative inlay hint, etc) when LSP request data are ready.
     */
    public void refreshEditorFeatureWhenReady() {
        var future = getValidLSPFuture();
        if (refreshEditorFeatureFuture != null || future == null) {
            return;
        }
        var file = getFile();
        refreshEditorFeatureFuture =
                EditorFeatureManager.getInstance(file.getProject())
                        .refreshEditorFeatureWhenAllDone(future,
                                file,
                                featureType,
                                new PsiFileCancelChecker(file));
    }

    @Override
    public void cancel() {
        super.cancel();
        CancellationSupport.cancel(refreshEditorFeatureFuture);
    }

}
