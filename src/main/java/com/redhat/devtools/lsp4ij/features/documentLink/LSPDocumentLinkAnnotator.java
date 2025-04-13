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
package com.redhat.devtools.lsp4ij.features.documentLink;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.AbstractLSPExternalAnnotator;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Intellij {@link ExternalAnnotator} implementation which collect LSP document links and display them with underline style.
 */
public class LSPDocumentLinkAnnotator extends AbstractLSPExternalAnnotator<List<DocumentLinkData>, List<DocumentLinkData>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDocumentLinkAnnotator.class);

    private static final Key<Boolean> APPLIED_KEY = Key.create("lsp.documentLink.annotator.applied");

    public LSPDocumentLinkAnnotator() {
        super(APPLIED_KEY);
    }

    @Nullable
    @Override
    public List<DocumentLinkData> collectInformation(@NotNull PsiFile psiFile, @NotNull Editor editor, boolean hasErrors) {
        if (ProjectIndexingManager.canExecuteLSPFeature(psiFile) != ExecuteLSPFeatureStatus.NOW) {
            return null;
        }
        // Consume LSP 'textDocument/documentLink' request
        LSPDocumentLinkSupport documentLinkSupport = LSPFileSupport.getSupport(psiFile).getDocumentLinkSupport();
        var params = new DocumentLinkParams(LSPIJUtils.toTextDocumentIdentifier(psiFile.getVirtualFile()));
        documentLinkSupport.cancel();
        CompletableFuture<List<DocumentLinkData>> documentLinkFuture = documentLinkSupport.getDocumentLinks(params);
        try {
            waitUntilDone(documentLinkFuture, psiFile);
        } catch (ProcessCanceledException e) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            documentLinkSupport.cancel();
            return null;
        } catch (CancellationException e) {
            // cancel the LSP requests textDocument/documentLink
            documentLinkSupport.cancel();
            return null;
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/documentLink' request", e);
            return null;
        }

        if (isDoneNormally(documentLinkFuture)) {
            return documentLinkFuture.getNow(null);
        }
        return null;
    }

    @Override
    public @Nullable List<DocumentLinkData> doAnnotate(List<DocumentLinkData> documentLinks) {
        return documentLinks;
    }

    @Override
    public void doApply(@NotNull PsiFile file, @Nullable List<DocumentLinkData> documentLinks, @NotNull AnnotationHolder holder) {
        if (documentLinks == null || documentLinks.isEmpty()) {
            return;
        }
        Document document = LSPIJUtils.getDocument(file.getVirtualFile());
        if (document == null) {
            return;
        }
        for (var documentLink : documentLinks) {
            TextRange range = LSPIJUtils.toTextRange(documentLink.documentLink().getRange(), document);
            if (range != null) {
                holder.newSilentAnnotation(HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY)
                        .range(range)
                        .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                        .create();
            }
        }
    }
}
