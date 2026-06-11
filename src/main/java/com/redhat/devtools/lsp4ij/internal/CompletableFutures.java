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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
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
    public static boolean isDoneNormally(@Nullable CompletableFuture<?> future) {
        return future != null && future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally();
    }

    public static void waitUntilDone(@Nullable CompletableFuture<?> future) throws ExecutionException, ProcessCanceledException {
        waitUntilDone(future, null);
    }

    /**
     * Wait for the done of the given future and stop the wait if {@link ProcessCanceledException} is thrown.
     *
     * @param future the future to wait.
     * @param file   the Psi file.
     */
    public static void waitUntilDone(@Nullable CompletableFuture<?> future,
                                     @Nullable PsiFile file) throws ExecutionException, ProcessCanceledException {
        try {
            waitUntilDone(future, file, null);
        } catch (TimeoutException e) {
            // Should never occur since timeout is null
            throw new RuntimeException(e);
        }
    }

    /**
     * Wait for the done of the given future and stop the wait if {@link ProcessCanceledException} is thrown.
     *
     * @param future  the future to wait.
     * @param file    the Psi file.
     * @param timeout wait for the given timeout and null otherwise.
     */
    public static void waitUntilDone(@Nullable CompletableFuture<?> future,
                                     @Nullable PsiFile file,
                                     @Nullable Integer timeout) throws ExecutionException, ProcessCanceledException, TimeoutException {
        if (future == null) {
            return;
        }

        // CRITICAL: Detect dangerous contexts and compute appropriate timeout
        // Note: We only detect context ONCE at the start - the context could change during the wait,
        // but we use the initial context to determine the max safe timeout
        Integer effectiveTimeout = timeout;
        String initialContext = null;
        boolean isSafetyTimeout = false;  // true if we forced the timeout for safety (vs explicit from caller)

        if (effectiveTimeout == null) {
            final boolean isOnEDT = ApplicationManager.getApplication().isDispatchThread();
            final boolean isInReadAction = ApplicationManager.getApplication().isReadAccessAllowed();
            final boolean isInWriteAction = ApplicationManager.getApplication().isWriteAccessAllowed();

            // Force timeout in dangerous contexts to prevent freezes/deadlocks
            if (isInWriteAction) {
                // WriteAction: VERY dangerous - future likely needs ReadAction → guaranteed deadlock
                effectiveTimeout = 500;  // 500ms max
                initialContext = "WriteAction";
                isSafetyTimeout = true;
            } else if (isInReadAction) {
                // ReadAction: dangerous - future might need WriteAction → potential deadlock
                effectiveTimeout = 2000;  // 2s max
                initialContext = "ReadAction";
                isSafetyTimeout = true;
            } else if (isOnEDT) {
                // EDT: dangerous - blocking freezes UI
                effectiveTimeout = 1000;  // 1s max to avoid visible UI freeze
                initialContext = "EDT";
                isSafetyTimeout = true;
            }
            // else: background thread without locks - can wait indefinitely
        }

        long start = System.currentTimeMillis();
        // Check file modification stamp at each iteration (only if in ReadAction)
        Long initialModificationStamp = null;
        if (file != null && ApplicationManager.getApplication().isReadAccessAllowed()) {
            initialModificationStamp = file.getFileDocument().getModificationStamp();
        }

        while (!future.isDone()) {
            try {
                // check progress canceled
                ProgressManager.checkCanceled();

                // Check psi file modification (only if in ReadAction)
                // Re-check context at each iteration as it may have changed
                if (file != null && initialModificationStamp != null && ApplicationManager.getApplication().isReadAccessAllowed()) {
                    if (!initialModificationStamp.equals(file.getFileDocument().getModificationStamp())) {
                        throw new PsiFileChangedException();
                    }
                }

                // wait for 25 ms
                future.get(25, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeoutEx) {
                long time = System.currentTimeMillis() - start;
                if (effectiveTimeout != null && time > effectiveTimeout) {
                    if (isSafetyTimeout) {
                        // Safety timeout triggered - log warning with clear explanation of the root cause
                        // Detect current context for accurate logging (may have changed since start)
                        String currentContext = ApplicationManager.getApplication().isWriteAccessAllowed() ? "WriteAction" :
                                ApplicationManager.getApplication().isReadAccessAllowed() ? "ReadAction" :
                                ApplicationManager.getApplication().isDispatchThread() ? "EDT" : "background";

                        String contextInfo = initialContext.equals(currentContext) ? initialContext : initialContext + " → " + currentContext;

                        // Explain WHY the timeout was forced based on the context
                        String reason = switch (initialContext) {
                            case "WriteAction" ->
                                    "WriteAction + LSP request = guaranteed deadlock (LSP needs ReadAction)";
                            case "ReadAction" ->
                                    "ReadAction + blocking wait = potential deadlock (LSP might need WriteAction)";
                            case "EDT" -> "EDT + blocking wait = UI freeze";
                            default -> "dangerous context detected";
                        };

                        LOGGER.warn("waitUntilDone() TIMEOUT after {}ms in {}. Cause: {}. " +
                                        "LSP feature will return no data this time. " +
                                        "File: {}",
                                time, contextInfo, reason, file != null ? file.getName() : "null", new Throwable());

                        // Throw SafetyTimeoutException - caller can catch and handle gracefully with async fallback
                        throw new SafetyTimeoutException("Safety timeout after " + time + "ms in " + initialContext);
                    } else {
                        // Timeout was explicitly provided by caller - respect it by throwing TimeoutException
                        throw timeoutEx;
                    }
                }
                if (file != null && !future.isDone() && System.currentTimeMillis() - start > 5000 &&
                        (ProjectIndexingManager.isIndexingAll() || ApplicationManager.getApplication().isDispatchThread())) {
                    // When some projects are being indexed,
                    // the language server startup can take a long time
                    // and the LSP feature (ex: codeLens)
                    // waits for the language server startup.
                    // This wait can block IJ, here we stop the wait (and we could lose some LSP feature)
                    throw new CancellationException("Some projects are indexing");
                }
                // Ignore timeout
            } catch (ExecutionException | CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ProcessCanceledException pce) {
                    throw pce;
                }
                if (cause instanceof LanguageServerException) {
                    // Server cannot be started, throws a ProcessCanceledException to ignore the error.
                    throw new ProcessCanceledException(cause);
                }
                if (cause instanceof CancellationException ce) {
                    throw ce;
                }
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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
                    waitUntilDone(future, file);
                } catch (
                        ProcessCanceledException e) {//Since 2024.2 ProcessCanceledException extends CancellationException so we can't use multicatch to keep backward compatibility
                    // Case when user click on cancel of progress Task.
                    CancellationSupport.cancel(future);
                    throw e;
                } catch (CancellationException e) {
                    CancellationSupport.cancel(future);
                } catch (Exception e) {
                    // Do nothing, error should be handled by the block code
                    // which consumes the future:
                    // future.handler((response, error) -> ....
                }
            }
        });
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