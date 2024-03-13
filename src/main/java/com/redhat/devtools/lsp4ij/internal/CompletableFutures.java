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
import com.intellij.openapi.progress.ProgressManager;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures.FutureCancelChecker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * {@link CompletableFuture} utility class.
 *
 * @author Angelo ZERR
 */
public class CompletableFutures {

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
     * @return
     */
    public static <T> @NotNull CompletableFuture<List<T>> mergeInOneFuture(@NotNull List<CompletableFuture<List<T>>> futures,
                                                                           @NotNull CancellationSupport cancellationSupport) {
        CompletableFuture<Void> allFutures = cancellationSupport
                .execute(CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])));
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
    public static boolean isDoneNormally(CompletableFuture<?> future) {
        return future != null && future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally();
    }

    /**
     * Wait for the done of the given future and stop the wait if {@link ProcessCanceledException} is thrown.
     *
     * @param future the future to wait.
     */
    public static void waitUntilDone(CompletableFuture<?> future) throws ExecutionException, ProcessCanceledException {
        while (!future.isDone()) {
            try {
                // wait for 25 ms
                future.get(25, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignore) {
            } catch (InterruptedException e) {
                Thread.interrupted();
            }
            finally {
                // check progress canceled
                ProgressManager.checkCanceled();
                // No ProcessCanceledException thrown, wait again for 25ms....
            }
        }
    }

}
