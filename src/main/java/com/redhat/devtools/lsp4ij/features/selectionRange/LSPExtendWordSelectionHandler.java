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

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase.expandToWholeLinesWithBlanks;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Implementation of the IDE's extendWordSelectionHandler EP for LSP4IJ files against textDocument/selectionRange.
 */
public class LSPExtendWordSelectionHandler implements ExtendWordSelectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPExtendWordSelectionHandler.class);

    @Override
    public boolean canSelect(@NotNull PsiElement element) {
        if (!element.isValid()) {
            return false;
        }

        PsiFile file = element.getContainingFile();
        if ((file == null) || !file.isValid()) {
            return false;
        }

        Project project = file.getProject();
        if (project.isDisposed()) {
            return false;
        }

        // Only if textDocument/selectionRange is supported for the file
        return LanguageServiceAccessor.getInstance(project)
                .hasAny(file.getVirtualFile(), ls -> ls.getClientFeatures().getSelectionRangeFeature().isSelectionRangeSupported(file));
    }

    @Override
    public @Nullable List<TextRange> select(@NotNull PsiElement element,
                                            @NotNull CharSequence editorText,
                                            int offset,
                                            @NotNull Editor editor) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }

        // Get the selection ranges
        Document document = editor.getDocument();
        List<SelectionRange> selectionRanges = getSelectionRanges(file, document, offset);
        if (ContainerUtil.isEmpty(selectionRanges)) {
            return null;
        }

        // Convert the selection ranges into text ranges
        Set<TextRange> textRanges = new LinkedHashSet<>(selectionRanges.size());
        for (SelectionRange selectionRange : selectionRanges) {
            TextRange selectionTextRange = getTextRange(selectionRange, document);
            textRanges.addAll(expandToWholeLinesWithBlanks(editorText, selectionTextRange));

            for (SelectionRange parentSelectionRange = selectionRange.getParent();
                 parentSelectionRange != null;
                 parentSelectionRange = parentSelectionRange.getParent()) {
                TextRange parentSelectionTextRange = getTextRange(parentSelectionRange, document);
                textRanges.addAll(expandToWholeLinesWithBlanks(editorText, parentSelectionTextRange));
            }
        }
        return new ArrayList<>(textRanges);
    }

    private static @NotNull List<SelectionRange> getSelectionRanges(@NotNull PsiFile file,
                                                                    @NotNull Document document,
                                                                    int offset) {
        if (ProjectIndexingManager.canExecuteLSPFeature(file) != ExecuteLSPFeatureStatus.NOW) {
            return Collections.emptyList();
        }

        // Consume LSP 'textDocument/selectionRanges' request
        LSPSelectionRangeSupport selectionRangeSupport = LSPFileSupport.getSupport(file).getSelectionRangeSupport();
        TextDocumentIdentifier textDocumentIdentifier = LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile());
        Position position = LSPIJUtils.toPosition(offset, document);
        var params = new LSPSelectionRangeParams(textDocumentIdentifier, Collections.singletonList(position), offset);
        CompletableFuture<List<SelectionRange>> selectionRangesFuture = selectionRangeSupport.getSelectionRanges(params);
        try {
            waitUntilDone(selectionRangesFuture, file);
        } catch (ProcessCanceledException e) {
            //Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            selectionRangeSupport.cancel();
            return Collections.emptyList();
        } catch (CancellationException e) {
            // cancel the LSP requests textDocument/selectionRanges
            selectionRangeSupport.cancel();
            return Collections.emptyList();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/selectionRanges' request", e);
            return Collections.emptyList();
        }

        if (!isDoneNormally(selectionRangesFuture)) {
            return Collections.emptyList();
        }

        // textDocument/selectionRanges has been collected correctly, create list of IJ SelectionDescriptor from LSP SelectionRange list
        return selectionRangesFuture.getNow(Collections.emptyList());
    }

    /**
     * Converts the LSP selection range into the IDE text range.
     *
     * @param selectionRange the LSP selection range
     * @param document       the document for for which the selection range applies
     * @return the corresponding text range
     */
    private static @NotNull TextRange getTextRange(@NotNull SelectionRange selectionRange,
                                                   @NotNull Document document) {
        Range range = selectionRange.getRange();
        Position start = range.getStart();
        Position end = range.getEnd();
        int startOffset = LSPIJUtils.toOffset(start, document);
        int endOffset = LSPIJUtils.toOffset(end, document);
        return TextRange.create(startOffset, endOffset);
    }
}
