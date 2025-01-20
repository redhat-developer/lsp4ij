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

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Performs LSP-based on-type formatting if appropriate for the file when Enter is typed. For handling of other typed
 * characters, see {@link LSPServerSideOnTypeFormattingTypedHandler}.
 */
public class LSPServerSideOnTypeFormattingEnterHandler extends EnterHandlerDelegateAdapter {

    @Override
    public Result postProcessEnter(@NotNull PsiFile file,
                                   @NotNull Editor editor,
                                   @NotNull DataContext dataContext) {
        return LSPServerSideOnTypeFormattingHelper.applyOnTypeFormatting('\n', editor, file) ?
                Result.Stop :
                super.postProcessEnter(file, editor, dataContext);
    }
}
