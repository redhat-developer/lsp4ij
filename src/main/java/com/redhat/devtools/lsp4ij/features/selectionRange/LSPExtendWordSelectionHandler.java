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
package com.redhat.devtools.lsp4ij.features.selectionRange;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase.expandToWholeLinesWithBlanks;

/**
 * Implementation of the IDE's extendWordSelectionHandler EP for LSP4IJ files against textDocument/selectionRange.
 */
public class LSPExtendWordSelectionHandler extends AbstractLSPExtendWordSelectionHandler {

    @Override
    public boolean canSelect(@NotNull PsiElement element) {
        if (!super.canSelect(element)) {
            return false;
        }

        // These should all be safely non-null now
        Project project = element.getProject();
        PsiFile file = element.getContainingFile();
        VirtualFile virtualFile = LSPIJUtils.getFile(element);

        // Only if textDocument/selectionRange is supported for the file
        //noinspection DataFlowIssue
        return LanguageServiceAccessor.getInstance(project)
                .hasAny(virtualFile, ls -> ls.getClientFeatures().getSelectionRangeFeature().isSelectionRangeSupported(file));
    }

    @Override
    @Nullable
    public List<TextRange> select(@NotNull PsiElement element,
                                  @NotNull CharSequence editorText,
                                  int offset,
                                  @NotNull Editor editor) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }

        // If the caret is at a line start, try to find the first non-whitespace character in the line and get the
        // selection ranges for it
        int effectiveOffset = offset;
        Document document = editor.getDocument();
        int lineNumber = document.getLineNumber(offset);
        int lineStartOffset = document.getLineStartOffset(lineNumber);
        if (offset == lineStartOffset) {
            int lineEndOffset = document.getLineEndOffset(lineNumber);
            int selectionStartOffset = offset;
            while (Character.isWhitespace(editorText.charAt(selectionStartOffset)) && selectionStartOffset <= lineEndOffset) {
                selectionStartOffset++;
            }
            if (selectionStartOffset <= lineEndOffset) {
                effectiveOffset = selectionStartOffset;
            }
        }

        // Get the selection ranges and extend them to whole lines
        Set<TextRange> textRanges = new LinkedHashSet<>();
        List<TextRange> selectionTextRanges = LSPSelectionRangeSupport.getSelectionTextRanges(file, editor, effectiveOffset);
        for (TextRange selectionTextRange : selectionTextRanges) {
            ContainerUtil.addAllNotNull(textRanges, expandToWholeLinesWithBlanks(editorText, selectionTextRange));
        }

        // If the original offset was at line start and the effective offset was not, remove smaller text ranges
        if ((offset == lineStartOffset) && (offset != effectiveOffset)) {
            textRanges.removeIf(textRange -> textRange.getStartOffset() > lineStartOffset);
        }

        return new ArrayList<>(textRanges);
    }
}
