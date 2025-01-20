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

package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Performs LSP-based on-type formatting if appropriate for the file and typed character. Note that this is not invoked
 * for Enter/Newline; instead see {@link LSPServerSideOnTypeFormattingEnterHandler}.
 */
public class LSPServerSideOnTypeFormattingTypedHandler extends TypedHandlerDelegate {

    @Override
    @NotNull
    public Result charTyped(char charTyped,
                            @NotNull Project project,
                            @NotNull Editor editor,
                            @NotNull PsiFile file) {
        return LSPServerSideOnTypeFormattingHelper.applyOnTypeFormatting(charTyped, editor, file) ?
                Result.STOP :
                super.charTyped(charTyped, project, editor, file);
    }
}
