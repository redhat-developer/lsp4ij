/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.highlight;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;


/**
 * LSP implementation of {@link HighlightUsagesHandlerFactory} to support
 * <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentHighlight>LSP 'textDocument/highlight'</a>.
 */
public class LSPHighlightUsagesHandlerFactory implements HighlightUsagesHandlerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSPHighlightUsagesHandlerFactory.class);

    @Override
    public @Nullable HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file) {
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            return null;
        }
        if (ProjectIndexingManager.getInstance(file.getProject()).isIndexing()) {
            return null;
        }
        List<LSPHighlightPsiElement> targets = getTargets(editor, file);
        return targets.isEmpty() ? null : new LSPHighlightUsagesHandler(editor, file, targets);
    }

    private List<LSPHighlightPsiElement> getTargets(Editor editor, PsiFile psiFile) {
        VirtualFile file = LSPIJUtils.getFile(psiFile);
        if (file == null) {
            return Collections.emptyList();
        }

        ProgressManager.checkCanceled();

        // Create DocumentHighlightParams
        Document document = editor.getDocument();
        int offset = TargetElementUtil.adjustOffset(psiFile, document, editor.getCaretModel().getOffset());

        // Consume LSP 'textDocument/documentHighlight' request
        var params = new LSPDocumentHighlightParams(LSPIJUtils.toTextDocumentIdentifier(file), LSPIJUtils.toPosition(offset, document), offset);
        LSPHighlightSupport highlightSupport = LSPFileSupport.getSupport(psiFile).getHighlightSupport();
        CompletableFuture<List<DocumentHighlight>> highlightFuture = highlightSupport.getHighlights(params);
        try {
            waitUntilDone(highlightFuture, psiFile);
        } catch (ProcessCanceledException e) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            highlightSupport.cancel();
            return Collections.emptyList();
        } catch (CancellationException e) {
            // cancel the LSP requests textDocument/documentHighlight
            highlightSupport.cancel();
            return Collections.emptyList();
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/documentHighlight' request", e);
            return Collections.emptyList();
        }

        if (isDoneNormally(highlightFuture)) {
            // textDocument/highlight has been collected correctly, create list of LSPHighlightPsiElement from LSP DocumentHighlight list
            List<DocumentHighlight> highlights = highlightFuture.getNow(null);
            if (highlights != null) {
                List<LSPHighlightPsiElement> elements = new ArrayList<>();
                highlights
                        .forEach(highlight -> {
                            TextRange textRange = LSPIJUtils.toTextRange(highlight.getRange(), document);
                            if (textRange != null) {
                                // According LSP spec, the default highlight kind is DocumentHighlightKind.Text.
                                DocumentHighlightKind kind = highlight.getKind() != null ? highlight.getKind() : DocumentHighlightKind.Text;
                                elements.add(new LSPHighlightPsiElement(psiFile, textRange, kind));
                            }
                        });
                return elements;
            }
        }
        return Collections.emptyList();
    }
}