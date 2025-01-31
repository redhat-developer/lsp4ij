/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.codeBlockProvider;

import com.intellij.codeInsight.editorActions.CodeBlockProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeBuilder;
import com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport;
import org.eclipse.lsp4j.FoldingRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Code block provider that uses information from {@link LSPSelectionRangeSupport} and {@link LSPFoldingRangeBuilder}.
 */
public class LSPCodeBlockProvider implements CodeBlockProvider {

    @Override
    @Nullable
    public TextRange getCodeBlockRange(Editor editor, PsiFile file) {
        if ((editor == null) || (file == null)) {
            return null;
        }

        int offset = editor.getCaretModel().getOffset();
        return getCodeBlockRange(editor, file, offset);
    }

    @Nullable
    @ApiStatus.Internal
    public static TextRange getCodeBlockRange(@NotNull Editor editor,
                                              @NotNull PsiFile file,
                                              int offset) {
        Document document = editor.getDocument();
        CharSequence documentChars = document.getCharsSequence();
        int documentLength = documentChars.length();

        // Adjust the offset slightly based on before/after brace to ensure evaluation occurs "within" the braced block
        Character beforeCharacter = offset > 0 ? documentChars.charAt(offset - 1) : null;
        Character afterCharacter = offset < documentLength ? documentChars.charAt(offset) : null;
        if (LSPIJEditorUtils.isOpenBraceCharacter(file, afterCharacter)) {
            offset++;
        } else if (LSPIJEditorUtils.isCloseBraceCharacter(file, beforeCharacter)) {
            offset--;
        }

        // See if we're anchored by a known brace character
        int openBraceOffset = -1;
        Character openBraceChar = null;
        int closeBraceOffset = -1;
        Character closeBraceChar = null;
        if ((offset > 0) && LSPIJEditorUtils.isOpenBraceCharacter(file, documentChars.charAt(offset - 1))) {
            openBraceOffset = offset - 1;
            openBraceChar = documentChars.charAt(offset - 1);
            closeBraceChar = LSPIJEditorUtils.getCloseBraceCharacter(file, openBraceChar);
        } else if (LSPIJEditorUtils.isCloseBraceCharacter(file, documentChars.charAt(offset))) {
            closeBraceOffset = offset;
            closeBraceChar = documentChars.charAt(offset);
            openBraceChar = LSPIJEditorUtils.getOpenBraceCharacter(file, closeBraceChar);
        } else if ((offset < (documentLength - 1)) && LSPIJEditorUtils.isCloseBraceCharacter(file, documentChars.charAt(offset + 1))) {
            closeBraceOffset = offset + 1;
            closeBraceChar = documentChars.charAt(offset + 1);
            openBraceChar = LSPIJEditorUtils.getOpenBraceCharacter(file, closeBraceChar);
        }

        // Try to find it first using the selection ranges which tend to be more accurate; we must use the effective
        // offset for selection ranges to act as if we're in the adjusted braced block
        TextRange codeBlockRange = getUsingSelectionRanges(
                file,
                editor,
                offset,
                openBraceChar,
                openBraceOffset,
                closeBraceChar,
                closeBraceOffset
        );

        // Failing that, try to find it using the folding ranges
        if (codeBlockRange == null) {
            codeBlockRange = getUsingFoldingRanges(
                    file,
                    document,
                    offset,
                    openBraceChar,
                    openBraceOffset,
                    closeBraceChar,
                    closeBraceOffset
            );
        }

        // If those failed and we're seemingly anchored by a brace character, try to search unanchored
        if ((codeBlockRange == null) && (openBraceChar != null) && (closeBraceChar != null)) {
            codeBlockRange = getUsingSelectionRanges(
                    file,
                    editor,
                    offset,
                    null,
                    -1,
                    null,
                    -1
            );

            if (codeBlockRange == null) {
                codeBlockRange = getUsingFoldingRanges(
                        file,
                        document,
                        offset,
                        null,
                        -1,
                        null,
                        -1
                );
            }
        }

        return codeBlockRange;
    }

