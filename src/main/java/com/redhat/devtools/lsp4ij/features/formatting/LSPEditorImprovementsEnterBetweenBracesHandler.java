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

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.client.features.EditorBehaviorFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This provides a workaround for <a href="https://youtrack.jetbrains.com/issue/IJPL-159454">IJPL-159454</a>.
 */
public class LSPEditorImprovementsEnterBetweenBracesHandler extends EnterBetweenBracesHandler {
    private boolean enterAfterOpenBrace = false;
    private Character openBraceCharacter = null;
    private boolean enterBeforeCloseBrace = false;
    private Character closeBraceCharacter = null;

    @Override
    public Result preprocessEnter(
            @NotNull PsiFile file,
            @NotNull Editor editor,
            @NotNull Ref<Integer> caretOffset,
            @NotNull Ref<Integer> caretAdvance,
            @NotNull DataContext dataContext,
            @Nullable EditorActionHandler originalHandler) {
        if (LSPIJEditorUtils.isSupportedAbstractFileTypeOrTextMateFile(file) &&
            CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER &&
            EditorBehaviorFeature.enableEnterBetweenBracesFix(file)) {
            CaretModel caretModel = editor.getCaretModel();
            int offset = caretModel.getOffset();
            if (offset > 0) {
                Document document = editor.getDocument();
                CharSequence charsSequence = document.getCharsSequence();
                char previousCharacter = charsSequence.charAt(offset - 1);
                if (LSPIJEditorUtils.isOpenBraceCharacter(file, previousCharacter)) {
                    enterAfterOpenBrace = true;
                    openBraceCharacter = previousCharacter;
                    closeBraceCharacter = LSPIJEditorUtils.getCloseBraceCharacter(file, openBraceCharacter);
                    if (closeBraceCharacter != null) {
                        char nextCharacter = charsSequence.charAt(offset);
                        if (closeBraceCharacter == nextCharacter) {
                            enterBeforeCloseBrace = true;
                        }
                    }
                }
            }
        }

        return super.preprocessEnter(file, editor, caretOffset, caretAdvance, dataContext, originalHandler);
    }

