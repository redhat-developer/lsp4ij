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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature.FormattingScope;
import com.redhat.devtools.lsp4ij.features.codeBlockProvider.LSPCodeBlockProvider;
import com.redhat.devtools.lsp4ij.features.completion.LSPCompletionTriggerTypedHandler;
import com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;

import static com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase.expandToWholeLinesWithBlanks;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;

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
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            // The file is not associated to a language server
            return super.charTyped(charTyped, project, editor, file);
        }

        LSPFormattingFeature formattingFeature = getFormattingFeature(file);
        if (formattingFeature != null) {
            boolean rangeFormattingSupported = formattingFeature.isRangeFormattingSupported(file);

            // Close braces
            if (formattingFeature.isFormatOnCloseBrace(file) &&
                // Make sure the formatter supports formatting of the configured scope
                ((formattingFeature.getFormatOnCloseBraceScope(file) == FormattingScope.FILE) || rangeFormattingSupported)) {
                Map.Entry<Character, Character> bracePair = ContainerUtil.find(
                        LSPIJEditorUtils.getBracePairs(file).entrySet(),
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
            if (formattingFeature.isFormatOnStatementTerminator(file) &&
                // Make sure the formatter supports formatting of the configured scope
                ((formattingFeature.getFormatOnStatementTerminatorScope(file) == FormattingScope.FILE) || rangeFormattingSupported)) {
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
                // Make sure the formatter supports range formatting
                rangeFormattingSupported &&
                // It must be a completion trigger character for the language no matter what
                LSPCompletionTriggerTypedHandler.hasLanguageServerSupportingCompletionTriggerCharacters(charTyped, project, file)) {
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
    private static LSPFormattingFeature getFormattingFeature(@NotNull PsiFile file) {
        Project project = file.getProject();
        VirtualFile virtualFile = file.getVirtualFile();

        CompletableFuture<@NotNull List<LanguageServerItem>> languageServersFuture = LanguageServiceAccessor.getInstance(project).getLanguageServers(
                virtualFile,
                // Client-side on-type formatting shouldn't trigger a language server to start
                f -> (f.getServerStatus() == ServerStatus.started) &&
                     f.getFormattingFeature().isEnabled(file) &&
                     // Must support formatting
                     f.getFormattingFeature().isFormattingSupported(file),
                null
        );

        //noinspection TryWithIdenticalCatches
        try {
            // wait few ms to get formatting features to avoid freezing UI
            // when server started and didOpen must occur.
            languageServersFuture.get(500, TimeUnit.MILLISECONDS);
        } catch (ProcessCanceledException e) {
            return null;
        } catch (CancellationException e) {
            return null;
        } catch (ExecutionException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (TimeoutException e) {
            return null;
        }

        if (!isDoneNormally(languageServersFuture)) {
            return null;
        }

        // Just return the first matching language server, if any
        List<LanguageServerItem> languageServers = languageServersFuture.getNow(Collections.emptyList());
        LanguageServerItem languageServer = ContainerUtil.getFirstItem(languageServers);
        return languageServer != null ? languageServer.getClientFeatures().getFormattingFeature() : null;
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
