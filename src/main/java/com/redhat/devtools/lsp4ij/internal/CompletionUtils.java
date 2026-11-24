/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 *****************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP/DAP Completion utilities.
 */
public class CompletionUtils {

    @Nullable
    public static Integer computePrefixStartFromInsertText(@NotNull Document document,
                                                           @Nullable PsiFile file,
                                                           int completionOffset,
                                                           String insertText) {

        // case 2.1: first strategy, we collect word range at
        // ex :
        // insertText= '(let [${1:binding} ${2:value}])'
        // document= le
        // we have to return |le as prefix start offset

        TextRange wordRange = LSPIJUtils.getWordRangeAt(document, file, completionOffset);
        if (wordRange != null) {
            int endOffset = Math.min(completionOffset, document.getTextLength());
            int startOffset = Math.min(wordRange.getStartOffset(), endOffset);
            if (containsWhitespace(document.getCharsSequence().subSequence(startOffset, endOffset))) {
                // The PSI element at the completion offset spans several words, as a comment that a custom PSI keeps
                // as one token: the completion replaces the word that ends at the completion offset (#447)
                // ex :
                // document= ---@diagnostic disable-next-line : u|
                // we have to return |u as prefix start offset
                return getWordStartOffset(document, completionOffset);
            }
            return wordRange.getStartOffset();
        }

        // case 2.2: second strategy, we check if the left content of the completion offset
        // matches the full insertText left content
        // ex :
        // insertText= 'foo.bar'
        // document= {foo.b|}
        // we have to return {| as prefix start offset

        return getPrefixStartOffsetWhichMatchesLeftContent(document, completionOffset, insertText);
    }

    /**
     * Returns the start offset of the word that ends at the given offset in the given document: the Java identifier
     * characters before the offset, which IntelliJ completes in a comment or in a plain text file.
     *
     * @param document the document.
     * @param offset   the offset.
     * @return the start offset of the word that ends at the given offset, which is the offset if there is none.
     */
    public static int getWordStartOffset(@NotNull Document document, int offset) {
        CharSequence chars = document.getCharsSequence();
        int startOffset = Math.min(offset, chars.length());
        while (startOffset > 0 && Character.isJavaIdentifierPart(chars.charAt(startOffset - 1))) {
            startOffset--;
        }
        return startOffset;
    }

    /**
     * Returns true if the given text contains whitespace, as the text of a comment from its start to the completion
     * offset, which is then no word to complete, and false otherwise.
     *
     * @param text the text.
     * @return true if the given text contains whitespace and false otherwise.
     */
    public static boolean containsWhitespace(@NotNull CharSequence text) {
        return text.chars().anyMatch(Character::isWhitespace);
    }

    @Nullable
    private static Integer getPrefixStartOffsetWhichMatchesLeftContent(@NotNull Document document,
                                                                       int completionOffset,
                                                                       @NotNull String insertText) {
        int startOffset = Math.max(0, completionOffset - insertText.length());
        int endOffset = startOffset + Math.min(insertText.length(), completionOffset);
        String subDoc = document.getText(new TextRange(startOffset, endOffset)); // "".ch
        for (int i = 0; i < insertText.length() && i < completionOffset; i++) {
            String tentativeCommonString = subDoc.substring(i);
            if (insertText.startsWith(tentativeCommonString)) {
                return completionOffset - tentativeCommonString.length();
            }
        }
        return null;
    }

}