    @Override
    public Result postProcessEnter(
            @NotNull PsiFile file,
            @NotNull Editor editor,
            @NotNull DataContext dataContext) {
        if (LSPIJEditorUtils.isSupportedAbstractFileTypeOrTextMateFile(file) &&
            CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER &&
            EditorBehaviorFeature.enableEnterBetweenBracesFix(file)) {
            Project project = file.getProject();
            Document document = editor.getDocument();
            PsiDocumentManager.getInstance(project).commitDocument(document);

            CaretModel caretModel = editor.getCaretModel();
            int offset = caretModel.getOffset();
            int lineNumber = document.getLineNumber(offset);
            int lineStartOffset = document.getLineStartOffset(lineNumber);
            int lineEndOffset = document.getLineEndOffset(lineNumber);

            CharSequence documentChars = document.getCharsSequence();

            int currentIndentSize = getCurrentIndentSize(file, document, offset);
            int indentSize = getIndentSize(file);
            int newIndentSize = currentIndentSize + indentSize;
            char indentChar = useTab(file) ? '\t' : ' ';

            if (enterAfterOpenBrace && enterBeforeCloseBrace) {
                String indentedBracedPair = openBraceCharacter + "\n" +
                                            StringUtil.repeatSymbol(indentChar, newIndentSize) + "\n" +
                                            StringUtil.repeatSymbol(indentChar, currentIndentSize) + closeBraceCharacter;

                int bracedPairStartOffset = StringUtil.lastIndexOf(documentChars, openBraceCharacter, 0, offset + 1);
                int bracedPairEndOffset = StringUtil.indexOf(documentChars, closeBraceCharacter, offset);
                if ((bracedPairStartOffset > -1) && (bracedPairEndOffset > -1)) {
                    // This is a workaround for a bizarre behavior where additional whitespace is being added
                    int bracedPairEndLineNumber = document.getLineNumber(bracedPairEndOffset);
                    int bracedPairEndLineEndOffset = document.getLineEndOffset(bracedPairEndLineNumber);
                    if (bracedPairEndLineEndOffset > bracedPairEndOffset) {
                        CharSequence afterBracedPairEndChars = documentChars.subSequence(bracedPairEndOffset + 1, bracedPairEndLineEndOffset);
                        String afterBracedPairEndText = afterBracedPairEndChars.toString();
                        if (!afterBracedPairEndText.isEmpty() && afterBracedPairEndText.trim().isEmpty()) {
                            bracedPairEndOffset = bracedPairEndLineEndOffset - 1;
                        }
                    }

                    document.replaceString(bracedPairStartOffset, bracedPairEndOffset + 1, indentedBracedPair);
                    caretModel.moveToOffset(lineStartOffset + newIndentSize);
                }
            } else {
                int newLineIndentSize = enterAfterOpenBrace ? newIndentSize : currentIndentSize;
                String indentedNewline = StringUtil.repeatSymbol(indentChar, newLineIndentSize);

                // Find the first non-whitespace character in the line as we'll only replace up to that point
                CharSequence lineChars = documentChars.subSequence(lineStartOffset, lineEndOffset);
                String lineText = lineChars.toString();
                int firstNonWhitespaceCharacterIndex = StringUtil.findFirst(
                        lineText,
                        lineCharacter -> !Character.isWhitespace(lineCharacter)
                );
                if (firstNonWhitespaceCharacterIndex > -1) {
                    lineEndOffset = firstNonWhitespaceCharacterIndex - 1;
                    indentedNewline = "";
                }

                if (lineEndOffset > lineStartOffset) {
                    document.replaceString(lineStartOffset, lineEndOffset - 1, indentedNewline);
                } else {
                    document.insertString(lineStartOffset, indentedNewline);
                }
                caretModel.moveToOffset(lineStartOffset + newLineIndentSize);
            }

            enterAfterOpenBrace = false;
            enterBeforeCloseBrace = false;
            openBraceCharacter = null;
            closeBraceCharacter = null;

            return Result.Stop;
        }

        return super.postProcessEnter(file, editor, dataContext);
    }

    private static int getCurrentIndentSize(@NotNull PsiFile file,
                                            @NotNull Document document,
                                            int offset) {
        int currentIndentSize = 0;

        int lineNumber = document.getLineNumber(offset);
        int beforeLineNumber = lineNumber - 1;
        int beforeLineStartOffset = document.getLineStartOffset(beforeLineNumber);
        int beforeLineEndOffset = document.getLineEndOffset(beforeLineNumber);
        CharSequence documentChars = document.getCharsSequence();
        CharSequence beforeLineChars = documentChars.subSequence(beforeLineStartOffset, beforeLineEndOffset);
        String beforeLineText = beforeLineChars.toString();
        if (StringUtil.isNotEmpty(beforeLineText.trim())) {
            for (int i = 0, lineLength = beforeLineText.length(); i < lineLength; i++) {
                if (!Character.isWhitespace(beforeLineText.charAt(i))) {
                    currentIndentSize = i;
                    break;
                }
            }
        } else if (!beforeLineText.isEmpty()) {
            currentIndentSize = StringUtil.countChars(beforeLineText, useTab(file) ? '\t' : ' ');
        }

        return currentIndentSize;
    }

    @NotNull
    private static CodeStyleSettings getCodeStyleSettings(@NotNull PsiFile file) {
        CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(file);
        if (codeStyleSettings == null) {
            codeStyleSettings = CodeStyle.getDefaultSettings();
        }
        return codeStyleSettings;
    }

    private static boolean useTab(@NotNull PsiFile file) {
        return getCodeStyleSettings(file).useTabCharacter(file.getFileType());
    }

    private static int getIndentSize(@NotNull PsiFile file) {
        return useTab(file) ? 1 : getCodeStyleSettings(file).getIndentSize(file.getFileType());
    }
}
