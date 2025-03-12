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

package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.TextRangeUtil;
import com.redhat.devtools.lsp4ij.features.codeBlockProvider.LSPCodeBlockProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Composite extend word selection handler for contents of LSP-defined code blocks that ensures that we only have to
 * retrieve the code block text range once for all constituent selectioners.
 */
public class LSPCodeBlockExtendWordSelectionHandler extends AbstractLSPExtendWordSelectionHandler {

    // This is a composite implementation that derives its information from each of the following
    private final List<LSPCodeBlockSelectioner> codeBlockSelectioners = List.of(
            new LSPCodeBlockStatementGroupSelectioner(),
            new LSPCodeBlockBodySelectioner()
    );

    @Override
    @Nullable
    public final List<TextRange> select(@NotNull PsiElement element,
                                        @NotNull CharSequence editorText,
                                        int offset,
                                        @NotNull Editor editor) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }

        Document document = editor.getDocument();
        int currentLineNumber = document.getLineNumber(offset);

        // If necessary, move to the first non-whitespace character in the line for finding the code block
        int currentLineStartOffset = document.getLineStartOffset(currentLineNumber);
        int currentLineEndOffset = document.getLineEndOffset(currentLineNumber);
        int effectiveOffset = Math.max(offset, currentLineStartOffset);
        while ((effectiveOffset <= currentLineEndOffset) &&
                (effectiveOffset < editorText.length()) &&
                Character.isWhitespace(editorText.charAt(effectiveOffset))) {
            effectiveOffset++;
        }

        // Get the current code block once
        TextRange codeBlockRange = LSPCodeBlockProvider.getCodeBlockRange(editor, file, effectiveOffset);
        if (codeBlockRange == null) {
            return null;
        }

        // Add text ranges from all constituent selectioners
        Set<TextRange> textRanges = new LinkedHashSet<>();
        for (LSPCodeBlockSelectioner codeBlockSelectioner : codeBlockSelectioners) {
            List<TextRange> textRangesForSelectioner = codeBlockSelectioner.getTextRanges(
                    file,
                    editor,
                    editorText,
                    codeBlockRange,
                    currentLineNumber
            );
            if (!ContainerUtil.isEmpty(textRangesForSelectioner)) {
                ContainerUtil.addAllNotNull(textRanges, textRangesForSelectioner);
            }
        }
        if (textRanges.isEmpty()) {
            return null;
        }

        List<TextRange> sortedTextRanges = new ArrayList<>(textRanges);
        sortedTextRanges.sort(TextRangeUtil.RANGE_COMPARATOR);
        return sortedTextRanges;
    }
}
