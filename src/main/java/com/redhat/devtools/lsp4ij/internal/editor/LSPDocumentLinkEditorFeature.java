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
import com.redhat.devtools.lsp4ij.features.documentLink.LSPDocumentLinkApplier;
import com.redhat.devtools.lsp4ij.features.documentLink.LSPDocumentLinkPassFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * LSP document link feature to refresh document links when server starts/stops.
 *
 * <p>Document links are reactive (applied via {@link LSPDocumentLinkApplier}).</p>
 */
@ApiStatus.Internal
public class LSPDocumentLinkEditorFeature implements EditorFeature {

    @Override
    public EditorFeatureType getFeatureType() {
        return EditorFeatureType.LSP_DOCUMENT_LINK;
    }

    @Override
    public void clearEditorCache(@NotNull Editor editor, @NotNull Project project) {
        // Reset PSI modification stamp to allow reactive refresh
        LSPDocumentLinkPassFactory.resetModificationStamp(editor);
    }

    @Override
    public void clearLSPCache(PsiFile file) {
        // Evict document link cache to force refresh when server restarts
        LSPFileSupport.getSupport(file).getDocumentLinkSupport().cancel();
    }

    @Override
    public void collectUiRunnable(@NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull List<Runnable> runnableList) {
        // Trigger reactive refresh
        Runnable runnable = () -> {
            LSPDocumentLinkApplier.getInstance(file.getProject())
                    .scheduleRefresh(file.getVirtualFile());
        };
        runnableList.add(runnable);
    }
}
