/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.client.ExecutionAttemptLimitReachedException;
import com.redhat.devtools.lsp4ij.client.IndexAwareLanguageClient;
import com.redhat.devtools.lsp4ij.client.LSPCompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * {@link PromiseToCompletableFuture} tests, through the {@link LSPCompletableFuture} which
 * {@link IndexAwareLanguageClient#runAsBackground} creates, for instance when the MicroProfile language server of
 * Quarkus Tools asks the IDE for the Java code lenses (#1203). The future runs its computation in a non-blocking read
 * action. It retries a computation which throws an {@link IndexNotReadyException} or a {@link CancellationException}
 * other than a {@link ProcessCanceledException}, and gives up with an {@link ExecutionAttemptLimitReachedException}
 * after 5 such attempts:
 * <ul>
 *     <li>a computation waits for indexing to end, instead of failing while the project indexes;</li>
 *     <li>a write action, which cancels the running read action, restarts the computation without using up one of its
 *     attempts, however many write actions there are: 0.13.0 counted each of them as an attempt;</li>
 *     <li>the failures which count still make the future give up after 5 attempts, and cancelling the future stops
 *     the retries, even a retry which starts as the future is cancelled.</li>
 * </ul>
 * The tests run outside the EDT, as the computation is requested on a background thread, so that the EDT is free for
 * the write actions and the start and end of indexing.
 *
 * @see <a href="https://github.com/redhat-developer/lsp4ij/issues/1203">#1203</a>
 */
public class PromiseToCompletableFutureTest extends BasePlatformTestCase {

    /**
     * The progress title of the computation in #1203.
     */
    private static final String TITLE = "Computing Java codelens";

    private static final String RESULT = "code lenses";

    /**
     * The attempts the future makes before it gives up.
     */
    private static final int ATTEMPTS = 5;

    /**
     * Twice the attempts, to show that the write actions don't count.
     */
    private static final int WRITE_ACTIONS = 2 * ATTEMPTS;

    /**
     * The retry delays the future waits between its 5 attempts: 100, 300, 600 and 1000 ms.
     */
    private static final Duration RETRY_DELAYS = Duration.ofMillis(100 + 300 + 600 + 1000);

    /**
     * The bound of a wait for something which takes milliseconds, or the retry delays.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /**
     * How long a test checks that the computation doesn't run: it would run within milliseconds.
     */
    private static final Duration WHILE = Duration.ofMillis(500);

    private final List<Computation> computations = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<?>> futures = new CopyOnWriteArrayList<>();
    private IndexAwareLanguageClient client;
    private @Nullable DumbModeTestUtils.EternalTaskShutdownToken indexing;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        client = new IndexAwareLanguageClient(getProject());
        Disposer.register(getTestRootDisposable(), client);
        assertFalse("the project must not index when the test starts", DumbService.isDumb(getProject()));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            futures.forEach(future -> future.cancel(true));
            computations.forEach(Computation::release);
            endIndexing();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    public void testAComputationRequestedWhileTheProjectIndexesWaitsForIndexingToEnd() {
        var computation = new Computation();
        startIndexing();
        var future = start(computation);

        assertStays("the computation ran while the project indexed", () -> computation.runs() == 0);
        endIndexing();

        assertEquals(RESULT, resultOf(future));
        assertEquals("the computation runs once, after indexing", 1, computation.runs());
    }

    public void testAComputationRunningWhenIndexingStartsWaitsForIndexingToEndWithoutUsingUpAnAttempt() {
        var computation = new Computation().hold();
        var future = start(computation);
        awaitRuns(computation, 1, future);

        // Indexing starts in a write action, which cancels the running read action
        startIndexing();

        assertStays("the computation ran again while the project indexed", () -> computation.runs() == 1);
        endIndexing();
        awaitRuns(computation, 2, future);
        // The computation can still fail 4 times: the indexing used up none of its 5 attempts
        computation.failNext(ATTEMPTS - 1, IndexNotReadyException::create).release();

        assertEquals(RESULT, resultOf(future));
        assertEquals("the start of indexing cancels the running computation", 1, computation.cancelledRuns());
        assertEquals(0, computation.runsWhileIndexing());
        assertEquals(1 + ATTEMPTS, computation.runs());
    }

    public void testWriteActionsWhichCancelTheComputationDontUseUpItsAttempts() {
        var computation = new Computation().hold();
        var future = start(computation);

        for (int run = 1; run <= WRITE_ACTIONS; run++) {
            awaitRuns(computation, run, future);
            runWriteAction();
        }
        awaitRuns(computation, WRITE_ACTIONS + 1, future);
        // The computation can still fail 4 times: the write actions used up none of its 5 attempts
        computation.failNext(ATTEMPTS - 1, IndexNotReadyException::create).release();

        assertEquals(RESULT, resultOf(future));
        assertEquals("each write action cancels the running computation", WRITE_ACTIONS, computation.cancelledRuns());
        assertEquals(WRITE_ACTIONS + ATTEMPTS, computation.runs());
    }

    public void testFiveRunsWhichFindTheIndexNotReadyMakeTheFutureGiveUp() {
        var computation = new Computation().failNext(ATTEMPTS, IndexNotReadyException::create);

        var failure = failureOf(start(computation));

        assertEquals("Execution attempt limit (5) reached to execute 'Computing Java codelens.", failure.getMessage());
        assertTrue("the future gives up with the failure of the last attempt",
                String.valueOf(failure.getResponseError().getData()).contains(IndexNotReadyException.class.getName()));
        assertEquals(ATTEMPTS, computation.runs());
        var span = computation.span();
        assertTrue("the retries wait 100, 300, 600 and 1000 ms, but the attempts took " + span.toMillis() + " ms",
                span.compareTo(RETRY_DELAYS) >= 0);
    }

    public void testFiveRunsCancelledOtherwiseThanByAProcessCanceledExceptionMakeTheFutureGiveUp() {
        var computation = new Computation().failNext(ATTEMPTS, CancellationException::new);

        var failure = failureOf(start(computation));

        assertTrue("the future gives up with the failure of the last attempt",
                String.valueOf(failure.getResponseError().getData()).contains(CancellationException.class.getName()));
        assertEquals(ATTEMPTS, computation.runs());
    }

    public void testFourRunsWhichFindTheIndexNotReadyAreRetriedUntilTheComputationSucceeds() {
        var computation = new Computation().failNext(ATTEMPTS - 1, IndexNotReadyException::create);

        assertEquals(RESULT, resultOf(start(computation)));
        assertEquals(ATTEMPTS, computation.runs());
    }

    public void testCancellingTheFutureStopsItsRetries() {
        var computation = new Computation().failNext(ATTEMPTS, IndexNotReadyException::create);
        var future = start(computation);
        // The future retries the computation 600 ms after its third failure
        await("the computation didn't fail 3 times", () -> computation.failedRuns() >= 3, future);

        future.cancel(true);
        long cancelled = System.nanoTime();

        // A retry which started before the cancellation, as when this thread is slow, may still run, but none starts
        // after it
        assertStays("the computation ran again after the future was cancelled",
                () -> computation.runsStartedAfter(cancelled) == 0, Duration.ofMillis(600 + 500));
        assertTrue(future.isCancelled());
    }

    /**
     * A cancellation of the future while a retry starts, after the retry checked that the future isn't cancelled and
     * before it records the read action it starts, used to cancel the read action which had failed, and not the new one,
     * which then ran on.
     */
    public void testCancellingTheFutureWhileARetryStartsCancelsTheRetry() {
        var computation = new Computation().failNext(1, IndexNotReadyException::create);
        computations.add(computation);
        var future = new CancelledAsItRetriesFuture(computation, getProject());
        futures.add(future);
        var first = readActionOf(future);

        await("the future didn't retry its computation", () -> readActionOf(future) != first, null);
        var retry = readActionOf(future);

        await("the read action of the retry, which started as the future was cancelled, wasn't cancelled",
                retry::isDone, null);
        assertTrue(retry.isCancelled());
        assertTrue(future.isCancelled());
    }

    public void testCancellingTheFutureCancelsItsRunningComputation() {
        var computation = new Computation().hold();
        var future = start(computation);
        awaitRuns(computation, 1, future);

        future.cancel(true);

        await("the running computation wasn't cancelled", () -> computation.cancelledRuns() == 1, null);
        assertStays("the computation ran again after the future was cancelled", () -> computation.runs() == 1);
        assertTrue(future.isCancelled());
    }

    /**
     * Starts the given computation in the {@link LSPCompletableFuture} which
     * {@link IndexAwareLanguageClient#runAsBackground} creates.
     */
    private CompletableFuture<String> start(Computation computation) {
        computations.add(computation);
        var future = new LSPCompletableFuture<>(computation, TITLE, client, null);
        futures.add(future);
        return future;
    }

    /**
     * Runs a write action, as a change to a document does: it cancels the running read action.
     */
    private static void runWriteAction() {
        WriteAction.runAndWait(() -> {
            // Nothing to change
        });
    }

    private void startIndexing() {
        indexing = DumbModeTestUtils.startEternalDumbModeTask(getProject());
    }

    private void endIndexing() {
        var token = indexing;
        indexing = null;
        if (token != null) {
            DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(getProject(), token);
        }
    }

    private static void awaitRuns(Computation computation, int runs, CompletableFuture<?> future) {
        await("the computation didn't start its run " + runs, () -> computation.runs() >= runs, future);
    }

    /**
     * Waits until the given condition holds, and fails with what didn't happen if it doesn't hold in time, or if the
     * given future, when there is one, completes before.
     */
    private static void await(String didNotHappen, BooleanSupplier condition, @Nullable CompletableFuture<?> future) {
        var deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (future != null && future.isDone()) {
                throw new AssertionError(didNotHappen + ": the future completed before", failureOrNull(future));
            }
            if (System.nanoTime() - deadline > 0) {
                fail(didNotHappen + " within " + TIMEOUT.toSeconds() + " s");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
    }

    private static void assertStays(String failure, BooleanSupplier condition) {
        assertStays(failure, condition, WHILE);
    }

    /**
     * Checks that the given condition holds for the given time, and fails as soon as it doesn't.
     */
    private static void assertStays(String failure, BooleanSupplier condition, Duration time) {
        var end = System.nanoTime() + time.toNanos();
        do {
            assertTrue(failure, condition.getAsBoolean());
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        } while (System.nanoTime() - end < 0);
        assertTrue(failure, condition.getAsBoolean());
    }

    private static <R> R resultOf(CompletableFuture<R> future) {
        try {
            return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new AssertionError("the computation failed", e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError("the computation didn't complete within " + TIMEOUT.toSeconds() + " s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the computation", e);
        }
    }

    /**
     * Returns the read action which the given future started last.
     */
    private static CancellablePromise<?> readActionOf(PromiseToCompletableFuture<?> future) {
        try {
            var field = PromiseToCompletableFuture.class.getDeclaredField("nonBlockingReadActionPromise");
            field.setAccessible(true);
            return (CancellablePromise<?>) field.get(future);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("the future has no read action field", e);
        }
    }

    private static ExecutionAttemptLimitReachedException failureOf(CompletableFuture<?> future) {
        try {
            var result = future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            throw new AssertionError("the future didn't give up, but returned " + result);
        } catch (ExecutionException e) {
            return assertInstanceOf(e.getCause(), ExecutionAttemptLimitReachedException.class);
        } catch (TimeoutException e) {
            throw new AssertionError("the future didn't give up within " + TIMEOUT.toSeconds() + " s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the future to give up", e);
        }
    }

    private static @Nullable Throwable failureOrNull(CompletableFuture<?> future) {
        return future.handle((result, failure) -> failure).getNow(null);
    }

    /**
     * A future which is cancelled as it retries its computation: after it checked that it isn't cancelled, and before
     * it starts the read action of the retry, as when another thread cancels it then. It holds the retry, so that only
     * a cancellation ends it.
     */
    private static final class CancelledAsItRetriesFuture extends PromiseToCompletableFuture<String> {

        private final Computation computation;
        private final AtomicInteger attempts = new AtomicInteger();

        CancelledAsItRetriesFuture(Computation computation, Project project) {
            super(computation, TITLE, project, null, (Object) null);
            this.computation = computation;
            init();
        }

        @Override
        protected ProgressIndicator createProgressIndicator() {
            if (attempts.incrementAndGet() == 2) {
                computation.hold();
                cancel(true);
            }
            return super.createProgressIndicator();
        }
    }

    /**
     * The computation of the tests, which records its runs. It throws an {@link IndexNotReadyException} when it runs
     * while the project indexes, as an access to an index does. While it is held, it waits, checking for cancellation
     * as a long PSI search does. Then it throws the failures it was given, one per run, before it returns
     * {@link #RESULT}.
     */
    private final class Computation implements Function<ProgressIndicator, String> {

        /**
         * The bound of a hold, in case a test fails before it releases the computation.
         */
        private static final Duration HOLD_LIMIT = Duration.ofSeconds(60);

        private final Queue<Long> starts = new ConcurrentLinkedQueue<>();
        private final AtomicInteger runsWhileIndexing = new AtomicInteger();
        private final AtomicInteger cancelledRuns = new AtomicInteger();
        private final AtomicInteger failedRuns = new AtomicInteger();
        private final Queue<Supplier<? extends RuntimeException>> failures = new ConcurrentLinkedQueue<>();
        private volatile boolean held;

        @Override
        public String apply(ProgressIndicator indicator) {
            starts.add(System.nanoTime());
            if (DumbService.isDumb(getProject())) {
                runsWhileIndexing.incrementAndGet();
                throw IndexNotReadyException.create();
            }
            waitWhileHeld();
            var failure = failures.poll();
            if (failure != null) {
                failedRuns.incrementAndGet();
                throw failure.get();
            }
            return RESULT;
        }

        private void waitWhileHeld() {
            var deadline = System.nanoTime() + HOLD_LIMIT.toNanos();
            try {
                while (held) {
                    ProgressManager.checkCanceled();
                    if (System.nanoTime() - deadline > 0) {
                        throw new IllegalStateException("the test didn't release the computation within "
                                + HOLD_LIMIT.toSeconds() + " s");
                    }
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                }
            } catch (ProcessCanceledException e) {
                cancelledRuns.incrementAndGet();
                throw e;
            }
        }

        Computation hold() {
            held = true;
            return this;
        }

        Computation release() {
            held = false;
            return this;
        }

        /**
         * Makes the next runs throw the given failure, one per run.
         */
        Computation failNext(int runs, Supplier<? extends RuntimeException> failure) {
            IntStream.range(0, runs).forEach(run -> failures.add(failure));
            return this;
        }

        int runs() {
            return starts.size();
        }

        /**
         * Returns how many runs started after the given time of {@link System#nanoTime()}.
         */
        int runsStartedAfter(long nanoTime) {
            return (int) starts.stream().filter(start -> start - nanoTime > 0).count();
        }

        int runsWhileIndexing() {
            return runsWhileIndexing.get();
        }

        int cancelledRuns() {
            return cancelledRuns.get();
        }

        int failedRuns() {
            return failedRuns.get();
        }

        /**
         * Returns the time from the start of the first run to the start of the last.
         */
        @NotNull Duration span() {
            var first = starts.stream().mapToLong(Long::longValue).min().orElseThrow();
            var last = starts.stream().mapToLong(Long::longValue).max().orElseThrow();
            return Duration.ofNanos(last - first);
        }
    }
}
