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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature.FormattingScope;
import com.redhat.devtools.lsp4ij.features.codeBlockProvider.LSPCodeBlockProvider;
import com.redhat.devtools.lsp4ij.features.codeBlockProvider.LSPCodeBlockUtils;
import com.redhat.devtools.lsp4ij.features.completion.LSPTypedHandlerDelegate;
import com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed handler for LSP4IJ-managed files that performs automatic on-type formatting for specific keystrokes.
 */
public class LSPClientSideOnTypeFormattingTypedHandler extends TypedHandlerDelegate {

    @Override
    @NotNull
    public Result charTyped(char charTyped,
                            @NotNull Project project,
                            @NotNull Editor editor,
                            @NotNull PsiFile file) {
        LSPFormattingFeature formattingFeature = getClientConfigurationSettings(file);
        if (formattingFeature != null) {
            // Close braces
            if (formattingFeature.isFormatOnCloseBrace(file)) {
                Map.Entry<Character, Character> bracePair = ContainerUtil.find(
                        LSPCodeBlockUtils.getBracePairs(file).entrySet(),
                        entry -> entry.getValue() == charTyped
                );
                if (bracePair != null) {
                    String formatOnCloseBraceCharacters = formattingFeature.getFormatOnCloseBraceCharacters(file);
                    Character openBraceChar = bracePair.getKey();
                    Character closeBraceChar = bracePair.getValue();
                    if (StringUtil.isEmpty(formatOnCloseBraceCharacters) ||
                        (formatOnCloseBraceCharacters.indexOf(closeBraceChar) > -1)) {
                        return handleCloseBraceTyped(
                                project,
                                editor,
                                file,
                                formattingFeature,
                                openBraceChar,
                                closeBraceChar
                        );
                    }
                }
            }

            // Statement terminators
            if (formattingFeature.isFormatOnStatementTerminator(file)) {
                String formatOnStatementTerminatorCharacters = formattingFeature.getFormatOnStatementTerminatorCharacters(file);
                if (StringUtil.isNotEmpty(formatOnStatementTerminatorCharacters) &&
                    (formatOnStatementTerminatorCharacters.indexOf(charTyped) > -1)) {
                    return handleStatementTerminatorTyped(
                            project,
                            editor,
                            file,
                            formattingFeature,
                            charTyped
                    );
                }
            }

            // Completion triggers
            if (formattingFeature.isFormatOnCompletionTrigger(file) &&
                // It must be a completion trigger character for the language no matter what
                LSPTypedHandlerDelegate.hasLanguageServerSupportingCompletionTriggerCharacters(charTyped, project, file)) {
                // But the subset that should trigger completion can be configured
                String formatOnCompletionTriggerCharacters = formattingFeature.getFormatOnCompletionTriggerCharacters(file);
                if (StringUtil.isEmpty(formatOnCompletionTriggerCharacters) ||
                    (formatOnCompletionTriggerCharacters.indexOf(charTyped) > -1)) {
                    return handleCompletionTriggerTyped(
                            project,
                            editor,
                            file
                    );
                }
            }
        }

        return super.charTyped(charTyped, project, editor, file);
    }

    @Nullable
    private static LSPFormattingFeature getClientConfigurationSettings(@NotNull PsiFile file) {
        Project project = file.getProject();
        // Client-side on-type formatting shouldn't trigger a language server to start
        Set<LanguageServerWrapper> startedLanguageServers = LanguageServiceAccessor.getInstance(project).getStartedServers();
        for (LanguageServerWrapper startedLanguageServer : startedLanguageServers) {
            // TODO: Is there a better way to ask if this language server supports the file?
            if (startedLanguageServer.isConnectedTo(LSPIJUtils.toUri(file))) {
                LSPFormattingFeature formattingFeature = startedLanguageServer.getClientFeatures().getFormattingFeature();
                if (formattingFeature.isEnabled(file) && formattingFeature.isSupported(file)) {
                    // TODO: This returns the first match. Is that okay?
                    return formattingFeature;
                }
            }
        }
        return null;
    }

