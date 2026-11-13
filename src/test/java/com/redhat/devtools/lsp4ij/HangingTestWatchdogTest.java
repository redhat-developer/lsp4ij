/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.launcher.TestIdentifier;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the watchdog ends the tests when one hangs, with the stacks of all threads, and leaves them running
 * otherwise. The JVM keeps running here: the watchdog counts a latch down instead of ending it.
 */
public class HangingTestWatchdogTest {

    private static final Duration LIMIT = Duration.ofMillis(100);
    // Several times the limit, for a watchdog which wrongly ends the tests to do so
    private static final long QUIET_MILLIS = 1000;

    private final CompletableFuture<String> report = new CompletableFuture<>();
    private final CountDownLatch ended = new CountDownLatch(1);
    private HangingTestWatchdog watchdog;

    @AfterEach
    void stopTheWatchdog() {
        if (watchdog != null) {
            watchdog.testPlanExecutionFinished(null);
        }
    }

    @Test
    void aTestWhichRunsForLongerThanTheLimitEndsTheTestsWithTheStacksOfAllThreads() throws Exception {
        watchdog = new HangingTestWatchdog(LIMIT, report::complete, ended::countDown, false);

        watchdog.executionStarted(identify("hangs", TestDescriptor.Type.TEST));

        assertTrue(ended.await(30, TimeUnit.SECONDS), "the watchdog ends the tests");
        String text = report.getNow("");
        assertTrue(text.contains("[test:hangs]"), "the report names the test: " + text);
        assertTrue(text.contains("\"" + Thread.currentThread().getName() + "\" "), "the report has every thread: " + text);
        // The bottom of this thread's stack, far below the 8 frames which ThreadInfo.toString() shows
        assertTrue(text.contains("org.junit.platform.launcher"), "the report has whole stacks: " + text);
    }

    @Test
    void theStacksSayWhichThreadHoldsTheLockThatAnotherWaitsFor() throws Exception {
        Object lock = new Object();
        var holding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = new Thread(() -> {
            synchronized (lock) {
                holding.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "holder");
        var waiter = new Thread(() -> {
            synchronized (lock) {
                // the lock is free again
            }
        }, "waiter");
        holder.start();
        try {
            assertTrue(holding.await(30, TimeUnit.SECONDS));
            waiter.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (waiter.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(Thread.State.BLOCKED, waiter.getState());

            String dump = HangingTestWatchdog.threadDump();

            assertTrue(dump.contains("\"waiter\" #" + waiter.getId() + " BLOCKED on ")
                    && dump.contains(" held by \"holder\" #" + holder.getId()), dump);
            String holderStack = dump.substring(dump.indexOf("\"holder\" #" + holder.getId()));
            assertTrue(holderStack.substring(0, holderStack.indexOf("\n\n")).contains("\t- locked java.lang.Object@"), dump);
        } finally {
            release.countDown();
            holder.join(30_000);
            waiter.join(30_000);
        }
    }

    @Test
    void aTestWhichEndsInTimeLeavesTheTestsRunning() throws Exception {
        watchdog = new HangingTestWatchdog(LIMIT, report::complete, ended::countDown, false);
        var test = identify("passes", TestDescriptor.Type.TEST);

        watchdog.executionStarted(test);
        watchdog.executionFinished(test, TestExecutionResult.successful());

        assertFalse(ended.await(QUIET_MILLIS, TimeUnit.MILLISECONDS));
    }

    @Test
    void aClassOfTestsIsNotWatchedSinceItRunsAsLongAsAllItsTests() throws Exception {
        watchdog = new HangingTestWatchdog(LIMIT, report::complete, ended::countDown, false);

        watchdog.executionStarted(identify("SomeTest", TestDescriptor.Type.CONTAINER));

        assertFalse(ended.await(QUIET_MILLIS, TimeUnit.MILLISECONDS));
    }

    @Test
    void noTestIsWatchedWhileADebuggerIsAttached() throws Exception {
        watchdog = new HangingTestWatchdog(LIMIT, report::complete, ended::countDown, true);

        watchdog.executionStarted(identify("stopsAtABreakpoint", TestDescriptor.Type.TEST));

        assertFalse(ended.await(QUIET_MILLIS, TimeUnit.MILLISECONDS));
    }

    private static @NotNull TestIdentifier identify(@NotNull String name, @NotNull TestDescriptor.Type type) {
        var id = UniqueId.forEngine("watchdog").append(type == TestDescriptor.Type.TEST ? "test" : "class", name);
        return TestIdentifier.from(new AbstractTestDescriptor(id, name) {
            @Override
            public Type getType() {
                return type;
            }
        });
    }
}
