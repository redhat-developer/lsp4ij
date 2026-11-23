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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
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
 * It is a class of its own so that it can be tested without a debug adapter, which a {@link DAPClient} can hardly be.
 */
final class DAPExecutionEpoch {

    private final AtomicInteger current = new AtomicInteger();
    // Held only while a request of an epoch is written, and while an epoch ends before a request resumes the program
    private final Object lock = new Object();

    /**
     * Returns the current epoch.
     *
     * @return the current epoch, 0 before the program first stops.
     */
    int get() {
        return current.get();
    }

    /**
     * Ends the current epoch because the debug adapter says that the program stopped or continued. The program has
     * already run by then, so that no lock can order a request before it: the thread which reads the messages of the
     * debug adapter doesn't wait for a request which another thread writes.
     *
     * @return the new epoch.
     */
    int end() {
        return current.incrementAndGet();
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
     * to resume the program, unless the epoch has changed since.
     *
     * @param ended the epoch which {@link #endBeforeResuming()} ended.
     */
    void resumeRefused(int ended) {
        current.compareAndSet(ended + 1, ended);
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
        synchronized (lock) {
            return epoch == current.get() ? request.get() : null;
        }
    }
}
