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
import com.intellij.lang.Commenter;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.EditorBehaviorFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Improves editor-typed handling of nested quote characters and statement terminators.
 */
public class LSPEditorImprovementsTypedHandler extends TypedHandlerDelegate {

    @NotNull
    @Override
    public Result beforeCharTyped(char charTyped,
                                  @NotNull Project project,
                                  @NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull FileType fileType) {
        if (LSPIJEditorUtils.isSupportedAbstractFileTypeOrTextMateFile(file)) {
            // String literal improvements
            if (LSPIJEditorUtils.isQuoteCharacter(file, charTyped) &&
                EditorBehaviorFeature.enableStringLiteralImprovements(file) &&
                handleNestedQuote(file, editor, charTyped)) {
                return Result.STOP;
            }
            // Statement terminator improvements
            else if (EditorBehaviorFeature.enableStatementTerminatorImprovements(file) &&
                     isStatementTerminatorCharacter(file, charTyped) &&
                     handleStatementTerminator(file, editor)) {
                return Result.STOP;
            }
        }

        return Result.CONTINUE;
    }

    private boolean handleNestedQuote(@NotNull PsiFile file,
                                      @NotNull Editor editor,
                                      char quote) {
        Document document = editor.getDocument();
        CharSequence documentChars = document.getCharsSequence();
        int offset = editor.getCaretModel().getOffset();

        // Try to find a string literal that contains the current offset
        String stringLiteral = getStringLiteral(file, documentChars, offset);
        if (StringUtil.isNotEmpty(stringLiteral)) {
            char stringLiteralQuote = stringLiteral.charAt(0);
            // Single quote within double-quoted string or vice-versa
            // Escaped single quote within single-quoted string or vice-versa
            if ((stringLiteralQuote != quote) || ((offset > 0) && (documentChars.charAt(offset - 1) == '\\'))) {
                document.insertString(offset, String.valueOf(quote));
                editor.getCaretModel().moveToOffset(offset + 1);
                return true;
            }
        }
        // If the same quote character used for the string is typed immediately after an escape and before the string
        // literal's closing quote character, no string literal will be found, so just insert it and advance the caret
        else if ((offset > 0) && (documentChars.charAt(offset - 1) == '\\')) {
            document.insertString(offset, String.valueOf(quote));
            editor.getCaretModel().moveToOffset(offset + 1);
            return true;
        }

        return false;
    }

    private static boolean isStatementTerminatorCharacter(@NotNull PsiFile file, char charTyped) {
        return LanguageServiceAccessor.getInstance(file.getProject()).hasAny(
                file.getVirtualFile(),
                ls -> ls.getClientFeatures().getStatementTerminatorCharacters(file).indexOf(charTyped) > -1
        );
    }

    private boolean handleStatementTerminator(@NotNull PsiFile file,
                                              @NotNull Editor editor) {
        Document document = editor.getDocument();
        CaretModel caretModel = editor.getCaretModel();
        int offset = caretModel.getOffset();
        if (offset == document.getTextLength()) {
            return false;
        }

        CharSequence documentChars = document.getCharsSequence();
        char nextCharacter = documentChars.charAt(offset);
        if (isStatementTerminatorCharacter(file, nextCharacter) &&
            !inStringLiteral(file, documentChars, offset) &&
            !inComment(file, documentChars, offset)) {
            caretModel.moveToOffset(offset + 1);
            return true;
        }

        return false;
    }

    @Nullable
    private static String getStringLiteral(@NotNull PsiFile file,
                                           @NotNull CharSequence fileChars,
                                           int offset) {
        Set<Character> quoteCharacters = LSPIJEditorUtils.getQuoteCharacters(file);
        if (!ContainerUtil.isEmpty(quoteCharacters)) {
            // This pattern is basically from any quote character to the same character with no unescaped same quote
            // character or line break in between
            Pattern stringLiteralPattern = Pattern.compile("([" + StringUtil.join(quoteCharacters, "") + "])([^$1\\r\\n]|\\\\\\.)*\\1");
            Matcher stringLiteralMatcher = stringLiteralPattern.matcher(fileChars);
            while (stringLiteralMatcher.find()) {
                int stringLiteralStart = stringLiteralMatcher.start();
                int stringLiteralEnd = stringLiteralMatcher.end();
                if ((stringLiteralStart < offset) && (offset < stringLiteralEnd)) {
                    return fileChars.subSequence(stringLiteralStart, stringLiteralEnd).toString();
                }
            }
        }

        return null;
    }

    private static boolean inStringLiteral(@NotNull PsiFile file,
                                           @NotNull CharSequence documentChars,
                                           int offset) {
        return getStringLiteral(file, documentChars, offset) != null;
    }

    private static boolean inComment(@NotNull PsiFile file,
                                     @NotNull CharSequence documentChars,
                                     int offset) {
        boolean inComment = false;

        Document document = LSPIJUtils.getDocument(file);
        if (document != null) {
            Commenter commenter = LSPIJEditorUtils.getCommenter(file);

            // First try to see if we're in a line comment since that's much more efficient. We're in a line comment if
            // we find a line comment start on the same line before the current position.
            String lineCommentPrefix = commenter.getLineCommentPrefix();
            if (StringUtil.isNotEmpty(lineCommentPrefix)) {
                int lineNumber = document.getLineNumber(offset);
                int lineStartOffset = document.getLineStartOffset(lineNumber);
                int lineEndOffset = document.getLineEndOffset(lineNumber);
                if (lineEndOffset > lineStartOffset) {
                    CharSequence lineChars = documentChars.subSequence(lineStartOffset, lineEndOffset);
                    if (!lineChars.isEmpty()) {
                        String lineText = lineChars.toString();
                        int lineCommentPrefixIndex = lineText.lastIndexOf(lineCommentPrefix, offset);
                        if (lineCommentPrefixIndex > -1) {
                            int lineCommentPrefixOffset = lineStartOffset + lineCommentPrefixIndex;
                            if (lineCommentPrefixOffset < offset) {
                                inComment = true;
                            }
                        }
                    }
                }
            }

            // If not detected in a line comment, try to see if we're in a block comment. We're in a block comment if
            // we find a block comment start before the current position without a block comment end in the interim.
            if (!inComment) {
                String blockCommentPrefix = commenter.getBlockCommentPrefix();
                String blockCommentSuffix = commenter.getBlockCommentSuffix();
                if (StringUtil.isNotEmpty(blockCommentPrefix) && StringUtil.isNotEmpty(blockCommentSuffix)) {
                    CharSequence beforeOffsetChars = documentChars.subSequence(0, offset);
                    if (!beforeOffsetChars.isEmpty()) {
                        String beforeOffsetText = beforeOffsetChars.toString();
                        int previousBlockCommentPrefixIndex = beforeOffsetText.lastIndexOf(blockCommentPrefix, offset);
                        if (previousBlockCommentPrefixIndex > -1) {
                            // For efficiency, limit the text that has to be searched for the block comment end to the span
                            // between the previous block comment start and the current position.
                            CharSequence betweenBlockCommentPrefixAndOffsetChars = documentChars.subSequence(previousBlockCommentPrefixIndex + blockCommentPrefix.length(), offset);
                            if (!betweenBlockCommentPrefixAndOffsetChars.isEmpty()) {
                                String betweenBlockCommentPrefixAndOffsetText = betweenBlockCommentPrefixAndOffsetChars.toString();
                                int previousBlockCommentSuffixIndex = betweenBlockCommentPrefixAndOffsetText.indexOf(blockCommentSuffix);
                                if (previousBlockCommentSuffixIndex == -1) {
                                    inComment = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return inComment;
    }
}