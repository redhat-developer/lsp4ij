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

import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Base class to consume LSP requests:
 *
 * <ul>
 *     <li>textDocument/* : (ex : textDocument/codeLens) from all language servers applying to a given Psi file.</li>
 *     <li>workspace/*: (ex : workspace/symbol) from all language servers applying to a given project.</li>
 * </ul>
 *
 * @param <Params> the LSP requests parameters (ex : CodelensParams).
 * @param <Result> the LSP response results (ex : List<CodeLensData>).
 */
public abstract class AbstractLSPFeatureSupport<Params, Result> {

    // The current LSP requests for all language servers applying to a given Psi file, project
    private @Nullable CompletableFuture<Result> future;

    // The current cancellation support
    private @Nullable CancellationSupport cancellationSupport;

    /**
     * Returns the (cached or not) LSP requests for all language servers applying to a given Psi file or project.
     *
     * @param params the LSP parameters expected to execute LSP requests.
     * @return the (cached or not) LSP requests for all language servers applying to a given Psi file or project and null otherwise
     * (rare case when the future is loaded and cancel is occurred in the same time which set the future to null).
     */
    @Nullable
    public CompletableFuture<Result> getFeatureData(Params params) {
        if (!isValidLSPFuture()) {
            // - the LSP requests have never been executed
            // - or the LSP requests has failed
            //  - or the Psi file has been updated
            // --> consume LSP requests for all language servers applying to a given Psi file, or project.
            future = load(params);
        }
        return future;
    }

    /**
     * Returns true if the current LSP requests is valid and false otherwise.
     *
     * @return true if the current LSP requests is valid and false otherwise.
     */
    protected boolean isValidLSPFuture() {
        return future != null && !future.isCompletedExceptionally() && checkValid();
    }

    protected abstract boolean checkValid();

    /**
     * Returns the current valid LSP request and null otherwise.
     *
     * @return the current valid LSP request and null otherwise.
     */
    public @Nullable CompletableFuture<Result> getValidLSPFuture() {
        return isValidLSPFuture() ? future : null;
    }

    /**
     * Cancel previous LSP requests and load the LSP requests for all language servers applying to a given Psi file or project by using the given cancellation support.
     *
     * @param params the LSP parameters expected to execute LSP requests.
     * @return the LSP response results.
     */
    protected synchronized CompletableFuture<Result> load(Params params) {
        if (isValidLSPFuture()) {
            return future;
        }
        // Cancel previous LSP requests future
        cancel();
        // Load a new LSP requests future
        cancellationSupport = new CancellationSupport();
        return doLoad(params, cancellationSupport);
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
        // Store the CancellationSupport in a local variable to prevent from NPE (very rare case)
        CancellationSupport cancellation = cancellationSupport;
        var future = this.future;
        if (future != null && !future.isCancelled() && !future.isDone()) {
            future.cancel(true);
        }
        this.future = null;
        if (cancellation != null) {
            cancellation.cancel();
        }
    }
}
