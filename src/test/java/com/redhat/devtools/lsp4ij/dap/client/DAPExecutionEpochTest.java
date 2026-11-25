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

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request which names references of an execution epoch, such as Set Value's, is sent only while the epoch is the
 * current one, and before a request which resumes the program, or not at all.
 */
public class DAPExecutionEpochTest {

    private static final long BOUND_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private final DAPExecutionEpoch epoch = new DAPExecutionEpoch();
    // What was written to the debug adapter, in order
    private final List<String> written = new CopyOnWriteArrayList<>();

    @Test
    void aRequestOfTheCurrentEpochIsSent() {
        int current = epoch.end();

        assertNotNull(epoch.send(current, this::setVariable));
        assertEquals(List.of("setVariable"), written);
    }

    @Test
    void aRequestOfAnEpochWhichAStopOrAContinuedEventEndedIsNotSent() {
        int stop = epoch.end();
        epoch.end();

        assertNull(epoch.send(stop, this::setVariable));
        assertEquals(List.of(), written);
    }

    @Test
    void aRequestOfAnEpochWhichAResumeEndedIsNotSent() {
        int stop = epoch.end();
        assertEquals(stop, epoch.endBeforeResuming());

        assertNull(epoch.send(stop, this::setVariable));
        assertEquals(List.of(), written);
    }

    @Test
    void anEpochIsCurrentAgainWhenTheDebugAdapterRefusesToResume() {
        int stop = epoch.end();
        int ended = epoch.endBeforeResuming();

        epoch.resumeRefused(ended);
        assertNotNull(epoch.send(stop, this::setVariable));
    }

    @Test
    void anEpochIsNotCurrentAgainWhenTheProgramStoppedSince() {
        int stop = epoch.end();
        int ended = epoch.endBeforeResuming();
        epoch.end();

        epoch.resumeRefused(ended);
        assertNull(epoch.send(stop, this::setVariable));
    }

    @Test
    void anEpochIsCurrentAgainOnceTheDebugAdapterRefusedEachRequestWhichResumedFromIt() {
        int stop = epoch.end();
        // Reverse Continue twice, before the debug adapter answers
        int first = epoch.endBeforeResuming();
        int second = epoch.endBeforeResuming();

        // The debug adapter answers in order
        epoch.resumeRefused(first);
        assertNull(epoch.send(stop, this::setVariable), "the second request may still resume the program");
        epoch.resumeRefused(second);
        assertNotNull(epoch.send(stop, this::setVariable));
        assertEquals(stop, epoch.get());
    }

    @Test
    void anEpochIsCurrentAgainOnceTheDebugAdapterRefusedEachRequestInTheOtherOrder() {
        int stop = epoch.end();
        int first = epoch.endBeforeResuming();
        int second = epoch.endBeforeResuming();

        epoch.resumeRefused(second);
        assertNull(epoch.send(stop, this::setVariable), "the first request may still resume the program");
        epoch.resumeRefused(first);
        assertNotNull(epoch.send(stop, this::setVariable));
        assertEquals(stop, epoch.get());
    }

    @Test
    void anEpochStaysEndedWhenTheDebugAdapterAcceptedTheFirstRequestWhichResumedFromIt() {
        int stop = epoch.end();
        epoch.endBeforeResuming();
        int second = epoch.endBeforeResuming();

        // The debug adapter accepts the first request, which runs the program, and refuses the second one
        epoch.resumeRefused(second);
        assertNull(epoch.send(stop, this::setVariable));
    }

    @Test
    void anEpochStaysEndedWhenTheDebugAdapterAcceptedTheSecondRequestWhichResumedFromIt() {
        int stop = epoch.end();
        int first = epoch.endBeforeResuming();
        epoch.endBeforeResuming();

        // The debug adapter refuses the first request, and accepts the second one, which runs the program
        epoch.resumeRefused(first);
        assertNull(epoch.send(stop, this::setVariable));
    }

    @Test
    void aRefusalOfARequestOfAnEarlierStopMakesNoEpochCurrentAgain() {
        epoch.end();
        int beforeTheStop = epoch.endBeforeResuming();
        int stop = epoch.end();
        int afterTheStop = epoch.endBeforeResuming();

        epoch.resumeRefused(afterTheStop);
        epoch.resumeRefused(beforeTheStop);
        assertEquals(stop, epoch.get(), "the epoch of the last stop is the current one again, and no earlier one");
        assertNotNull(epoch.send(stop, this::setVariable));
    }

