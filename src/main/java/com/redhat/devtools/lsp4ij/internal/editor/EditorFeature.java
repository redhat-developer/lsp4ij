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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The editor feature.
 */
@ApiStatus.Internal
public interface EditorFeature {

    /**
     * Returns the editor feature type.
     *
     * @return the editor feature type.
     */
    EditorFeatureType getFeatureType();

    /**
     * Clear notification stamp from the given editor.
     *
     * @param editor the editor.
     */
    void clearEditorCache(@NotNull Editor editor, @NotNull Project project);

    /**
     * Clear LSP data cache (ex : LSP CodeLens).
     *
     * @param file the Psi file.
     */
    void clearLSPCache(PsiFile file);

    /**
     * Collect runnable which must be executed on UI step.
     *
     * @param editor       the editor to refresh.
     * @param file         the file edited
     * @param runnableList the Ui runnable list.
     */
    void collectUiRunnable(@NotNull Editor editor,
                           @NotNull PsiFile file,
                           @NotNull List<Runnable> runnableList);
}
