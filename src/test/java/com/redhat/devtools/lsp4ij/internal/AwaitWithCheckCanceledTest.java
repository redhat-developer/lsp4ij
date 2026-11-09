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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
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

    /**
     * Since Java 25, the get() of a cancelled future throws a CancellationException caused by the future's, which the
     * platform reports as a ProcessCanceledException: the wait still throws the future's cancellation.
     */
    public void testACancelledFutureThrowsItsCancellationAsJava25ReportsIt() {
        var cancellation = new CancellationException("the future's cancellation");
        var future = cancelledAsOnJava25(cancellation);

        var thrown = thrownBy(() -> CompletableFutures.awaitWithCheckCanceled(future));

        assertSame(cancellation, thrown);
    }

    /**
     * A progress that is cancelled too still throws its own cancellation, which callers tell from the future's.
     */
    public void testACanceledProgressThrowsAProcessCanceledExceptionWhenTheFutureIsCancelledToo() {
        var indicator = new EmptyProgressIndicator();
        var future = cancelledAsOnJava25();

        var thrown = thrownBy(() -> ProgressManager.getInstance().executeProcessUnderProgress(
                () -> {
                    indicator.cancel();
                    CompletableFutures.awaitWithCheckCanceled(future);
                },
                indicator));

        assertInstanceOf(thrown, ProcessCanceledException.class);
    }

    /**
     * The wait checks a progress whose own checks don't throw, as one that cannot be cancelled, and throws a
     * ProcessCanceledException when it is cancelled: the wait throws it, although the future is cancelled too.
     */
    public void testACanceledProgressWhoseChecksDontThrowThrowsAProcessCanceledExceptionWhenTheFutureIsCancelledToo() {
        var indicator = progressWhoseChecksDontThrow();
        var future = cancelledWhileItIsAwaited();

        var thrown = thrownBy(() -> ProgressManager.getInstance().executeProcessUnderProgress(
                () -> {
                    indicator.cancel();
                    CompletableFutures.awaitWithCheckCanceled(future);
                },
                indicator));

        assertInstanceOf(thrown, ProcessCanceledException.class);
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

    /**
     * Returns a cancelled future whose get() throws a CancellationException caused by the future's, as the get() of a
     * cancelled CompletableFuture does since Java 25, so that a test takes that path on any Java.
     */
    public static <T> CompletableFuture<T> cancelledAsOnJava25() {
        return cancelledAsOnJava25(new CancellationException());
    }

    private static <T> CompletableFuture<T> cancelledAsOnJava25(CancellationException cancellation) {
        var future = new CompletableFuture<T>() {
            @Override
            public T get(long timeout, TimeUnit unit) {
                throw (CancellationException) new CancellationException().initCause(cancellation);
            }
        };
        future.cancel(true);
        return future;
    }

    /**
     * Returns a cancelled future which isn't done yet when its wait checks the progress, as when it is cancelled while
     * it is awaited.
     */
    public static <T> CompletableFuture<T> cancelledWhileItIsAwaited() {
        var future = new CompletableFuture<T>() {
            @Override
            public boolean isDone() {
                return false;
            }
        };
        future.cancel(true);
        return future;
    }

    /**
     * Returns a progress whose checkCanceled() doesn't throw once it is cancelled, as for a progress that cannot be
     * cancelled, which ProgressManager.checkCanceled() doesn't report either.
     */
    public static ProgressIndicator progressWhoseChecksDontThrow() {
        return new ProgressIndicatorBase() {
            @Override
            protected boolean isCancelable() {
                return false;
            }
        };
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