    @Nullable
    private static TextRange getUsingSelectionRanges(@NotNull PsiFile file,
                                                     @NotNull Editor editor,
                                                     int offset,
                                                     @Nullable Character openBraceChar,
                                                     int openBraceOffset,
                                                     @Nullable Character closeBraceChar,
                                                     int closeBraceOffset) {
        Document document = editor.getDocument();
        List<TextRange> selectionTextRanges = LSPSelectionRangeSupport.getSelectionTextRanges(file, editor, offset);
        if (!ContainerUtil.isEmpty(selectionTextRanges)) {
            CharSequence documentChars = document.getCharsSequence();
            int documentLength = documentChars.length();

            // Find containing text ranges that are bounded by brace pairs
            List<TextRange> containingTextRanges = new ArrayList<>(selectionTextRanges.size());
            for (TextRange textRange : selectionTextRanges) {
                if (textRange.getLength() > 1) {
                    int startOffset = textRange.getStartOffset();
                    int endOffset = textRange.getEndOffset();

                    char startChar = documentChars.charAt(startOffset);
                    char endChar = documentChars.charAt(endOffset - 1);

                    // If aligned on an open brace and this ends with the expected close brace, use it
                    if ((startOffset == openBraceOffset)) {
                        if ((closeBraceChar != null) && (closeBraceChar == endChar)) {
                            return textRange;
                        } else {
                            return null;
                        }
                    }
                    // If aligned on a close brace and this starts with the expected open brace, use it
                    else if (((endOffset - 1) == closeBraceOffset)) {
                        if ((openBraceChar != null) && (openBraceChar == startChar)) {
                            return textRange;
                        } else {
                            return null;
                        }
                    }
                    // Otherwise see if it starts and ends with a known brace pair and we'll find the "closest" below
                    else if ((openBraceOffset == -1) && (closeBraceOffset == -1) && LSPIJEditorUtils.isOpenBraceCharacter(file, startChar)) {
                        Character pairedCloseBraceChar = LSPIJEditorUtils.getCloseBraceCharacter(file, startChar);
                        if ((pairedCloseBraceChar != null) && (pairedCloseBraceChar == endChar)) {
                            containingTextRanges.add(textRange);
                        }
                    }
                    // Also try to see if these are exactly the contents of a code block
                    else if ((openBraceOffset == -1) && (closeBraceOffset == -1) && (startOffset > 0) && (endOffset < documentLength)) {
                        startChar = documentChars.charAt(startOffset - 1);
                        endChar = documentChars.charAt(endOffset);

                        if (LSPIJEditorUtils.isOpenBraceCharacter(file, startChar)) {
                            Character pairedCloseBraceChar = LSPIJEditorUtils.getCloseBraceCharacter(file, startChar);
                            if ((pairedCloseBraceChar != null) && (pairedCloseBraceChar == endChar)) {
                                containingTextRanges.add(textRange);
                            }
                        }
                    }
                }
            }

            // Return the closest (i.e., smallest) containing text range
            if (!ContainerUtil.isEmpty(containingTextRanges)) {
                containingTextRanges.sort(Comparator.comparingInt(TextRange::getLength));
                return ContainerUtil.getFirstItem(containingTextRanges);
            }
        }

        return null;
    }

    @Nullable
    private static TextRange getUsingFoldingRanges(@NotNull PsiFile file,
                                                   @NotNull Document document,
                                                   int offset,
                                                   @Nullable Character openBraceChar,
                                                   int openBraceOffset,
                                                   @Nullable Character closeBraceChar,
                                                   int closeBraceOffset) {
        List<FoldingRange> foldingRanges = LSPFoldingRangeBuilder.getFoldingRanges(file);
        if (!ContainerUtil.isEmpty(foldingRanges)) {
            CharSequence documentChars = document.getCharsSequence();
            int documentLength = documentChars.length();

            List<TextRange> containingTextRanges = new ArrayList<>(foldingRanges.size());
            for (FoldingRange foldingRange : foldingRanges) {
                TextRange textRange = LSPFoldingRangeBuilder.getTextRange(
                        foldingRange,
                        file,
                        document,
                        openBraceChar,
                        closeBraceChar
                );
                if ((textRange != null) && (textRange.getLength() > 1)) {
                    int startOffset = Math.max(0, textRange.getStartOffset() - 1);
                    int endOffset = Math.min(documentLength - 1, textRange.getEndOffset());
                    // These ranges can tend to add whitespace at the end, so trim that before looking for braces
                    while ((endOffset > startOffset) && Character.isWhitespace(documentChars.charAt(endOffset))) {
                        endOffset--;
                    }

                    char startChar = documentChars.charAt(startOffset);
                    char endChar = documentChars.charAt(endOffset);

                    // If aligned on an open brace and this ends with the expected close brace, use it
                    if ((startOffset == openBraceOffset)) {
                        if ((closeBraceChar != null) && (closeBraceChar == endChar)) {
                            return textRange;
                        } else {
                            return null;
                        }
                    }
                    // If aligned on a close brace and this starts with the expected open brace, use it
                    else if ((endOffset == closeBraceOffset)) {
                        if ((openBraceChar != null) && (openBraceChar == startChar)) {
                            return textRange;
                        } else {
                            return null;
                        }
                    }
                    // Otherwise see if it starts and ends with a known brace pair and we'll find the "closest" below
                    else if (textRange.containsOffset(offset) &&
                             (openBraceOffset == -1) &&
                             (closeBraceOffset == -1) &&
                             LSPIJEditorUtils.isOpenBraceCharacter(file, startChar)) {
                        Character pairedCloseBraceChar = LSPIJEditorUtils.getCloseBraceCharacter(file, startChar);
                        if ((pairedCloseBraceChar != null) && (pairedCloseBraceChar == endChar)) {
                            containingTextRanges.add(textRange);
                        }
                    }
                }
            }

            // Return the closest (i.e., smallest) containing text range
            if (!ContainerUtil.isEmpty(containingTextRanges)) {
                containingTextRanges.sort(Comparator.comparingInt(TextRange::getLength));
                return ContainerUtil.getFirstItem(containingTextRanges);
            }
        }

        return null;
    }
}
