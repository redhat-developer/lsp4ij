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
import com.redhat.devtools.lsp4ij.features.codeBlockProvider.LSPCodeBlockProvider;
import com.redhat.devtools.lsp4ij.features.codeBlockProvider.LSPCodeBlockUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase.expandToWholeLinesWithBlanks;

/**
 * Extend word selection handler that includes statement groups, i.e., groups of statements within the same code block
 * that have no intermediate empty lines.
 */
class LSPCodeBlockStatementGroupSelectioner implements LSPCodeBlockSelectioner {

    @Nullable
    public List<TextRange> getTextRanges(@NotNull PsiFile file,
                                         @NotNull Editor editor,
                                         @NotNull CharSequence editorText,
                                         @NotNull TextRange codeBlockRange,
                                         int currentLineNumber) {
        // Must be starting on a non-empty line
        Document document = editor.getDocument();
        if (isEmpty(document, editorText, currentLineNumber)) {
            return null;
        }

        // The current statement group contains all non-empty lines within the code block from the start offset
        int codeBlockStartLineNumber = document.getLineNumber(codeBlockRange.getStartOffset());
        int codeBlockEndLineNumber = document.getLineNumber(codeBlockRange.getEndOffset());
        if ((codeBlockStartLineNumber >= currentLineNumber) || (codeBlockEndLineNumber <= currentLineNumber)) {
            return null;
        }

        int statementGroupStartLineNumber = currentLineNumber;
        while ((statementGroupStartLineNumber > codeBlockStartLineNumber) &&
               !isEmpty(document, editorText, statementGroupStartLineNumber - 1)) {
            int pendingStatementGroupStartLineNumber = statementGroupStartLineNumber - 1;

            // If this line looks like it might end a nested code block, confirm that to be the case or not, and if so,
            // add all lines in that nested code block
            int currentLineStartOffset = document.getLineStartOffset(statementGroupStartLineNumber);
            int currentLineEndOffset = document.getLineEndOffset(statementGroupStartLineNumber);
            while ((currentLineStartOffset <= currentLineEndOffset) && Character.isWhitespace(editorText.charAt(currentLineStartOffset))) {
                currentLineStartOffset++;
            }
            if (LSPCodeBlockUtils.isCodeBlockEndChar(file, editorText.charAt(currentLineStartOffset))) {
                TextRange nestedCodeBlockRange = LSPCodeBlockProvider.getCodeBlockRange(editor, file, currentLineStartOffset - 1);
                if ((nestedCodeBlockRange != null) && codeBlockRange.contains(nestedCodeBlockRange) && !Objects.equals(codeBlockRange, nestedCodeBlockRange)) {
                    int nestedCodeBlockStartLineNumber = document.getLineNumber(nestedCodeBlockRange.getStartOffset());
                    if ((nestedCodeBlockStartLineNumber > codeBlockStartLineNumber) && (nestedCodeBlockStartLineNumber < statementGroupStartLineNumber)) {
                        pendingStatementGroupStartLineNumber = nestedCodeBlockStartLineNumber;
                    }
                }
            }

            statementGroupStartLineNumber = pendingStatementGroupStartLineNumber;
        }
        statementGroupStartLineNumber = Math.max(statementGroupStartLineNumber, codeBlockStartLineNumber + 1);

        int statementGroupEndLineNumber = currentLineNumber;
        while ((statementGroupEndLineNumber < codeBlockEndLineNumber) &&
               !isEmpty(document, editorText, statementGroupEndLineNumber + 1)) {
            int pendingStatementGroupEndLineNumber = statementGroupEndLineNumber + 1;

            // If this line looks like it might start a nested code block, confirm that to be the case or not, and if
            // so, add all lines in that nested code block
            int currentLineStartOffset = document.getLineStartOffset(statementGroupEndLineNumber);
            int currentLineEndOffset = document.getLineEndOffset(statementGroupEndLineNumber);
            while ((currentLineEndOffset >= currentLineStartOffset) && Character.isWhitespace(editorText.charAt(currentLineEndOffset))) {
                currentLineEndOffset--;
            }
            if (LSPCodeBlockUtils.isCodeBlockStartChar(file, editorText.charAt(currentLineEndOffset))) {
                TextRange nestedCodeBlockRange = LSPCodeBlockProvider.getCodeBlockRange(editor, file, currentLineEndOffset + 1);
                if ((nestedCodeBlockRange != null) && codeBlockRange.contains(nestedCodeBlockRange) && !Objects.equals(codeBlockRange, nestedCodeBlockRange)) {
                    int nestedCodeBlockEndLineNumber = document.getLineNumber(nestedCodeBlockRange.getEndOffset());
                    if ((nestedCodeBlockEndLineNumber < codeBlockEndLineNumber) && (nestedCodeBlockEndLineNumber > statementGroupEndLineNumber)) {
                        pendingStatementGroupEndLineNumber = nestedCodeBlockEndLineNumber;
                    }
                }
            }

            statementGroupEndLineNumber = pendingStatementGroupEndLineNumber;
        }
        statementGroupEndLineNumber = Math.min(statementGroupEndLineNumber, codeBlockEndLineNumber - 1);

        int statementGroupStartOffset = document.getLineStartOffset(statementGroupStartLineNumber);
        int statementGroupEndOffset = document.getLineEndOffset(statementGroupEndLineNumber);
        TextRange statementGroupTextRange = TextRange.create(statementGroupStartOffset, statementGroupEndOffset);
        return expandToWholeLinesWithBlanks(editorText, statementGroupTextRange);
    }

    private static boolean isEmpty(@NotNull Document document,
                                   @NotNull CharSequence editorText,
                                   int lineNumber) {
        int lineStartOffset = document.getLineStartOffset(lineNumber);
        int lineEndOffset = document.getLineEndOffset(lineNumber);
        if (lineEndOffset > lineStartOffset) {
            for (int offset = lineStartOffset; offset <= lineEndOffset; offset++) {
                if (!Character.isWhitespace(editorText.charAt(offset))) {
                    return false;
                }
            }
        }
        return true;
    }
}
