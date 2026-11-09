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
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.psi.PsiFile;
import com.intellij.util.ExceptionUtil;
import com.redhat.devtools.lsp4ij.server.LanguageServerException;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures.FutureCancelChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * {@link CompletableFuture} utility class.
 *
 * @author Angelo ZERR
 */
public class CompletableFutures {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompletableFutures.class);

    private CompletableFutures() {

    }

    /**
     * It's a copy of
     * {@link org.eclipse.lsp4j.jsonrpc.CompletableFutures#computeAsync} that
     * accepts a function that returns a CompletableFuture.
     *
     * @param <R>  the return type of the asynchronous computation
     * @param code the code to run asynchronously
     * @return a future that sends the correct $/cancelRequest notification when
     * canceled
     * @see CompletableFutures#computeAsyncCompose(Function)
     */
    public static <R> CompletableFuture<R> computeAsyncCompose(
            Function<CancelChecker, CompletableFuture<R>> code) {
        CompletableFuture<CancelChecker> start = new CompletableFuture<>();
        CompletableFuture<R> result = start.thenComposeAsync(code);
        start.complete(new FutureCancelChecker(result));
        return result;
    }

    /**
     * Merge the given futures List<CompletableFuture<List<T>>> in one future CompletableFuture<List<T>.
     *
     * @param futures             the list of futures which return a List<T>.
     * @param cancellationSupport the cancellation support.
     * @param <T>                 the merged futures.
     * @return the future.
     */
    public static <T> @NotNull CompletableFuture<List<T>> mergeInOneFuture(@NotNull List<CompletableFuture<List<T>>> futures,
                                                                           @NotNull CancellationSupport cancellationSupport) {
        CompletableFuture<Void> allFutures = cancellationSupport
                .execute(CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])));
        return allFutures.thenApply(Void -> {
            List<T> mergedDataList = new ArrayList<>(futures.size());
            for (CompletableFuture<List<T>> dataListFuture : futures) {
                var data = dataListFuture.join();
                if (data != null) {
                    mergedDataList.addAll(data);
                }
            }
            return mergedDataList;
        });
    }

    /**
     * Returns true if the given {@link CompletableFuture} is done normally and false otherwise.
     *
     * @param future the completable future.
     * @return true if the given {@link CompletableFuture} is done normally and false otherwise.
     */
    public static boolean isDoneNormally(@Nullable CompletableFuture<?> future) {
        return future != null && future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally();
    }

    /**
     * Wait in Task (which is cancellable) for the done of the given future and stop the wait if {@link ProcessCanceledException} is thrown.
     *
     * @param future the future to wait.
     * @param title  the task title.
     * @param file   the Psi file.
     */
    public static void waitUntilDoneAsync(@Nullable CompletableFuture<?> future,
                                          @NotNull String title,
                                          @NotNull PsiFile file) {

        if (future == null) {
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(file.getProject(), title, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    ProgressIndicatorUtils.awaitWithCheckCanceled(future);
                } catch (
                        ProcessCanceledException e) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
                    CancellationSupport.cancel(future);
                    throw e;
                } catch (CancellationException e) {
                    CancellationSupport.cancel(future);
                } catch (Exception e) {
                    // Errors are handled by the caller's future.handle((response, error) -> ...)
                }
            }
        });
    }

    /**
     * Wait for the done of the given future and stop the wait if {@link ProcessCanceledException} is thrown.
     * <p>
     * A failure of the future is not thrown: the caller checks {@link #isDoneNormally(CompletableFuture)}
     * and skips a failed future.
     *
     * @param future the future to wait.
     * @throws CancellationException if the future is cancelled, or the progress is canceled
     *                               ({@link ProcessCanceledException}).
     */
    public static void awaitWithCheckCanceled(@Nullable Future<?> future) {
        if (future == null) {
            return;
        }
        try {
            ProgressIndicatorUtils.awaitWithCheckCanceled(future);
        } catch (ProcessCanceledException e) {
            CancellationException cancellation = cancellationOf(future, e);
            if (cancellation == null) {
                throw e;
            }
            // A progress cancelled too still throws its own
            ProgressManager.checkCanceled();
            throw cancellation;
        } catch (CancellationException e) {
            // Since 2024.2 ProcessCanceledException extends CancellationException
            throw e;
        } catch (RuntimeException e) {
            // ProgressIndicatorUtils rethrows the failure of the future, an ExecutionException, in a RuntimeException
            if (!(e.getCause() instanceof ExecutionException failure)) {
                throw e;
            }
            logFailure(failure);
        }
    }

    /**
     * Returns the cancellation of the given future when the given ProcessCanceledException, thrown by a wait for it,
     * reports it, and null when it reports the cancellation of the operation, such as a cancelled progress.
     * <p>
     * Since Java 25, the get() of a cancelled future throws a CancellationException caused by the future's, which the
     * platform reports as a ProcessCanceledException caused by that one. A cancelled progress is reported without it,
     * even when the future is cancelled too, and even when the progress's own checks don't throw.
     *
     * @param future the awaited future.
     * @param e      the ProcessCanceledException thrown by the wait.
     * @return the future's cancellation, or null.
     */
    public static @Nullable CancellationException cancellationOf(@NotNull Future<?> future,
                                                                 @NotNull ProcessCanceledException e) {
        return future.isCancelled()
                && e.getCause() instanceof CancellationException cancellation
                && !(cancellation instanceof ProcessCanceledException)
                ? cancellation
                : null;
    }

    /**
     * Logs the failure of an awaited future, which the wait doesn't throw.
     * <p>
     * A language server which cannot start, or which stopped, has already been reported when it failed, and an LSP
     * error response is reported by {@link CancellationSupport}, which completes the request with null. Any other
     * failure is unexpected.
     *
     * @param failure the failure of the future.
     */
    static void logFailure(@NotNull ExecutionException failure) {
        if (ExceptionUtil.causedBy(failure, LanguageServerException.class)) {
            LOGGER.debug("Skipped a future whose language server is not running", failure);
        } else {
            LOGGER.warn("Skipped a failed future", failure);
        }
    }

    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
        var allOff = CompletableFuture.allOf(cfs);
        CancellationSupport.forwardCancellation(allOff, cfs);
        return allOff;
    }

    /**
     * Returns an exception handler that silently ignores all exceptions.
     * Useful for operations where errors (including cancellations) can be safely ignored,
     * such as real-time color picker updates where rapid changes may cancel pending requests.
     *
     * @param <T> the type of the CompletableFuture result
     * @return a function that returns null for any exception
     */
    public static <T> Function<Throwable, T> ignoreAllExceptions() {
        return ex -> null;
    }
}