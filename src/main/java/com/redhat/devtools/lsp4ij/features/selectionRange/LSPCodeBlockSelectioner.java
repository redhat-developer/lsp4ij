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

package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Extend word selection handler interface for contents of LSP-defined code blocks retrieved via
 * {@link LSPCodeBlockExtendWordSelectionHandler}.
 */
interface LSPCodeBlockSelectioner {

    /**
     * Returns the selection text ranges for the specified code block and current line number.
     *
     * @param file              the PSI file
     * @param editor            the editor
     * @param editorText        the editor text
     * @param codeBlockRange    the code block text range
     * @param currentLineNumber the current line number
     * @return the selection text ranges or null if this selectioner cannot provide selection text ranges
     */
    @Nullable
    List<TextRange> getTextRanges(@NotNull PsiFile file,
                                  @NotNull Editor editor,
                                  @NotNull CharSequence editorText,
                                  @NotNull TextRange codeBlockRange,
                                  int currentLineNumber);
}
