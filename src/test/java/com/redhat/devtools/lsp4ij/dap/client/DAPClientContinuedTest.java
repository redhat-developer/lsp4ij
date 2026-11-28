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

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 'continued' event shows the program as running: the session resumes when it is suspended at a stop of the client
 * which received the event, and the event continued the thread of that stop. A 'continued' event of another thread
 * leaves the session suspended, and shows the threads again. A stop whose thread continued while the session was
 * being suspended there doesn't stay suspended. The resume doesn't interleave with the suspension at another stop.
 */
public class DAPClientContinuedTest {

    private static final int STOPPED_THREAD = 1;
    private static final int OTHER_THREAD = 2;
    private static final long BOUND_SECONDS = 30;

    private final DAPSessionChanges sessionChanges = new DAPSessionChanges();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    // The suspend context of a stop of the client which receives the events
    private final XSuspendContext ownStop = new XSuspendContext() {
    };
    // The suspend context of a stop of another client, such as another session of a tree ('startDebugging')
    private final XSuspendContext otherClientStop = new XSuspendContext() {
    };

    @Test
    void resumesWhenAllThreadsContinued() {
        DAPClient.continued(session(true, ownStop), sessionChanges, event(OTHER_THREAD, true),
                this::stoppedThread, this::refreshThreads);

        assertEquals(List.of("resumed"), calls);
    }

    @Test
    void resumesWhenTheEventDoesNotSayWhetherAllThreadsContinued() {
        // DAP: "If omitted or set to `true`, this event signals to the client that all threads have been resumed."
        DAPClient.continued(session(true, ownStop), sessionChanges, event(OTHER_THREAD, null),
                this::stoppedThread, this::refreshThreads);

        assertEquals(List.of("resumed"), calls);
    }

    @Test
    void resumesWhenTheStoppedThreadContinuedAlone() {
        DAPClient.continued(session(true, ownStop), sessionChanges, event(STOPPED_THREAD, false),
                this::stoppedThread, this::refreshThreads);

        assertEquals(List.of("resumed"), calls);
    }

    @Test
    void staysSuspendedAndShowsTheThreadsAgainWhenAnotherThreadContinuedAlone() {
        DAPClient.continued(session(true, ownStop), sessionChanges, event(OTHER_THREAD, false),
                this::stoppedThread, this::refreshThreads);

        assertEquals(List.of("threads"), calls);
    }

    @Test
    void staysSuspendedAtAStopOfAnotherClient() {
        // a worker's 'continued' doesn't resume a session suspended in the page's session
        DAPClient.continued(session(true, otherClientStop), sessionChanges, event(STOPPED_THREAD, true),
                this::stoppedThread, this::refreshThreads);
        DAPClient.continued(session(true, otherClientStop), sessionChanges, event(STOPPED_THREAD, false),
                this::stoppedThread, this::refreshThreads);

        assertEquals(List.of(), calls);
    }

    @Test
    void doesNothingWhenTheSessionIsNotSuspended() {
        // as after a resume or a step of the session itself, which the debug adapter may follow with 'continued'
        DAPClient.continued(session(false, null), sessionChanges, event(STOPPED_THREAD, true),
                this::stoppedThread, this::refreshThreads);
        DAPClient.continued(session(false, null), sessionChanges, event(OTHER_THREAD, false),
                this::stoppedThread, this::refreshThreads);

        assertEquals(List.of(), calls);
    }

    @Test
    void aStopOfAnotherThreadWhichComesWhileTheEventResumesTheSessionSuspendsItAfterwardsAndTheSessionStaysThere() {
        // The stop of the other thread, which the event doesn't continue
        var otherThreadStop = new XSuspendContext() {
        };
        var context = new AtomicReference<>(ownStop);
        var stopCame = new AtomicBoolean();
        var session = new AtomicReference<XDebugSession>();
        session.set(session(context, () -> {
            if (stopCame.compareAndSet(false, true)) {
                // The other thread stopped before the event came, and the client has its frames on another thread
                // while the event resumes the session: that thread doesn't wait
                await(CompletableFuture.runAsync(() -> sessionChanges.execute(
                        () -> session.get().positionReached(otherThreadStop))), "the thread of the stop returns");
            }
        }));

        DAPClient.continued(session.get(), sessionChanges, event(STOPPED_THREAD, false), this::stoppedThread,
                this::refreshThreads);

        assertEquals(List.of("resumed", "suspended"), calls);
        assertSame(otherThreadStop, context.get(), "the session stays at the stop of the other thread");
    }

    @Test
    void anEventContinuesAllThreadsUnlessItSaysThatOnlyItsThreadContinued() {
        assertTrue(DAPClient.continues(event(OTHER_THREAD, null), STOPPED_THREAD));
        assertTrue(DAPClient.continues(event(OTHER_THREAD, true), STOPPED_THREAD));
        assertTrue(DAPClient.continues(event(STOPPED_THREAD, false), STOPPED_THREAD));
        assertFalse(DAPClient.continues(event(OTHER_THREAD, false), STOPPED_THREAD));
    }

    @Test
    void aStopWhichDidNotContinueStaysSuspended() {
        assertFalse(DAPClient.resumeIfContinued(session(true, ownStop), ownStop, () -> false));

        assertEquals(List.of(), calls);
    }

    @Test
    void aStopWhichContinuedWhileTheSessionWasBeingSuspendedResumesTheSession() {
        // the 'continued' event arrived before the session was suspended at the stop, and found nothing to resume
        assertTrue(DAPClient.resumeIfContinued(session(true, ownStop), ownStop, () -> true));

        assertEquals(List.of("resumed"), calls);
    }

    @Test
    void aStopWhichContinuedLeavesTheSessionAsItIsOnceItIsNoLongerSuspendedThere() {
        // the 'continued' event resumed the session itself
        assertTrue(DAPClient.resumeIfContinued(session(false, null), ownStop, () -> true));
        // another stop suspended the session since
        assertTrue(DAPClient.resumeIfContinued(session(true, otherClientStop), ownStop, () -> true));

        assertEquals(List.of(), calls);
    }

    private @Nullable Integer stoppedThread(@Nullable XSuspendContext context) {
        return context == ownStop ? STOPPED_THREAD : null;
    }

    private void refreshThreads() {
        calls.add("threads");
    }

    private static ContinuedEventArguments event(int threadId, @Nullable Boolean allThreadsContinued) {
        var args = new ContinuedEventArguments();
        args.setThreadId(threadId);
        args.setAllThreadsContinued(allThreadsContinued);
        return args;
    }

    private XDebugSession session(boolean suspended, @Nullable XSuspendContext context) {
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class<?>[]{XDebugSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isSuspended" -> suspended;
                    case "getSuspendContext" -> context;
                    case "sessionResumed" -> {
                        calls.add("resumed");
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Returns a debug session suspended at the given suspend context, or running for null, which positionReached()
     * suspends, and which sessionResumed() resumes once it ran the given runnable.
     */
    private XDebugSession session(@NotNull AtomicReference<@Nullable XSuspendContext> context,
                                  @NotNull Runnable resuming) {
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class<?>[]{XDebugSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isSuspended" -> context.get() != null;
                    case "getSuspendContext" -> context.get();
                    case "positionReached" -> {
                        calls.add("suspended");
                        context.set((XSuspendContext) args[0]);
                        yield null;
                    }
                    case "sessionResumed" -> {
                        resuming.run();
                        calls.add("resumed");
                        context.set(null);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static void await(@NotNull CompletableFuture<?> future, @NotNull String what) {
        try {
            future.get(BOUND_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError(what, e);
        }
    }
}
