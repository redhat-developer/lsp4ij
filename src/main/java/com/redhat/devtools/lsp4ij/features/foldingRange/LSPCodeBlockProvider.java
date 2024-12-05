/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.foldingRange;

import com.intellij.codeInsight.editorActions.CodeBlockProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.lsp4j.FoldingRange;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeBuilder.*;

/**
 * Code block provider that uses the folding range information from {@link LSPFoldingRangeBuilder}.
 */
public class LSPCodeBlockProvider implements CodeBlockProvider {

    @Override
    public @Nullable TextRange getCodeBlockRange(Editor editor, PsiFile file) {
        List<FoldingRange> foldingRanges = LSPFoldingRangeBuilder.getFoldingRanges(file);
        if (!ContainerUtil.isEmpty(foldingRanges)) {
            Document document = editor.getDocument();
            String documentText = document.getText();
            int documentLength = documentText.length();

            // Adjust the offset slightly based on before/after brace
            int offset = editor.getCaretModel().getOffset();
            Character beforeCharacter = offset > 0 ? documentText.charAt(offset - 1) : null;
            Character afterCharacter = offset < documentLength ? documentText.charAt(offset) : null;
            if (isOpenBraceChar(afterCharacter)) {
                offset++;
            } else if (isCloseBraceChar(beforeCharacter)) {
                offset--;
            }

            // See if we're anchored by a known brace character
            Character openBraceChar = null;
            Character closeBraceChar = null;
            if ((offset > 0) && isOpenBraceChar(documentText.charAt(offset - 1))) {
                openBraceChar = documentText.charAt(offset - 1);
                closeBraceChar = getCloseBraceChar(openBraceChar);
            } else if ((offset < (documentLength - 1)) && isCloseBraceChar(documentText.charAt(offset + 1))) {
                closeBraceChar = documentText.charAt(offset + 1);
                openBraceChar = getOpenBraceChar(closeBraceChar);
            }

            List<TextRange> containingTextRanges = new ArrayList<>(foldingRanges.size());
            for (FoldingRange foldingRange : foldingRanges) {
                TextRange textRange = LSPFoldingRangeBuilder.getTextRange(foldingRange, document, openBraceChar, closeBraceChar);
                if (textRange != null) {
                    // If this is the exact range for which a matching brace was requested, return it
                    if ((textRange.getStartOffset() == offset) || (textRange.getEndOffset() == offset)) {
                        return textRange;
                    }
                    // Otherwise add it to the list of containing ranges and we'll find the smallest at the end
                    else if (textRange.contains(offset)) {
                        containingTextRanges.add(textRange);
                    }
                }
            }

            // If we made it here and found containing text ranges, return the smallest one
            if (!ContainerUtil.isEmpty(containingTextRanges)) {
                containingTextRanges.sort(Comparator.comparingInt(TextRange::getLength));
                return ContainerUtil.getFirstItem(containingTextRanges);
            }
        }

        return null;
    }
}
