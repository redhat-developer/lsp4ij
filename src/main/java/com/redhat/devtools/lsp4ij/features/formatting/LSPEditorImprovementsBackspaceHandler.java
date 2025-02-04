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

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.client.features.EditorBehaviorFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Improves handling of backspace for the escaped opening character of a paired set, specifically quote characters and
 * brace pairs.
 */
public class LSPEditorImprovementsBackspaceHandler extends BackspaceHandlerDelegate {

    @Override
    public void beforeCharDeleted(char charDeleted,
                                  @NotNull PsiFile file,
                                  @NotNull Editor editor) {
        // No-op
    }

    @Override
    public boolean charDeleted(
            char charDeleted,
            @NotNull PsiFile file,
            @NotNull Editor editor) {
        if (LSPIJEditorUtils.isSupportedAbstractFileTypeOrTextMateFile(file) &&
            EditorBehaviorFeature.enableStringLiteralImprovements(file) &&
            isPairOpener(file, charDeleted)) {
            Character pairCloser = getPairCloser(file, charDeleted);
            if (pairCloser != null) {
                int offset = editor.getCaretModel().getOffset();
                Document document = editor.getDocument();
                if (offset < document.getTextLength()) {
                    CharSequence charsSequence = document.getCharsSequence();
                    char nextCharacter = charsSequence.charAt(offset);
                    if (pairCloser == nextCharacter) {
                        // If it's escaped, only delete the requested character
                        if (offset > 0) {
                            char previousCharacter = charsSequence.charAt(offset - 1);
                            if (previousCharacter == '\\') {
                                document.replaceString(offset, offset, "");
                                return true;
                            }
                        }

                        // Otherwise delete the pair
                        document.replaceString(offset, offset + 1, "");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isPairOpener(@NotNull PsiFile file,
                                        char charDeleted) {
        return LSPIJEditorUtils.isQuoteCharacter(file, charDeleted) ||
               LSPIJEditorUtils.isOpenBraceCharacter(file, charDeleted);
    }

    @Nullable
    private static Character getPairCloser(@NotNull PsiFile file,
                                           char charDeleted) {
        return LSPIJEditorUtils.isQuoteCharacter(file, charDeleted) ?
                Character.valueOf(charDeleted) :
                LSPIJEditorUtils.getCloseBraceCharacter(file, charDeleted);
    }
}
