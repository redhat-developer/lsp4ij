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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase.expandToWholeLinesWithBlanks;

/**
 * Extend word selection handler that includes all distinct lines within a code block.
 */
class LSPCodeBlockBodySelectioner implements LSPCodeBlockSelectioner {

    @Override
    @Nullable
    public List<TextRange> getTextRanges(@NotNull PsiFile file,
                                         @NotNull Editor editor,
                                         @NotNull CharSequence editorText,
                                         @NotNull TextRange codeBlockRange,
                                         int currentLineNumber) {
        // Make sure there's actually a code block to select
        Document document = editor.getDocument();
        int codeBlockStartLineNumber = document.getLineNumber(codeBlockRange.getStartOffset());
        int codeBlockEndLineNumber = document.getLineNumber(codeBlockRange.getEndOffset());
        if ((codeBlockEndLineNumber - codeBlockStartLineNumber) < 2) {
            return null;
        }

        // Select all lines in the code block
        int codeBlockBodyStartOffset = document.getLineStartOffset(codeBlockStartLineNumber + 1);
        int codeBlockBodyEndOffset = document.getLineEndOffset(codeBlockEndLineNumber - 1);
        TextRange codeBlockBodyTextRange = TextRange.create(codeBlockBodyStartOffset, codeBlockBodyEndOffset);
        return expandToWholeLinesWithBlanks(editorText, codeBlockBodyTextRange);
    }
}
