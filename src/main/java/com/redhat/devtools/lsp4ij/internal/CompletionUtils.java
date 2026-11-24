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
                // as one token: the completion replaces the text after the last whitespace that it starts with (#447)
                // ex :
                // insertText= 'undefined-global'
                // document= ---@diagnostic disable-next-line : undefined-g|
                // we have to return |undefined-g as prefix start offset
                return getPrefixStartOffsetAfterWhitespace(document, completionOffset, insertText);
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
     * Returns the start offset of the prefix that the given text completes at the given offset, where the PSI element
     * at the offset spans whitespace, as a comment that a custom PSI keeps as one token (#447).
     * <p>
     * The prefix is the longest end of the text between the last whitespace before the offset and the offset that the
     * given text starts with, and that doesn't start between two identifier characters: {@code undefined-g} for
     * {@code undefined-global}, {@code foo.ba} for {@code foo.bar}, and {@code ba} of {@code foo.ba} for {@code bar}.
     * If there is none, it is the Java identifier characters before the offset, which IntelliJ's prefix matcher then
     * matches as in any other comment.
     *
     * @param document the document.
     * @param offset   the offset.
     * @param text     the text of the completion item.
     * @return the start offset of the prefix that the given text completes at the given offset.
     */
    public static int getPrefixStartOffsetAfterWhitespace(@NotNull Document document, int offset, @NotNull String text) {
        CharSequence chars = document.getCharsSequence();
        int endOffset = Math.min(offset, chars.length());
        int tokenStartOffset = endOffset;
        while (tokenStartOffset > 0 && !Character.isWhitespace(chars.charAt(tokenStartOffset - 1))) {
            tokenStartOffset--;
        }
        String token = chars.subSequence(tokenStartOffset, endOffset).toString();
        for (int i = 0; i < token.length(); i++) {
            if (!isInsideWord(token, i) && text.regionMatches(0, token, i, token.length() - i)) {
                return tokenStartOffset + i;
            }
        }
        int wordStart = token.length();
        while (wordStart > 0 && Character.isJavaIdentifierPart(token.charAt(wordStart - 1))) {
            wordStart--;
        }
        return tokenStartOffset + wordStart;
    }

    private static boolean isInsideWord(@NotNull String text, int index) {
        return index > 0 &&
                Character.isJavaIdentifierPart(text.charAt(index - 1)) &&
                Character.isJavaIdentifierPart(text.charAt(index));
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
