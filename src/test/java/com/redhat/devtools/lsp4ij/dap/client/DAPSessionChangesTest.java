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
package com.redhat.devtools.lsp4ij.dap.client;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The changes of a debug session run one at a time, in the order in which they come, and no thread waits for another
 * change: a change runs right away on the thread which brings it, unless another change runs, and then after it, on
 * the thread which runs that one.
 */
public class DAPSessionChangesTest {

    private static final long BOUND_SECONDS = 30;

    private final DAPSessionChanges changes = new DAPSessionChanges();
    // What the changes did, in order, and on which thread
    private final List<String> done = new CopyOnWriteArrayList<>();
    private final List<Thread> threads = new CopyOnWriteArrayList<>();

    @Test
    void aChangeRunsRightAwayOnTheThreadWhichBringsItWhenNoOtherChangeRuns() {
        changes.execute(() -> change("resumed"));
        changes.execute(() -> change("suspended"));

        assertEquals(List.of("resumed", "suspended"), done);
        assertEquals(List.of(Thread.currentThread(), Thread.currentThread()), threads);
    }

    @Test
    void changesWhichComeWhileAnotherRunsRunAfterItInOrderOnItsThreadAndTheirThreadsDoNotWait() throws Exception {
        var running = new CompletableFuture<Void>();
        var mayEnd = new CompletableFuture<Void>();
        var resuming = CompletableFuture.runAsync(() -> changes.execute(() -> {
            running.complete(null);
            await(mayEnd, "the test lets the change end");
            change("resumed");
        }));
        await(running, "the first change runs");

        // Their threads return before the change which runs ends
        changes.execute(() -> change("suspended"));
        CompletableFuture.runAsync(() -> changes.execute(() -> change("suspended again"))).get(BOUND_SECONDS, TimeUnit.SECONDS);
        assertEquals(List.of(), done, "the changes wait for the change which runs");
        mayEnd.complete(null);
        resuming.get(BOUND_SECONDS, TimeUnit.SECONDS);

        assertEquals(List.of("resumed", "suspended", "suspended again"), done);
        assertEquals(1, threads.stream().distinct().count(), "the thread of the first change runs them all: " + threads);
    }

    @Test
    void aChangeWhichARunningChangeBringsRunsAfterIt() {
        changes.execute(() -> {
            changes.execute(() -> change("suspended"));
            change("resumed");
        });

        assertEquals(List.of("resumed", "suspended"), done);
        assertSame(Thread.currentThread(), threads.get(1));
    }

    private void change(@NotNull String what) {
        done.add(what);
        threads.add(Thread.currentThread());
    }

    private static void await(@NotNull CompletableFuture<?> future, @NotNull String what) {
        try {
            future.get(BOUND_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError(what, e);
        }
    }
}
