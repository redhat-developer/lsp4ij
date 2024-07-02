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
package com.redhat.devtools.lsp4ij.features.semanticTokens;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;


/**
 * LSP 'textDocument/semanticTokens support by implementing IntelliJ {@link HighlightVisitor}.
 *
 * <p>
 * Implementing {@link HighlightVisitor} gives the capability to have an existing highlighter (custom highlighter, TextMate highlighter)
 * and add semantic coloration.
 * </p>
 */
@ApiStatus.Internal
public class LSPSemanticTokensHighlightVisitor implements HighlightVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPSemanticTokensHighlightVisitor.class);

    private PsiFile file;

    private HighlightInfoHolder myHolder;

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
        return LanguageServersRegistry.getInstance().isFileSupported(file);
    }

    @Override
    public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable action) {
        myHolder = holder;
        if (LanguageServersRegistry.getInstance().isFileSupported(file)) {
            enableSemanticTokensHighlighting(file);
            myHolder = holder;
            try {
                action.run();
            }
            finally {
                myHolder = null;
            }
            return true;
        }
        return false;
    }

    @Override
    public void visit(@NotNull PsiElement element) {
        // This method is called for several PsiElements of the PsiFile
        // In the context of LSP semantic tokens, highlighting is performed
        // for the entire PsiFile and must be done just for the first call of this visit method
        // to have good performance.
        if (!isSemanticTokensHighlightingEnabled()) {
            // The file is already highlighted, ignore it
            return;
        }

        // Consume LSP 'textDocument/semanticTokens/full' request
        LSPSemanticTokensSupport semanticTokensSupport = LSPFileSupport.getSupport(file).getSemanticTokensSupport();
        var params = new SemanticTokensParams(LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile()));
        CompletableFuture<SemanticTokensData> semanticTokensFuture = semanticTokensSupport.getSemanticTokens(params);
        try {
            waitUntilDone(semanticTokensFuture, file);
        } catch (
                ProcessCanceledException e) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
            //TODO delete block when minimum required version is 2024.2
            semanticTokensSupport.cancel();
            return;
        } catch (CancellationException e) {
            // cancel the LSP requests textDocument/semanticTokens/full
            semanticTokensSupport.cancel();
            return;
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/semanticTokens/full' request", e);
            return;
        }

        if (isDoneNormally(semanticTokensFuture)) {
            // textDocument/semanticTokens/full has been collected correctly, create list of IJ HighlightInfo from LSP SemanticTokens data
            SemanticTokensData semanticTokens = semanticTokensFuture.getNow(null);
            if (semanticTokens != null) {
                var document = LSPIJUtils.getDocument(file.getVirtualFile());
                if (document == null) {
                    return;
                }
                semanticTokens.highlight(file, document, info -> addInfo(info));
                // LSP semantic token is highlighted, don't highlight it again for the next visited PsiElement
                disableSemanticTokensHighlighting();
            }
        }
    }

    private void enableSemanticTokensHighlighting(@NotNull PsiFile file) {
        this.file = file;
    }

    private void disableSemanticTokensHighlighting() {
        this.file = null;
    }

    private boolean isSemanticTokensHighlightingEnabled() {
        return file != null;
    }

    @Override
    public @NotNull HighlightVisitor clone() {
        return new LSPSemanticTokensHighlightVisitor();
    }

    protected void addInfo(@Nullable HighlightInfo highlightInfo) {
        myHolder.add(highlightInfo);
    }
}
