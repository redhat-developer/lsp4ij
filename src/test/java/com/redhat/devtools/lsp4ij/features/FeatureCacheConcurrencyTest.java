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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FeatureCacheConcurrencyTest extends LightPlatformTestCase {
    // Bounds each wait of the test
    private static final long BOUND_SECONDS = 30;
    // Bounds the wait of the validation for its release: longer than the waits of the test before it releases it
    private static final long VALIDATION_BOUND_SECONDS = 4 * BOUND_SECONDS;

    public void testValidFutureReadCannotSwitchOwnersDuringCancellation() throws Exception {
        var support = new PausedSupport();
        var original = support.getFeatureData("first");
        var read = CompletableFuture.supplyAsync(support::getValidLSPFuture);
        assertTrue(support.validating.await(BOUND_SECONDS, TimeUnit.SECONDS));
        var cancelled = new CompletableFuture<Void>();
        var canceller = new Thread(() -> {
            try {
                support.cancel();
                cancelled.complete(null);
            } catch (Throwable failure) {
                cancelled.completeExceptionally(failure);
            }
        }, "feature-cache-canceller");
        canceller.start();
        try {
            // Wait for cancellation to reach the occupied monitor (or, before the fix,
            // finish while the reader is validating). No duration-based scheduling guess.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUND_SECONDS);
            while (canceller.getState() != Thread.State.BLOCKED && !cancelled.isDone()
                    && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertTrue(canceller.getState() == Thread.State.BLOCKED || cancelled.isDone());
            support.continueValidation.countDown();
            assertSame(original, read.get(BOUND_SECONDS, TimeUnit.SECONDS));
            cancelled.get(BOUND_SECONDS, TimeUnit.SECONDS);
            assertNull(support.getFuture());
        } finally {
            support.continueValidation.countDown();
            canceller.join(TimeUnit.SECONDS.toMillis(BOUND_SECONDS));
            if (canceller.isAlive()) canceller.interrupt();
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
