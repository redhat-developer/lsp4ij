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
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 'continued' event shows the program as running: the session resumes when it is suspended at a stop of the client
 * which received the event, and the event continued the thread of that stop. A 'continued' event of another thread
 * leaves the session suspended, and shows the threads again. A stop whose thread continued while the session was
 * being suspended there doesn't stay suspended.
 */
public class DAPClientContinuedTest {

    private static final int STOPPED_THREAD = 1;
    private static final int OTHER_THREAD = 2;

    private final List<String> calls = new ArrayList<>();
    // The suspend context of a stop of the client which receives the events
    private final XSuspendContext ownStop = new XSuspendContext() {
    };
    // The suspend context of a stop of another client, such as another session of a tree ('startDebugging')
    private final XSuspendContext otherClientStop = new XSuspendContext() {
    };

    @Test
    void resumesWhenAllThreadsContinued() {
        assertTrue(DAPClient.continued(session(true, ownStop), event(OTHER_THREAD, true), this::stoppedThread, this::refreshThreads));

        assertEquals(List.of("resumed"), calls);
    }

    @Test
    void resumesWhenTheEventDoesNotSayWhetherAllThreadsContinued() {
        // DAP: "If omitted or set to `true`, this event signals to the client that all threads have been resumed."
        assertTrue(DAPClient.continued(session(true, ownStop), event(OTHER_THREAD, null), this::stoppedThread, this::refreshThreads));

        assertEquals(List.of("resumed"), calls);
    }

    @Test
    void resumesWhenTheStoppedThreadContinuedAlone() {
        assertTrue(DAPClient.continued(session(true, ownStop), event(STOPPED_THREAD, false), this::stoppedThread, this::refreshThreads));

        assertEquals(List.of("resumed"), calls);
    }

    @Test
    void staysSuspendedAndShowsTheThreadsAgainWhenAnotherThreadContinuedAlone() {
        assertFalse(DAPClient.continued(session(true, ownStop), event(OTHER_THREAD, false), this::stoppedThread, this::refreshThreads));

        assertEquals(List.of("threads"), calls);
    }

    @Test
    void staysSuspendedAtAStopOfAnotherClient() {
        // a worker's 'continued' doesn't resume a session suspended in the page's session
        assertFalse(DAPClient.continued(session(true, otherClientStop), event(STOPPED_THREAD, true), this::stoppedThread, this::refreshThreads));
        assertFalse(DAPClient.continued(session(true, otherClientStop), event(STOPPED_THREAD, false), this::stoppedThread, this::refreshThreads));

        assertEquals(List.of(), calls);
    }

    @Test
    void doesNothingWhenTheSessionIsNotSuspended() {
        // as after a resume or a step of the session itself, which the debug adapter may follow with 'continued'
        assertFalse(DAPClient.continued(session(false, null), event(STOPPED_THREAD, true), this::stoppedThread, this::refreshThreads));
        assertFalse(DAPClient.continued(session(false, null), event(OTHER_THREAD, false), this::stoppedThread, this::refreshThreads));

        assertEquals(List.of(), calls);
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
}
