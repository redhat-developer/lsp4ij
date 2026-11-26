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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;

/**
 * Shared utility class for LSP on-type formatting.
 */
final class LSPServerSideOnTypeFormattingHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPServerSideOnTypeFormattingHelper.class);

    /**
     * How long the typing waits for the edits of the language servers, in its write action on the UI thread, before it
     * leaves the document as typed.
     */
    private static final long ON_TYPE_FORMATTING_TIMEOUT_MS = 500L;

    // The wait of the typing for the edits, which only a test changes
    private static volatile long onTypeFormattingTimeoutMs = ON_TYPE_FORMATTING_TIMEOUT_MS;

    private LSPServerSideOnTypeFormattingHelper() {
        // Pure utility class
    }

    /**
     * Applies LSP on-type formatting for the provide parameters if possible/applicable.
     *
     * @param charTyped the type character
     * @param editor    the editor
     * @param file      the PSI file
     * @return true if on-type formatting was applied; otherwise false
     */
    static boolean applyOnTypeFormatting(char charTyped,
                                         @NotNull Editor editor,
                                         @NotNull PsiFile file) {
        if (ProjectIndexingManager.isIndexingAll() || !LanguageServersRegistry.getInstance().isFileSupported(file)) {
            return false;
        }
        if (!hasLanguageServerSupportingOnTypeFormatting(file)) {
            // The file is not associated to a language server which supports on type formatting
            return false;
        }
        // If so, issue a request for on-type formatting
        int offset = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();
        Position position = LSPIJUtils.toPosition(offset, document);
        DocumentOnTypeFormattingParams onTypeFormattingParams = new DocumentOnTypeFormattingParams(
                new TextDocumentIdentifier(),
                new FormattingOptions(LSPIJUtils.getTabSize(editor), LSPIJUtils.isInsertSpaces(editor)),
                position,
                String.valueOf(charTyped)
        );
        LSPOnTypeFormattingSupport onTypeFormattingSupport = LSPFileSupport.getSupport(file).getOnTypeFormattingSupport();
        CompletableFuture<List<TextEdit>> onTypeFormattingFutures = onTypeFormattingSupport.onTypeFormatting(onTypeFormattingParams);
        long timeoutMs = onTypeFormattingTimeoutMs;
        try {
            onTypeFormattingFutures.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (CancellationException e) {
            // Also a ProcessCanceledException, which extends CancellationException since IntelliJ 2024.2
            onTypeFormattingSupport.cancel();
            return false;
        } catch (TimeoutException e) {
            LOGGER.warn("On-type formatting timed out after {}ms in WriteAction", timeoutMs);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return false;
        }

        if (!isDoneNormally(onTypeFormattingFutures)) {
            return false;
        }

        // If on-type formatting resulted in text edits, apply them
        List<TextEdit> textEdits = onTypeFormattingFutures.getNow(Collections.emptyList());
        if (ContainerUtil.isEmpty(textEdits)) {
            return false;
        }

        LSPIJUtils.applyEdits(editor, document, textEdits);
        return true;
    }

    /**
     * Sets how long the typing waits for the edits of the language servers, in its write action on the UI thread,
     * instead of {@value #ON_TYPE_FORMATTING_TIMEOUT_MS} ms, until the given disposable is disposed, which restores the
     * wait it replaced. A typing which gets no answer holds the UI thread that long.
     *
     * @param timeout          how long the typing waits for the edits.
     * @param parentDisposable the disposable whose disposal restores the previous wait.
     */
    @TestOnly
    static void setOnTypeFormattingTimeout(@NotNull Duration timeout, @NotNull Disposable parentDisposable) {
        long previous = onTypeFormattingTimeoutMs;
        // Registered first: Disposer refuses a disposed parent, which then leaves the wait as it is
        Disposer.register(parentDisposable, () -> onTypeFormattingTimeoutMs = previous);
        onTypeFormattingTimeoutMs = timeout.toMillis();
    }

    private static boolean hasLanguageServerSupportingOnTypeFormatting(@NotNull PsiFile file) {
        return LanguageServiceAccessor.getInstance(file.getProject())
                .hasAny(file, ls -> ls.getClientFeatures()
                        .getOnTypeFormattingFeature()
                        .isSupported(file));
    }

}
