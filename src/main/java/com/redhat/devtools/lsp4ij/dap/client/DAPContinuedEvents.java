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

import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The 'continued' events of a debug adapter, as far as a stop needs them: whether the thread which stopped has
 * continued since. A 'stopped' event is handled asynchronously, as the threads and the stack frames are asked for
 * first, so the debug adapter can say that the thread continued before the session is suspended at the stop; the
 * session must then not be suspended there.
 * <p>
 * A 'continued' event continues all threads, unless it says that only its own thread continued
 * ({@code allThreadsContinued: false}). A 'continued' event of another thread doesn't end a stop.
 * <p>
 * The events are recorded, and the marks taken, on the thread which reads the messages of the debug adapter, in the
 * order in which they arrive. The {@link DAPExecutionEpoch} can't tell this: it also ends at each later stop and
 * before each resume of the session, and at a 'continued' event of any thread.
 * <p>
 * It is a class of its own so that it can be tested without a debug adapter, which a {@link DAPClient} can hardly be.
 */
final class DAPContinuedEvents {

    // The number of 'continued' events so far
    private final AtomicInteger count = new AtomicInteger();
    // The number of the last 'continued' event of all threads, 0 before there is one
    private volatile int allThreads;
    // The number of the last 'continued' event of each thread which continued alone
    private final Map<Integer, Integer> threads = new ConcurrentHashMap<>();

    /**
     * Returns the mark of the 'continued' events so far, which a 'stopped' event takes as it arrives.
     *
     * @return the mark of the 'continued' events so far.
     */
    int mark() {
        return count.get();
    }

    /**
     * Records a 'continued' event, as it arrives.
     *
     * @param args the 'continued' event.
     */
    void continued(@NotNull ContinuedEventArguments args) {
        int event = count.incrementAndGet();
        if (Boolean.FALSE.equals(args.getAllThreadsContinued())) {
            threads.put(args.getThreadId(), event);
        } else {
            allThreads = event;
        }
    }

    /**
     * Returns whether a 'continued' event which arrived after the given mark continued the given thread.
     *
     * @param mark     the mark of the 'continued' events when the thread stopped.
     * @param threadId the DAP thread which stopped.
     * @return true if the thread continued since the mark and false otherwise.
     */
    boolean continuedSince(int mark, int threadId) {
        return allThreads > mark || threads.getOrDefault(threadId, 0) > mark;
    }
}
