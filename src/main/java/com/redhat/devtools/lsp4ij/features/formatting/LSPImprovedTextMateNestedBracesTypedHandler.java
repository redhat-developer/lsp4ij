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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedEditorBehaviorFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.editor.TextMateEditorUtils;
import org.jetbrains.plugins.textmate.editor.TextMateTypedHandler;
import org.jetbrains.plugins.textmate.language.preferences.TextMateAutoClosingPair;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

/**
 * Improves on-type handling of unbalanced nested brace/bracket/paren pairs in TextMate files.
 */
public class LSPImprovedTextMateNestedBracesTypedHandler extends TypedHandlerDelegate {

    @Override
    @NotNull
    public Result charTyped(char charTyped,
                            @NotNull Project project,
                            @NotNull Editor editor,
                            @NotNull PsiFile file) {
        if (LSPIJEditorUtils.isSupportedTextMateFile(file) &&
            CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
            UserDefinedEditorBehaviorFeature.enableTextMateNestedBracesImprovements(file)) {
            int offset = editor.getCaretModel().getOffset();
            if (offset > 1) {
                TextMateScope scopeSelector = TextMateEditorUtils.getCurrentScopeSelector((EditorEx) editor);
                CharSequence sequence = editor.getDocument().getCharsSequence();
                TextMateAutoClosingPair autoInsertingPair = TextMateTypedHandler.findAutoInsertingPair(offset, sequence, scopeSelector);
                if (autoInsertingPair != null) {
                    // If this is a nested pair, close the new one; otherwise step forward
                    CharSequence pairOpen = autoInsertingPair.getLeft();
                    CharSequence pairClose = autoInsertingPair.getRight();

                    // Right now we only handle single character paired braces
                    if ((pairOpen.length() == 1) && (pairClose.length() == 1)) {
                        char openChar = pairOpen.charAt(0);
                        char closeChar = pairClose.charAt(0);

                        // Count nested open characters backward allowing for whitespace
                        int numOpenChars = 0;
                        for (int previousOffset = offset - 1; previousOffset >= 0; previousOffset--) {
                            char previousChar = sequence.charAt(previousOffset);
                            if (previousChar == openChar) {
                                numOpenChars++;
                            } else if (!Character.isWhitespace(previousChar)) {
                                break;
                            }
                        }

                        // And count nested close characters allowing for whitespace
                        int numCloseChars = 0;
                        for (int nextOffset = offset; nextOffset < sequence.length(); nextOffset++) {
                            char nextChar = sequence.charAt(nextOffset);
                            if (nextChar == closeChar) {
                                numCloseChars++;
                            } else if (!Character.isWhitespace(nextChar)) {
                                break;
                            }
                        }

                        // If there are more open characters than close characters, add a new close character
                        if (numOpenChars > numCloseChars) {
                            editor.getDocument().insertString(offset, pairClose.toString());
                            return Result.STOP;
                        }
                    }
                }
            }
        }

        return super.charTyped(charTyped, project, editor, file);
    }
}
