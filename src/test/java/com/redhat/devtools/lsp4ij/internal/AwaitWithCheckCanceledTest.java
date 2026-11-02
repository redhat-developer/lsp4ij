/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;

/**
 * A wait for a future which fails returns, and the caller skips the future, which is not done normally. The wait
 * doesn't throw the failure, which IntelliJ would report as an IDE error. A cancellation is thrown as before.
 */
public class AwaitWithCheckCanceledTest extends BasePlatformTestCase {

    public void testAFutureWhoseServerCannotStartIsNotThrown() {
        var future = CompletableFuture.failedFuture(new CannotStartProcessException("Cannot run program \"missing\""));

        CompletableFutures.awaitWithCheckCanceled(future);

        assertFalse(isDoneNormally(future));
    }

    public void testAFutureWhichFailsWhileItIsAwaitedIsNotThrown() {
        var future = new CompletableFuture<>();
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
                () -> future.completeExceptionally(new IllegalStateException("The request failed")),
                50,
                TimeUnit.MILLISECONDS);

        CompletableFutures.awaitWithCheckCanceled(future);

        assertFalse(isDoneNormally(future));
    }

    public void testACancelledFutureThrowsItsCancellation() {
        var future = new CompletableFuture<>();
        future.cancel(true);

        var thrown = thrownBy(() -> CompletableFutures.awaitWithCheckCanceled(future));

        assertEquals(CancellationException.class, thrown.getClass());
    }

    public void testAFutureCancelledInAPreviousStageThrowsAProcessCanceledException() {
        var previous = new CompletableFuture<>();
        var future = previous.thenApply(result -> result);
        previous.cancel(true);

        var thrown = thrownBy(() -> CompletableFutures.awaitWithCheckCanceled(future));

        assertInstanceOf(thrown, ProcessCanceledException.class);
    }

    public void testACanceledProgressThrowsAProcessCanceledException() {
        var indicator = new EmptyProgressIndicator();
        var future = new CompletableFuture<>();

        var thrown = thrownBy(() -> ProgressManager.getInstance().executeProcessUnderProgress(
                () -> {
                    indicator.cancel();
                    CompletableFutures.awaitWithCheckCanceled(future);
                },
                indicator));

        assertInstanceOf(thrown, ProcessCanceledException.class);
        assertFalse(future.isDone());
    }

    public void testCancellationSupportReturnsNoResultForAFailedFuture() {
        var future = CompletableFuture.failedFuture(new CannotStartProcessException("Cannot run program \"missing\""));

        assertNull(CancellationSupport.awaitWithCheckCanceled(future));
        assertEquals("result", CancellationSupport.awaitWithCheckCanceled(CompletableFuture.completedFuture("result")));
    }

    public void testCancellationSupportThrowsAProcessCanceledExceptionForACancelledFuture() {
        var future = new CompletableFuture<>();
        future.cancel(true);

        var thrown = thrownBy(() -> CancellationSupport.awaitWithCheckCanceled(future));

        assertInstanceOf(thrown, ProcessCanceledException.class);
    }

    private static Throwable thrownBy(ThrowableRunnable<?> wait) {
        try {
            wait.run();
        } catch (Throwable e) {
            return e;
        }
        throw new AssertionError("The wait returned");
    }
}
