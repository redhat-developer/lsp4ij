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

    /**
     * Cancel previous LSP requests and load the LSP requests for all language servers applying to a given Psi file by using the given cancellation support.
     *
     * @param params the LSP parameters expected to execute LSP requests.
     * @return the LSP response results.
     */
    protected synchronized CompletableFuture<Result> load(Params params) {
        CompletableFuture<Result> future = super.load(params);
        // Update the modification stamp with the current modification stamp of the Psi file
        this.modificationStamp = this.file.getModificationStamp();
        return future;
    }

    protected static CompletableFuture<List<LanguageServerItem>> getLanguageServers(@NotNull PsiFile file,
                                                                  @Nullable Predicate<LSPClientFeatures> beforeStartingServerFilter,
                                                                  @Nullable Predicate<LSPClientFeatures> afterStartingServerFilter) {
        return LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file.getVirtualFile(),
                        beforeStartingServerFilter,
                        afterStartingServerFilter);
    }

    protected static void updateTextDocumentUri(@NotNull TextDocumentIdentifier textDocument,
                                                @NotNull PsiFile file,
                                                @NotNull LanguageServerItem languageServer) {
        textDocument.setUri(FileUriSupport.getFileUri(file.getVirtualFile(), languageServer.getClientFeatures()).toASCIIString());
    }
}
