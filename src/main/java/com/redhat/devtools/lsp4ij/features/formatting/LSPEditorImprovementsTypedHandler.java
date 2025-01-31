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
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
        if (LSPIJEditorUtils.isSupportedPlainTextOrTextMateFile(file)) {
            if (LSPIJEditorUtils.isQuoteCharacter(file, charTyped) && handleNestedQuote(file, editor, charTyped)) {
                return Result.STOP;
            }
            // TODO: Get statement terminator character(s) from client config?
            else if ((';' == charTyped) && handleStatementTerminator(editor)) {
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

    private boolean handleStatementTerminator(@NotNull Editor editor) {
        Document document = editor.getDocument();
        CaretModel caretModel = editor.getCaretModel();
        int offset = caretModel.getOffset();
        if (offset == document.getTextLength()) {
            return false;
        }

        CharSequence charsSequence = document.getCharsSequence();
        char nextCharacter = charsSequence.charAt(offset);
        if (nextCharacter == ';') {
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
        List<String> stringLiteralPatterns = new ArrayList<>(quoteCharacters.size());
        for (Character quoteCharacter : quoteCharacters) {
            stringLiteralPatterns.add(quoteCharacter + "(?:[^" + quoteCharacter + "\\\\\\r\\n]|\\\\.)*" + quoteCharacter);
        }
        Pattern stringLiteralPattern = Pattern.compile("(" + StringUtil.join(stringLiteralPatterns, "|") + ")");
        Matcher stringLiteralMatcher = stringLiteralPattern.matcher(fileChars);
        while (stringLiteralMatcher.find()) {
            int stringLiteralStart = stringLiteralMatcher.start();
            int stringLiteralEnd = stringLiteralMatcher.end();
            if ((stringLiteralStart <= offset) && (offset <= stringLiteralEnd)) {
                return fileChars.subSequence(stringLiteralStart, stringLiteralEnd).toString();
            }
        }

        return null;
    }
}