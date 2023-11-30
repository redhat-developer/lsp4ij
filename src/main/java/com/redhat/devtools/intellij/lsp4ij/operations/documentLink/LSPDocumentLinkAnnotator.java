/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.documentLink;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LSPVirtualFileData;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.intellij.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.intellij.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Intellij {@link ExternalAnnotator} implementation which collect LSP document links and display them with underline style.
 */
public class LSPDocumentLinkAnnotator extends ExternalAnnotator<List<LSPVirtualFileData>, List<LSPVirtualFileData>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDocumentLinkAnnotator.class);

    @Nullable
    @Override
    public List<LSPVirtualFileData> collectInformation(@NotNull PsiFile psiFile, @NotNull Editor editor, boolean hasErrors) {
        Document document = editor.getDocument();
        VirtualFile file = LSPIJUtils.getFile(document);
        if (file == null) {
            return null;
        }
        List<LSPVirtualFileData> datas = new ArrayList<>();
        final CancellationSupport cancellationSupport = new CancellationSupport();
        try {
            ProgressManager.checkCanceled();

            URI uri = LSPIJUtils.toUri(file);
            DocumentLinkParams params = new DocumentLinkParams(LSPIJUtils.toTextDocumentIdentifier(uri));

            Project project = editor.getProject();
            BlockingDeque<Pair<List<DocumentLink>, LanguageServerWrapper>> documentLinks = new LinkedBlockingDeque<>();
            CompletableFuture<Void> future = LanguageServiceAccessor.getInstance(project).getLanguageServers(file,
                            capabilities -> capabilities.getDocumentLinkProvider() != null)
                    .thenAcceptAsync(languageServers ->
                            cancellationSupport.execute(CompletableFuture.allOf(languageServers.stream()
                                    .map(languageServer -> Pair.pair(
                                            cancellationSupport.execute(languageServer.getServer().getTextDocumentService().documentLink(params))
                                            , languageServer.getServerWrapper()))
                                    .map(request -> request.getFirst().thenAcceptAsync(result -> {
                                        if (result != null) {
                                            documentLinks.add(Pair.pair(result, request.getSecond()));
                                        }
                                    })).toArray(CompletableFuture[]::new))));
            while (!future.isDone() || !documentLinks.isEmpty()) {
                ProgressManager.checkCanceled();
                Pair<List<DocumentLink>, LanguageServerWrapper> links = documentLinks.poll(25, TimeUnit.MILLISECONDS);
                if (links != null) {
                    LSPVirtualFileData data = links.getSecond().getLSPVirtualFileData(uri);
                    if (data != null) {
                        data.updateDocumentLink(links.getFirst());
                        datas.add(data);
                    }
                }
            }
        } catch (ProcessCanceledException cancellation) {
            cancellationSupport.cancel();
            throw cancellation;
        } catch (InterruptedException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
            Thread.currentThread().interrupt();
        }
        return datas;
    }

    @Override
    public @Nullable List<LSPVirtualFileData> doAnnotate(List<LSPVirtualFileData> wrapper) {
        return wrapper;
    }

    @Override
    public void apply(@NotNull PsiFile file, List<LSPVirtualFileData> datas, @NotNull AnnotationHolder holder) {
        if (datas.isEmpty()) {
            return;
        }
        Document document = LSPIJUtils.getDocument(file.getVirtualFile());
        for (var data : datas) {
            var documentLinkPerServer = data.getDocumentLinkForServer();
            for (var documentLink : documentLinkPerServer.getDocumentLinks()) {
                TextRange range = LSPIJUtils.toTextRange(documentLink.getRange(), document);
                holder.newSilentAnnotation(HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY)
                        .range(range)
                        .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                        .create();
            }
        }
    }
}
