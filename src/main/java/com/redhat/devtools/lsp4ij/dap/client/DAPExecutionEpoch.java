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
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The execution epochs of the program of a debug adapter: an epoch ends whenever the program resumes or stops. The
 * references which the debug adapter gives during an epoch, such as the id of a stack frame or a 'variablesReference',
 * are valid only until the epoch ends: DAP says that they are valid while the program stays suspended, and a debug
 * adapter may give them to something else at the next stop, as GDB numbers the frames of each stop from 0.
 * <p>
 * A request which names references of an epoch is sent with {@link #send(int, Supplier)}, which checks that the epoch
 * is the current one and sends the request under the lock which {@link #endBeforeResuming()} takes before the client
 * sends a request which resumes the program. LSP4J writes a request on the thread which sends it, so the request is
 * written before the request which resumes the program, or not at all.
 * <p>
 * When the debug adapter refuses a request which resumes the program, the epoch which it ended is the current one
 * again ({@link #resumeRefused(int)}). Several such requests can be pending at a stop, since Step Back, Reverse Continue
 * and Reset Frame leave the session suspended until the debug adapter answers: the epoch of the stop is the current
 * one again only once the debug adapter refused each of them.
 * <p>
 * A 'continued' event which says that only its thread continued ({@code allThreadsContinued: false}) doesn't end the
 * epoch: the debug adapters which run a thread alone by themselves, such as java-debug, debugpy and vscode-js-debug,
 * keep the references of the threads which stay stopped. Only the references of the thread which continued are out of
 * date, which the client tells by the run states of the threads ({@link DAPThreadRunStates#leftStopSince(int, int)})
 * with the lock held ({@link #send(int, BooleanSupplier, Supplier)}). Everything else ends the epoch for every thread:
 * <ul>
 *     <li>a 'continued' event of all threads;</li>
 *     <li>any 'stopped' event, as a debug adapter may number the references of each stop from 1 again, as Delve does:
 *     one with {@code allThreadsStopped: true} ends it even for a thread which never left its stop;</li>
 *     <li>a request of the client which resumes the program, even one which resumes a single thread ('singleThread'):
 *     GDB, the debug adapter which supports them, forgets all its references whenever the program resumes.</li>
 * </ul>
 * <p>
 * It is a class of its own so that it can be tested without a debug adapter, which a {@link DAPClient} can hardly be.
 */
final class DAPExecutionEpoch {

    private final AtomicInteger current = new AtomicInteger();
    // The epoch which the last 'stopped' or 'continued' event which ended an epoch started: the program ran since the
    // requests which resumed it before, and no refusal makes their epochs current again
    private final AtomicInteger lastEvent = new AtomicInteger();
    // Held only while a request of an epoch is written, and while an epoch ends before a request resumes the program
    private final Object lock = new Object();
    // Guarded by itself, not by the lock, which the thread that reads the messages of the debug adapter doesn't wait
    // for: the epochs which requests that the debug adapter refused ended, while a request sent after them, which the
    // debug adapter accepted or hasn't answered yet, ended the epoch which followed
    private final Set<Integer> refused = new HashSet<>();

    /**
     * Returns the current epoch.
     *
     * @return the current epoch, 0 before the program first stops.
     */
    int get() {
        return current.get();
    }

    /**
     * Ends the current epoch because the debug adapter says that the program stopped, or that all its threads
     * continued. The program has already run by then, so that no lock can order a request before it: the thread which
     * reads the messages of the debug adapter doesn't wait for a request which another thread writes.
     *
     * @return the new epoch.
     */
    int end() {
        int epoch = current.incrementAndGet();
        lastEvent.accumulateAndGet(epoch, Math::max);
        return epoch;
    }

    /**
     * Ends the current epoch before the client sends a request which resumes the program: a request which
     * {@link #send(int, Supplier)} writes meanwhile is written first.
     *
     * @return the epoch which ended.
     */
    int endBeforeResuming() {
        synchronized (lock) {
            return current.getAndIncrement();
        }
    }

    /**
     * Makes the epoch which {@link #endBeforeResuming()} ended the current one again, when the debug adapter refused
     * to resume the program, unless the debug adapter said since that the program stopped or continued. When requests
     * sent after it, such as a second Step Back, ended the epochs which followed, it is the current one again once the
     * debug adapter refused each of them too: it stays ended while one of them is pending, and once the debug adapter
     * accepted one.
     *
     * @param ended the epoch which {@link #endBeforeResuming()} ended.
     */
    void resumeRefused(int ended) {
        synchronized (refused) {
            // A 'stopped' or 'continued' event ended the epochs of the requests sent before it for good
            int event = lastEvent.get();
            refused.removeIf(refusedEpoch -> refusedEpoch < event);
            if (ended >= event) {
                refused.add(ended);
            }
            // The latest epoch which a refused request ended is the current one again, and so on back, unless an event
            // or a request ends the current epoch meanwhile
            int epoch = current.get();
            while (refused.contains(epoch - 1) && current.compareAndSet(epoch, epoch - 1)) {
                refused.remove(--epoch);
            }
        }
    }

    /**
     * Returns true if the debug adapter said that the program stopped or continued since the given epoch ended before
     * a request resumed the program: the event is newer than the request.
     *
     * @param ended the epoch which {@link #endBeforeResuming()} ended.
     * @return true if the debug adapter said that the program stopped or continued since the given epoch ended, and
     * false otherwise.
     */
    boolean stoppedOrContinuedSince(int ended) {
        return lastEvent.get() > ended;
    }

    /**
     * Sends a request which names references of the given epoch, if it is the current one.
     *
     * @param epoch   the epoch of the references which the request names.
     * @param request sends the request: called with the lock held, it must only write the request.
     * @param <T>     the type of the response.
     * @return the future of the response, or null if the epoch has ended and the request was not sent.
     */
    <T> @Nullable CompletableFuture<T> send(int epoch, @NotNull Supplier<CompletableFuture<T>> request) {
        return send(epoch, () -> true, request);
    }

    /**
     * Sends a request which names references of the given epoch, if it is the current one and the references are
     * still valid in it, as when the thread of a stack frame hasn't continued alone since its stop.
     *
     * @param epoch   the epoch of the references which the request names.
     * @param valid   returns whether the references are still valid in the epoch: called with the lock held, once the
     *                epoch is known to be the current one.
     * @param request sends the request: called with the lock held, it must only write the request.
     * @param <T>     the type of the response.
     * @return the future of the response, or null if the epoch has ended, or the references are no longer valid, and
     * the request was not sent.
     */
    <T> @Nullable CompletableFuture<T> send(int epoch,
                                            @NotNull BooleanSupplier valid,
                                            @NotNull Supplier<CompletableFuture<T>> request) {
        synchronized (lock) {
            return epoch == current.get() && valid.getAsBoolean() ? request.get() : null;
        }
    }
}