    @Test
    void aStopOrAContinuedEventSinceARequestWhichResumedTheProgramIsKnown() {
        epoch.end();
        int ended = epoch.endBeforeResuming();
        assertFalse(epoch.stoppedOrContinuedSince(ended));

        epoch.end();
        assertTrue(epoch.stoppedOrContinuedSince(ended), "a stop or a 'continued' event since the request");
        assertFalse(epoch.stoppedOrContinuedSince(epoch.endBeforeResuming()));
    }

    @Test
    void aResumeWaitsUntilTheRequestOfTheEpochIsWritten() throws Exception {
        int stop = epoch.end();
        var writing = new CountDownLatch(1);
        var mayWrite = new CountDownLatch(1);
        // Set Value checks the epoch and writes its request...
        var setValue = new Thread(() -> epoch.send(stop, () -> {
            writing.countDown();
            await(mayWrite);
            return setVariable();
        }), "Set Value");
        setValue.start();
        assertTrue(writing.await(BOUND_MILLIS, TimeUnit.MILLISECONDS));
        // ... while Step Over ends the epoch to resume the program
        var step = new Thread(() -> {
            epoch.endBeforeResuming();
            written.add("next");
        }, "Step Over");
        step.start();

        assertEquals(Thread.State.BLOCKED, waitUntilBlockedByOrEnded(step, setValue),
                "the resume waits for the request");
        assertEquals(stop, epoch.get(), "the epoch doesn't end while its request is written");
        mayWrite.countDown();
        setValue.join(BOUND_MILLIS);
        step.join(BOUND_MILLIS);
        assertEquals(List.of("setVariable", "next"), written, "the request is written before the resume");
        assertNull(epoch.send(stop, this::setVariable), "the request of the epoch isn't sent after the resume");
    }

    @Test
    void aRefusalDoesNotWaitWhileARequestOfAnEpochIsWritten() throws Exception {
        epoch.end();
        int ended = epoch.endBeforeResuming();
        int stop = epoch.end();
        var writing = new CountDownLatch(1);
        var mayWrite = new CountDownLatch(1);
        // Set Value writes its request at the stop...
        var setValue = new Thread(() -> epoch.send(stop, () -> {
            writing.countDown();
            await(mayWrite);
            return setVariable();
        }), "Set Value");
        setValue.start();
        assertTrue(writing.await(BOUND_MILLIS, TimeUnit.MILLISECONDS), "Set Value doesn't write its request");
        // ... while the thread which reads the messages of the debug adapter handles the refusal of an earlier request
        var reader = new Thread(() -> epoch.resumeRefused(ended), "DAP messages");
        reader.start();
        try {
            assertEquals(Thread.State.TERMINATED, waitUntilBlockedByOrEnded(reader, setValue),
                    "the refusal waits for the request");
        } finally {
            mayWrite.countDown();
            setValue.join(BOUND_MILLIS);
            reader.join(BOUND_MILLIS);
        }
        assertEquals(stop, epoch.get(), "the epoch of the stop stays the current one");
        assertEquals(List.of("setVariable"), written);
    }

    private CompletableFuture<String> setVariable() {
        written.add("setVariable");
        return CompletableFuture.completedFuture("42");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(BOUND_MILLIS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /**
     * Waits until the given thread is blocked on a lock which the given owner holds, or has ended, and returns its
     * state then.
     * <p>
     * A thread is also blocked for a moment on other locks of the JVM, such as a lock of the coverage agent or of a
     * class which another thread of the test's application loads: only a lock which the owner holds, which is the lock
     * of the epoch while the owner writes its request, counts.
     */
    private static Thread.State waitUntilBlockedByOrEnded(Thread thread, Thread owner) throws InterruptedException {
        var threads = ManagementFactory.getThreadMXBean();
        long end = System.currentTimeMillis() + BOUND_MILLIS;
        while (System.currentTimeMillis() < end) {
            var state = thread.getState();
            if (state == Thread.State.TERMINATED) {
                return state;
            }
            if (state == Thread.State.BLOCKED) {
                ThreadInfo info = threads.getThreadInfo(thread.getId());
                if (info != null && info.getLockOwnerId() == owner.getId()) {
                    return state;
                }
            }
            Thread.sleep(1);
        }
        return thread.getState();
    }
}
