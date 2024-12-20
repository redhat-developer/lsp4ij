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
import com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeBuilder;
import com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.SelectionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Code block provider that uses information from {@link LSPSelectionRangeSupport} and {@link LSPFoldingRangeBuilder}.
 */
public class LSPCodeBlockProvider implements CodeBlockProvider {

    @Override
    @Nullable
    public TextRange getCodeBlockRange(Editor editor, PsiFile file) {
        Document document = editor.getDocument();
        CharSequence documentChars = document.getCharsSequence();
        int documentLength = documentChars.length();

        // Adjust the offset slightly based on before/after brace to ensure evaluation occurs "within" the braced block
        int offset = editor.getCaretModel().getOffset();
        Character beforeCharacter = offset > 0 ? documentChars.charAt(offset - 1) : null;
        Character afterCharacter = offset < documentLength ? documentChars.charAt(offset) : null;
        if (LSPCodeBlockUtils.isCodeBlockStartChar(file, afterCharacter)) {
            offset++;
        } else if (LSPCodeBlockUtils.isCodeBlockEndChar(file, beforeCharacter)) {
            offset--;
        }

        // See if we're anchored by a known brace character
        int openBraceOffset = -1;
        Character openBraceChar = null;
        int closeBraceOffset = -1;
        Character closeBraceChar = null;
        if ((offset > 0) && LSPCodeBlockUtils.isCodeBlockStartChar(file, documentChars.charAt(offset - 1))) {
            openBraceOffset = offset - 1;
            openBraceChar = documentChars.charAt(offset - 1);
            closeBraceChar = LSPCodeBlockUtils.getCodeBlockEndChar(file, openBraceChar);
        } else if (LSPCodeBlockUtils.isCodeBlockEndChar(file, documentChars.charAt(offset))) {
            closeBraceOffset = offset;
            closeBraceChar = documentChars.charAt(offset);
            openBraceChar = LSPCodeBlockUtils.getCodeBlockStartChar(file, closeBraceChar);
        } else if ((offset < (documentLength - 1)) && LSPCodeBlockUtils.isCodeBlockEndChar(file, documentChars.charAt(offset + 1))) {
            closeBraceOffset = offset + 1;
            closeBraceChar = documentChars.charAt(offset + 1);
            openBraceChar = LSPCodeBlockUtils.getCodeBlockStartChar(file, closeBraceChar);
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
        if (codeBlockRange != null) {
            return codeBlockRange;
        }

        // Failing that, try to find it using the folding ranges
        return getUsingFoldingRanges(
                file,
                document,
                offset,
                openBraceChar,
                openBraceOffset,
                closeBraceChar,
                closeBraceOffset
        );
    }

    @Nullable
    private TextRange getUsingSelectionRanges(@NotNull PsiFile file,
                                              @NotNull Editor editor,
                                              int offset,
                                              @Nullable Character openBraceChar,
                                              int openBraceOffset,
                                              @Nullable Character closeBraceChar,
                                              int closeBraceOffset) {
        Document document = editor.getDocument();
        List<SelectionRange> selectionRanges = LSPSelectionRangeSupport.getSelectionRanges(file, document, offset);
        if (!ContainerUtil.isEmpty(selectionRanges)) {
            // Convert the selection ranges into text ranges
            Set<TextRange> textRanges = new LinkedHashSet<>(selectionRanges.size());
            for (SelectionRange selectionRange : selectionRanges) {
                textRanges.add(LSPSelectionRangeSupport.getTextRange(selectionRange, document));
                for (SelectionRange parentSelectionRange = selectionRange.getParent();
                     parentSelectionRange != null;
                     parentSelectionRange = parentSelectionRange.getParent()) {
                    textRanges.add(LSPSelectionRangeSupport.getTextRange(parentSelectionRange, document));
                }
            }

            CharSequence documentChars = document.getCharsSequence();
            int documentLength = documentChars.length();

            // Find containing text ranges that are bounded by brace pairs
            List<TextRange> containingTextRanges = new ArrayList<>(textRanges.size());
            for (TextRange textRange : textRanges) {
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
                    else if ((openBraceOffset == -1) && (closeBraceOffset == -1) && LSPCodeBlockUtils.isCodeBlockStartChar(file, startChar)) {
                        Character pairedCloseBraceChar = LSPCodeBlockUtils.getCodeBlockEndChar(file, startChar);
                        if ((pairedCloseBraceChar != null) && (pairedCloseBraceChar == endChar)) {
                            containingTextRanges.add(textRange);
                        }
                    }
                    // Also try to see if these are exactly the contents of a code block
                    else if ((openBraceOffset == -1) && (closeBraceOffset == -1) && (startOffset > 0) && (endOffset < documentLength)) {
                        startChar = documentChars.charAt(startOffset - 1);
                        endChar = documentChars.charAt(endOffset);

                        if (LSPCodeBlockUtils.isCodeBlockStartChar(file, startChar)) {
                            Character pairedCloseBraceChar = LSPCodeBlockUtils.getCodeBlockEndChar(file, startChar);
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
                             LSPCodeBlockUtils.isCodeBlockStartChar(file, startChar)) {
                        Character pairedCloseBraceChar = LSPCodeBlockUtils.getCodeBlockEndChar(file, startChar);
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
