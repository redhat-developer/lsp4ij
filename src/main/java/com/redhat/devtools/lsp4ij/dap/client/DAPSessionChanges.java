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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the changes of a debug session which must not interleave, one at a time and in the order in which they come,
 * and makes no thread wait for them: a change runs right away on the thread which brings it, unless another change
 * runs, and then after the changes before it, on the thread which runs those.
 * <p>
 * A stop suspends the session on the thread which got its frames, while the answer to a request which IntelliJ doesn't
 * resume the session for, such as 'reverseContinue', resumes it on the thread which handles the answer, which may be
 * the EDT. Interleaved, the resume can undo the stop, and the session then shows the program running although it is
 * stopped. The refusal of a request which IntelliJ resumed the session for, which suspends the session again at the
 * stop of the request, can replace a newer stop in the same way. A lock would make one of these threads wait for the
 * other, while the other runs the listeners of the session, which may wait for the EDT, as a synchronous refresh of
 * the VFS does when the position of a frame is first computed, or for LSP4J's thread: here no thread waits for
 * another.
 * <p>
 * The clients of a tree of DAP sessions ('startDebugging') share the debug session, and so share these changes.
 * <p>
 * It is a class of its own so that it can be tested without a debug adapter, which a {@link DAPClient} can hardly be.
 */
final class DAPSessionChanges implements Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DAPSessionChanges.class);

    // The changes which haven't run yet, in the order in which they came
    private final Queue<Runnable> changes = new ConcurrentLinkedQueue<>();
    // The number of changes which came and haven't run yet, the running one included: the thread which raises it from 0
    // runs the changes until it is 0 again
    private final AtomicInteger pending = new AtomicInteger();

    /**
     * Runs the given change of the debug session once the changes which came before it have run: right away on the
     * calling thread when no other change runs, and otherwise later, on the thread which runs the changes. The calling
     * thread never waits for another change.
     *
     * @param change the change of the debug session.
     */
    @Override
    public void execute(@NotNull Runnable change) {
        changes.add(change);
        if (pending.getAndIncrement() > 0) {
            // The thread which runs the changes before this one runs it after them
            return;
        }
        do {
            try {
                changes.remove().run();
            } catch (Throwable e) {
                // The changes after it still run
                LOGGER.error("A change of the debug session failed", e);
            }
        } while (pending.decrementAndGet() > 0);
    }
}
