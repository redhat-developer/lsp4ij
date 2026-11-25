/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features;

import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FeatureCacheConcurrencyTest extends LightPlatformTestCase {
    // Bounds each wait of the test
    private static final long BOUND_SECONDS = 30;
    // Bounds the wait of the validation for its release: longer than the waits of the test before it releases it
    private static final long VALIDATION_BOUND_SECONDS = 4 * BOUND_SECONDS;
    // How long the test waits for the cancellation to end before it looks again whether it is blocked
    private static final long POLL_MILLIS = 10;

    public void testValidFutureReadCannotSwitchOwnersDuringCancellation() throws Exception {
        var support = new PausedSupport();
        var original = support.getFeatureData("first");
        // The threads which the test started: the cleanup releases the validation, also for a reader which starts
        // late, and waits for them, also when the test fails
        List<Thread> started = new ArrayList<>();
        try (AutoCloseable cleanup = () -> releaseAndJoin(support, started)) {
            var read = new CompletableFuture<CompletableFuture<String>>();
            started.add(start("feature-cache-reader", read, support::getValidLSPFuture));
            assertTrue("the reader must validate the cached future", support.validating.await(BOUND_SECONDS, TimeUnit.SECONDS));
            var cancelled = new CompletableFuture<Void>();
            var canceller = start("feature-cache-canceller", cancelled, () -> {
                support.cancel();
                return null;
            });
            started.add(canceller);
            // Wait for cancellation to reach the occupied monitor (or, before the fix,
            // finish while the reader is validating). No duration-based scheduling guess.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUND_SECONDS);
            while (canceller.getState() != Thread.State.BLOCKED && !cancelled.isDone()) {
                assertTrue("the cancellation must wait for the reader, or end", System.nanoTime() < deadline);
                try {
                    cancelled.get(POLL_MILLIS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException stillRunning) {
                    // It neither ended nor blocked yet: look again
                }
            }
            support.continueValidation.countDown();
            assertSame(original, read.get(BOUND_SECONDS, TimeUnit.SECONDS));
            cancelled.get(BOUND_SECONDS, TimeUnit.SECONDS);
            assertNull(support.getFuture());
        }
    }

    /**
     * Starts a thread with the given name which completes the given future with the result of the given task, or with
     * its failure.
     */
    private static <T> Thread start(String name, CompletableFuture<T> result, Callable<T> task) {
        var thread = new Thread(() -> {
            try {
                result.complete(task.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, name);
        thread.start();
        return thread;
    }

    /**
     * Releases the validation of the given support, also for a reader which validates only now, and waits within one
     * bound until the given threads ended; interrupts and reports those which didn't.
     */
    private static void releaseAndJoin(PausedSupport support, List<Thread> threads) throws InterruptedException {
        support.continueValidation.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUND_SECONDS);
        for (Thread thread : threads) {
            thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
        }
        List<Thread> running = threads.stream().filter(Thread::isAlive).toList();
        if (!running.isEmpty()) {
            running.forEach(Thread::interrupt);
            throw new AssertionError("The threads of the test must end once the validation is released: "
                    + running.stream().map(Thread::getName).toList());
        }
    }

    private static class PausedSupport extends AbstractLSPFeatureSupport<String, String> {
        private final CountDownLatch validating = new CountDownLatch(1);
        private final CountDownLatch continueValidation = new CountDownLatch(1);

        @Override protected boolean checkValid() {
            validating.countDown();
            try {
                if (!continueValidation.await(VALIDATION_BOUND_SECONDS, TimeUnit.SECONDS)) throw new AssertionError("Validation was not released");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return true;
        }

        @Override protected CompletableFuture<String> doLoad(String params, CancellationSupport cancellationSupport) {
            return CompletableFuture.completedFuture(params);
        }
    }
}
