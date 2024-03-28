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
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Base class to consume LSP requests (ex : textDocument/codeLens) from all language servers applying to a given Psi file.
 *
 * @param <Params> the LSP requests parameters (ex : CodelensParams).
 * @param <Result> the LSP response results (ex : List<CodeLensData>).
 */
public abstract class AbstractLSPFeatureSupport<Params, Result> {

    // The Psi file
    private final @NotNull PsiFile file;
    // The current modification stamp of the Psi file
    private long modificationStamp;

    // true if the future must be canceled when the Psi file is modified and false otherwise.
    private final boolean cancelWhenFileModified;

    // The current LSP requests for all language servers applying to a given Psi file
    private @Nullable CompletableFuture<Result> future;

    // The current cancellation support
    private @Nullable CancellationSupport cancellationSupport;

    public AbstractLSPFeatureSupport(@NotNull PsiFile file) {
        this(file, true);
    }

    public AbstractLSPFeatureSupport(@NotNull PsiFile file, boolean cancelWhenFileModified) {
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

    /**
     * Returns the (cached or not) LSP requests for all language servers applying to a given Psi file
     *
     * @param params the LSP parameters expected to execute LSP requests.
     * @return the (cached or not) LSP requests for all language servers applying to a given Psi file
     */
    public CompletableFuture<Result> getFeatureData(Params params) {
        if (!isValidLSPFuture()) {
            // - the LSP requests have never been executed
            // - or the LSP requests has failed
            //  - or the Psi file has been updated
            // --> consume LSP requests for all language servers applying to a given Psi file
            future = load(params);
        }
        return future;
    }

    /**
     * Returns true if the current LSP requests is valid and false otherwise.
     *
     * @return true if the current LSP requests is valid and false otherwise.
     */
    private boolean isValidLSPFuture() {
        return future != null && !future.isCompletedExceptionally() && checkFileValid();
    }

    private boolean checkFileValid() {
        return !cancelWhenFileModified || this.file.getModificationStamp() == modificationStamp;
    }

    /**
     * Cancel previous LSP requests and load the LSP requests for all language servers applying to a given Psi file by using the given cancellation support.
     *
     * @param params the LSP parameters expected to execute LSP requests.
     * @return the LSP response results.
     */
    private synchronized CompletableFuture<Result> load(Params params) {
        if (isValidLSPFuture()) {
            return future;
        }
        // Cancel previous LSP requests future
        cancel();
        // Load a new LSP requests future
        cancellationSupport = new CancellationSupport();
        CompletableFuture<Result> future = doLoad(params, cancellationSupport);
        // Update the modification stamp with the current modification stamp of the Psi file
        this.modificationStamp = this.file.getModificationStamp();
        return future;
    }

    /**
     * Load the LSP requests for all language servers applying to a given Psi file by using the given cancellation support.
     *
     * @param params              the LSP parameters expected to execute LSP requests.
     * @param cancellationSupport the cancellation support.
     * @return the LSP response results.
     */
    protected abstract CompletableFuture<Result> doLoad(Params params, CancellationSupport cancellationSupport);

    /**
     * Cancel all LSP requests.
     */
    public void cancel() {
        var future = this.future;
        if (future != null && !future.isCancelled() && !future.isDone()) {
            future.cancel(true);
        }
        this.future = null;
        // Store the CancellationSupport in a local variable to prevent from NPE (very rare case)
        CancellationSupport cancellation = cancellationSupport;
        if (cancellation != null) {
            cancellation.cancel();
        }
        cancellationSupport = null;
    }
}
