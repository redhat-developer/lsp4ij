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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature.FormattingScope;
import com.redhat.devtools.lsp4ij.client.features.LSPOnTypeFormattingFeature;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.codeBlockProvider.LSPCodeBlockProvider;
import com.redhat.devtools.lsp4ij.features.completion.LSPCompletionTriggerTypedHandler;
import com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings.ClientSideOnTypeFormattingSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase.expandToWholeLinesWithBlanks;

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
        if (!ProjectIndexingManager.isIndexing(project)) {
            // Gather all of the relevant client configuration
            Ref<Boolean> rangeFormattingSupportedRef = Ref.create(false);
            ClientSideOnTypeFormattingSettings onTypeFormattingSettings = new ClientSideOnTypeFormattingSettings();
            LanguageServiceAccessor.getInstance(project).processLanguageServers(
                    file,
                    ls -> {
                        // Only include servers that support formatting and don't support server-side on-type formatting
                        LSPClientFeatures clientFeatures = ls.getClientFeatures();
                        LSPFormattingFeature formattingFeature = clientFeatures.getFormattingFeature();
                        LSPOnTypeFormattingFeature onTypeFormattingFeature = clientFeatures.getOnTypeFormattingFeature();
                        if (formattingFeature.isEnabled(file) && formattingFeature.isSupported(file) &&
                                (!onTypeFormattingFeature.isEnabled(file) || !onTypeFormattingFeature.isSupported(file))) {
                            rangeFormattingSupportedRef.set(rangeFormattingSupportedRef.get() || formattingFeature.isRangeFormattingSupported(file));

                            onTypeFormattingSettings.formatOnCloseBrace |= formattingFeature.isFormatOnCloseBrace(file);
                            FormattingScope formatOnCloseBraceScope = formattingFeature.getFormatOnCloseBraceScope(file);
                            if (formatOnCloseBraceScope.compareTo(onTypeFormattingSettings.formatOnCloseBraceScope) > 0) {
                                onTypeFormattingSettings.formatOnCloseBraceScope = formatOnCloseBraceScope;
                            }
                            onTypeFormattingSettings.formatOnCloseBraceCharacters += formattingFeature.getFormatOnCloseBraceCharacters(file);

                            onTypeFormattingSettings.formatOnStatementTerminator |= formattingFeature.isFormatOnStatementTerminator(file);
                            FormattingScope formatOnStatementTerminatorScope = formattingFeature.getFormatOnStatementTerminatorScope(file);
                            if (formatOnStatementTerminatorScope.compareTo(onTypeFormattingSettings.formatOnStatementTerminatorScope) > 0) {
                                onTypeFormattingSettings.formatOnStatementTerminatorScope = formatOnStatementTerminatorScope;
                            }
                            onTypeFormattingSettings.formatOnStatementTerminatorCharacters += formattingFeature.getFormatOnStatementTerminatorCharacters(file);

                            onTypeFormattingSettings.formatOnCompletionTrigger |= formattingFeature.isFormatOnCompletionTrigger(file);
                            onTypeFormattingSettings.formatOnCompletionTriggerCharacters += formattingFeature.getFormatOnCompletionTriggerCharacters(file);
                        }
                    }
            );
            boolean rangeFormattingSupported = rangeFormattingSupportedRef.get();

            // Close braces
            if (onTypeFormattingSettings.formatOnCloseBrace &&
                    // Make sure the formatter supports formatting of the configured scope
                    ((onTypeFormattingSettings.formatOnCloseBraceScope == FormattingScope.FILE) || rangeFormattingSupported)) {
                Map.Entry<Character, Character> bracePair = ContainerUtil.find(
                        LSPIJEditorUtils.getBracePairs(file).entrySet(),
                        entry -> entry.getValue() == charTyped
                );
                if (bracePair != null) {
                    Character openBraceChar = bracePair.getKey();
                    Character closeBraceChar = bracePair.getValue();
                    if (StringUtil.isEmpty(onTypeFormattingSettings.formatOnCloseBraceCharacters) ||
                            (onTypeFormattingSettings.formatOnCloseBraceCharacters.indexOf(closeBraceChar) > -1)) {
                        return handleCloseBraceTyped(
                                project,
                                editor,
                                file,
                                onTypeFormattingSettings.formatOnCloseBraceScope,
                                openBraceChar,
                                closeBraceChar
                        );
                    }
                }
            }

            // Statement terminators
            if (onTypeFormattingSettings.formatOnStatementTerminator &&
                    // Make sure the formatter supports formatting of the configured scope
                    ((onTypeFormattingSettings.formatOnStatementTerminatorScope == FormattingScope.FILE) || rangeFormattingSupported)) {
                if (StringUtil.isNotEmpty(onTypeFormattingSettings.formatOnStatementTerminatorCharacters) &&
                        (onTypeFormattingSettings.formatOnStatementTerminatorCharacters.indexOf(charTyped) > -1)) {
                    return handleStatementTerminatorTyped(
                            project,
                            editor,
                            file,
                            onTypeFormattingSettings.formatOnStatementTerminatorScope,
                            charTyped
                    );
                }
            }

            // Completion triggers
            if (onTypeFormattingSettings.formatOnCompletionTrigger &&
                    // Make sure the formatter supports range formatting
                    rangeFormattingSupported &&
                    // It must be a completion trigger character for the language no matter what
                    LSPCompletionTriggerTypedHandler.hasLanguageServerSupportingCompletionTriggerCharacters(charTyped, project, file)) {
                // But the subset that should trigger completion can be configured
                if (StringUtil.isEmpty(onTypeFormattingSettings.formatOnCompletionTriggerCharacters) ||
                        (onTypeFormattingSettings.formatOnCompletionTriggerCharacters.indexOf(charTyped) > -1)) {
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

    @NotNull
    private static Result handleCloseBraceTyped(@NotNull Project project,
                                                @NotNull Editor editor,
                                                @NotNull PsiFile file,
                                                @NotNull FormattingScope formattingScope,
                                                char openBraceChar,
                                                char closeBraceChar) {
        TextRange formatTextRange = null;

        // Statement-level scope is not supported for code blocks
        if (formattingScope == FormattingScope.STATEMENT) {
            return Result.CONTINUE;
        }

        // If appropriate, find the code block that was closed by the brace
        if (formattingScope == FormattingScope.CODE_BLOCK) {
            int offset = editor.getCaretModel().getOffset();
            int beforeOffset = offset - 1;
            TextRange codeBlockRange = LSPCodeBlockProvider.getCodeBlockRange(editor, file, beforeOffset);
            if (codeBlockRange != null) {
                Document document = editor.getDocument();
                CharSequence documentChars = document.getCharsSequence();

                // Constrain the offsets to the valid range of the file
                int startOffset = Math.max(codeBlockRange.getStartOffset(), 0);
                int endOffset = Math.min(codeBlockRange.getEndOffset(), documentChars.length() - 1);

                // Make sure the range includes the brace pair
                if ((startOffset > 0) && (documentChars.charAt(startOffset) != openBraceChar)) {
                    startOffset--;
                }
                if ((endOffset > 0) && (documentChars.charAt(endOffset) != closeBraceChar) && (documentChars.charAt(endOffset - 1) == closeBraceChar)) {
                    endOffset--;
                }
                if ((endOffset < (documentChars.length() - 1)) && (documentChars.charAt(endOffset) != closeBraceChar)) {
                    endOffset++;
                }

                // If the range is now the braced block, format it
                if ((documentChars.charAt(startOffset) == openBraceChar) && (documentChars.charAt(endOffset) == closeBraceChar)) {
                    // If appropriate, make sure that the range includes the close brace that was just typed
                    formatTextRange = TextRange.create(startOffset, endOffset == beforeOffset ? offset : endOffset);
                }
            }
        }

        // If appropriate, use the file text range
        else if (formattingScope == FormattingScope.FILE) {
            formatTextRange = file.getTextRange();
        }

        // If we have a text range now, format it
        if (formatTextRange != null) {
            format(project, file, formatTextRange);
            return Result.STOP;
        }

        return Result.CONTINUE;
    }

    @NotNull
    private static Result handleStatementTerminatorTyped(@NotNull Project project,
                                                         @NotNull Editor editor,
                                                         @NotNull PsiFile file,
                                                         @NotNull FormattingScope formattingScope,
                                                         char statementTerminatorChar) {
        TextRange formatTextRange = null;

        int offset = editor.getCaretModel().getOffset();
        int beforeOffset = offset - 1;

        // If appropriate, find the statement that was just terminated
        if (formattingScope == FormattingScope.STATEMENT) {
            List<TextRange> selectionTextRanges = LSPSelectionRangeSupport.getSelectionTextRanges(file, editor, beforeOffset);
            if (!ContainerUtil.isEmpty(selectionTextRanges)) {
                Document document = editor.getDocument();
                CharSequence documentChars = document.getCharsSequence();

                // Extend these to full lines for evaluation below
                Set<TextRange> extendedSelectionTextRanges = new LinkedHashSet<>();
                for (TextRange selectionTextRange : selectionTextRanges) {
                    ContainerUtil.addAllNotNull(extendedSelectionTextRanges, expandToWholeLinesWithBlanks(documentChars, selectionTextRange));
                }

                // Find the closest selection range that is extended to line start/end; that should be the statement
                formatTextRange = ContainerUtil.find(
                        extendedSelectionTextRanges,
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
                // If we found a statement text range, make sure that we include only up to the terminator as some
                // formatters will react to additional trailing whitespace to do things like open an extraneous newline
                if (formatTextRange != null) {
                    formatTextRange = TextRange.create(formatTextRange.getStartOffset(), offset);
                }
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
            format(project, file, formatTextRange);
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
        format(project, file, TextRange.create(beforeOffset, offset));
        return Result.STOP;
    }

    private static void format(@NotNull Project project,
                               @NotNull PsiFile file,
                               @NotNull TextRange textRange) {
        // If formatting the entire file, don't specify a range
        if (textRange.equals(file.getTextRange())) {
            CodeStyleManager.getInstance(project).reformat(file);
        } else {
            CodeStyleManager.getInstance(project).reformatText(file, Collections.singletonList(textRange));
        }
    }
}