    @NotNull
    private static Result handleCloseBraceTyped(@NotNull Project project,
                                                @NotNull Editor editor,
                                                @NotNull PsiFile file,
                                                @NotNull LSPFormattingFeature formattingFeature,
                                                char openBraceChar,
                                                char closeBraceChar) {
        TextRange formatTextRange = null;

        // Statement-level scope is not supported for code blocks
        FormattingScope formattingScope = formattingFeature.getFormatOnCloseBraceScope(file);
        if (formattingScope == FormattingScope.STATEMENT) {
            return Result.CONTINUE;
        }

        // If appropriate, find the code block that was closed by the brace
        if (formattingScope == FormattingScope.CODE_BLOCK) {
            int offset = editor.getCaretModel().getOffset();
            int beforeOffset = offset - 1;
            TextRange codeBlockRange = LSPCodeBlockProvider.getCodeBlockRange(editor, file, beforeOffset);
            if (codeBlockRange != null) {
                int startOffset = codeBlockRange.getStartOffset();
                int endOffset = codeBlockRange.getEndOffset();

                // Make sure the range includes the brace pair
                Document document = editor.getDocument();
                CharSequence documentChars = document.getCharsSequence();
                if ((startOffset > 0) && (documentChars.charAt(startOffset) != openBraceChar)) {
                    startOffset--;
                }
                if ((endOffset < (documentChars.length() - 1)) && (documentChars.charAt(endOffset) != closeBraceChar)) {
                    endOffset++;
                }

                // If the range is now the braced block, format it
                if ((documentChars.charAt(startOffset) == openBraceChar) && (documentChars.charAt(endOffset) == closeBraceChar)) {
                    formatTextRange = TextRange.create(startOffset, endOffset);
                }
            }
        }

        // If appropriate, use the file text range
        else if (formattingScope == FormattingScope.FILE) {
            formatTextRange = file.getTextRange();
        }

        // If we have a text range now, format it
        if (formatTextRange != null) {
            CodeStyleManager.getInstance(project).reformatText(file, Collections.singletonList(formatTextRange));
            return Result.STOP;
        }

        return Result.CONTINUE;
    }

    @NotNull
    private static Result handleStatementTerminatorTyped(@NotNull Project project,
                                                         @NotNull Editor editor,
                                                         @NotNull PsiFile file,
                                                         @NotNull LSPFormattingFeature formattingFeature,
                                                         char statementTerminatorChar) {
        TextRange formatTextRange = null;

        int offset = editor.getCaretModel().getOffset();
        int beforeOffset = offset - 1;

        // If appropriate, find the statement that was just terminated
        FormattingScope formattingScope = formattingFeature.getFormatOnStatementTerminatorScope(file);
        if (formattingScope == FormattingScope.STATEMENT) {
            List<TextRange> selectionTextRanges = LSPSelectionRangeSupport.getSelectionTextRanges(file, editor, beforeOffset);
            if (!ContainerUtil.isEmpty(selectionTextRanges)) {
                // Find the closest selection range that is extended to line start/end; that should be the statement
                Document document = editor.getDocument();
                CharSequence documentChars = document.getCharsSequence();
                formatTextRange = ContainerUtil.find(
                        selectionTextRanges,
                        selectionTextRange -> {
                            int startOffset = selectionTextRange.getStartOffset();
                            int endOffset = selectionTextRange.getEndOffset();

                            // Remove leading newlines from the range
                            while ((startOffset < endOffset) && (documentChars.charAt(startOffset) == '\n'))
                                startOffset++;
                            // Remove trailing whitespace up until newlines (inclusive) from the range
                            boolean foundNewline = false;
                            while (endOffset > startOffset) {
                                char previousEndChar = documentChars.charAt(endOffset - 1);
                                if (!Character.isWhitespace(previousEndChar)) {
                                    break;
                                } else if (previousEndChar == '\n') {
                                    endOffset--;
                                    foundNewline = true;
                                } else if (!foundNewline) {
                                    endOffset--;
                                } else {
                                    break;
                                }
                            }

                            // See if this is a selection of complete lines
                            int startLineNumber = document.getLineNumber(startOffset);
                            int startLineStartOffset = document.getLineStartOffset(startLineNumber);
                            if (startLineStartOffset == startOffset) {
                                int endLineNumber = document.getLineNumber(endOffset);
                                int endLineEndOffset = document.getLineEndOffset(endLineNumber);
                                if ((endLineEndOffset == endOffset) || (endLineEndOffset == (endOffset + 1))) {
                                    // Make sure that it ends with the terminator that was just typed
                                    String selectionRangeText = StringUtil.trimTrailing(documentChars.subSequence(startOffset, endLineEndOffset).toString());
                                    return ((startOffset + selectionRangeText.length()) == offset) && selectionRangeText.endsWith(String.valueOf(statementTerminatorChar));
                                }
                            }
                            return false;
                        }
                );
            }
        }

        // If appropriate, find the enclosing code block to format
        else if (formattingScope == FormattingScope.CODE_BLOCK) {
            formatTextRange = LSPCodeBlockProvider.getCodeBlockRange(editor, file, beforeOffset);
        }

        // If appropriate, use the file text range
        else if (formattingScope == FormattingScope.FILE) {
            formatTextRange = file.getTextRange();
        }

        // If we have a text range now, format it
        if (formatTextRange != null) {
            CodeStyleManager.getInstance(project).reformatText(file, Collections.singletonList(formatTextRange));
            return Result.STOP;
        }

        return Result.CONTINUE;
    }

    @NotNull
    private static Result handleCompletionTriggerTyped(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile file) {
        // Just format the completion trigger
        int offset = editor.getCaretModel().getOffset();
        // NOTE: Right now all completion triggers are single characters, so this is safe/accurate
        int beforeOffset = offset - 1;
        CodeStyleManager.getInstance(project).reformatText(file, beforeOffset, offset);
        return Result.STOP;
    }
}
