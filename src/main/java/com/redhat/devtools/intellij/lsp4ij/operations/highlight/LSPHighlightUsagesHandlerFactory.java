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
package com.redhat.devtools.intellij.lsp4ij.operations.highlight;

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
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.intellij.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LSPHighlightUsagesHandlerFactory implements HighlightUsagesHandlerFactory {
    private static final Logger LOGGER = Logger.getLogger(LSPHighlightUsagesHandlerFactory.class.getName());

    @Override
    public @Nullable HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file) {
        List<LSPHighlightPsiElement> targets = getTargets(editor, file);
        return targets.isEmpty() ? null : new LSPHighlightUsagesHandler(editor, file, targets);
    }

    private List<LSPHighlightPsiElement> getTargets(Editor editor, PsiFile psiFile) {
        VirtualFile file = LSPIJUtils.getFile(psiFile);
        if (file == null) {
            return Collections.emptyList();
        }
        List<LSPHighlightPsiElement> elements = new ArrayList<>();
        final CancellationSupport cancellationSupport = new CancellationSupport();
        try {
            Document document = editor.getDocument();
            int offset = TargetElementUtil.adjustOffset(psiFile, document, editor.getCaretModel().getOffset());
            Position position = LSPIJUtils.toPosition(offset, document);

            ProgressManager.checkCanceled();

            String uri = LSPIJUtils.toUriAsString(file);
            TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
            DocumentHighlightParams params = new DocumentHighlightParams(identifier, position);
            BlockingDeque<DocumentHighlight> highlights = new LinkedBlockingDeque<>();

            CompletableFuture<Void> future = LanguageServiceAccessor.getInstance(editor.getProject())
                    .getLanguageServers(file, capabilities -> LSPIJUtils.hasCapability(capabilities.getDocumentHighlightProvider()))
                    .thenAcceptAsync(languageServers ->
                            cancellationSupport.execute(CompletableFuture.allOf(languageServers.stream()
                                    .map(languageServer -> cancellationSupport.execute(languageServer.getServer().getTextDocumentService().documentHighlight(params)))
                                    .map(request -> request.thenAcceptAsync(result -> {
                                        if (result != null) {
                                            highlights.addAll(result);
                                        }
                                    })).toArray(CompletableFuture[]::new))));
            while (!future.isDone() || !highlights.isEmpty()) {
                ProgressManager.checkCanceled();
                DocumentHighlight highlight = highlights.poll(25, TimeUnit.MILLISECONDS);
                if (highlight != null) {
                    TextRange textRange = LSPIJUtils.toTextRange(highlight.getRange(), document);
                    if (textRange != null) {
                        elements.add(new LSPHighlightPsiElement(textRange, highlight.getKind()));
                    }
                }
            }
        } catch (ProcessCanceledException cancellation) {
            cancellationSupport.cancel();
            throw cancellation;
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, e, e::getLocalizedMessage);
        }
        return elements;

    }
}