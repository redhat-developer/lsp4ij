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
package com.redhat.devtools.lsp4ij.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Base class to consume LSP requests (ex : textDocument/codeLens) from all language servers applying to a given Psi file.
 *
 * @param <Params> the LSP requests parameters (ex : CodelensParams).
 * @param <Result> the LSP response results (ex : List<CodeLensData>).
 */
public abstract class AbstractLSPDocumentFeatureSupport<Params, Result> extends AbstractLSPFeatureSupport<Params, Result> {

    // The Psi file
    private final @NotNull PsiFile file;
    // The current modification stamp of the Psi file
    private long modificationStamp;

    // true if the future must be canceled when the Psi file is modified and false otherwise.
    private final boolean cancelWhenFileModified;

    public AbstractLSPDocumentFeatureSupport(@NotNull PsiFile file) {
        this(file, true);
    }

    public AbstractLSPDocumentFeatureSupport(@NotNull PsiFile file, boolean cancelWhenFileModified) {
        this.file = file;
        this.modificationStamp = -1;
        this.cancelWhenFileModified = cancelWhenFileModified;
    }

    /**
     * Returns the Psi file.
     *
     * @return the Psi file.
     */
    public @NotNull PsiFile getFile() {
        return file;
    }

    @Override
    protected boolean checkValid() {
        return !cancelWhenFileModified || this.file.getModificationStamp() == modificationStamp;
    }

    @Override
    public CompletableFuture<Result> getFeatureData(Params params) {
        if (!isValidLSPFuture()) {
            // - the LSP requests have never been executed
            // - or the LSP requests has failed
            //  - or the Psi file has been updated
            // --> consume LSP requests for all language servers applying to a given Psi file.
            load(getLanguageServers(), params);
            // Update the modification stamp with the current modification stamp of the Psi file
            this.modificationStamp = this.file.getModificationStamp();
        }
        return getFuture();
    }

    /**
     * Returns the language servers that support this feature for the current file.
     * This method should return a future with the filtered language servers.
     *
     * @return the language servers future.
     */
    protected abstract CompletableFuture<List<LanguageServerItem>> getLanguageServers();

    /**
     * Load the LSP data for the given language servers.
     *
     * @param languageServers     the language servers.
     * @param params              the LSP parameters expected to execute LSP requests.
     * @param cancellationSupport the cancellation support.
     * @return the LSP response results.
     */
    protected abstract CompletableFuture<Result> doLoadData(@NotNull List<LanguageServerItem> languageServers,
                                                            @NotNull Params params,
                                                            @NotNull CancellationSupport cancellationSupport);

    protected static CompletableFuture<List<LanguageServerItem>> getLanguageServers(@NotNull PsiFile file,
                                                                  @Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter,
                                                                  @Nullable Predicate<LSPClientFeatures> afterStartingServerFilter) {
        return LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file,
                        beforeStartingServerFilter,
                        afterStartingServerFilter);
    }

    protected static void updateTextDocumentUri(@NotNull TextDocumentIdentifier textDocument,
                                                @NotNull PsiFile file,
                                                @NotNull LanguageServerItem languageServer) {
        textDocument.setUri(FileUriSupport.toString(file.getVirtualFile(), languageServer.getClientFeatures()));
    }
}
