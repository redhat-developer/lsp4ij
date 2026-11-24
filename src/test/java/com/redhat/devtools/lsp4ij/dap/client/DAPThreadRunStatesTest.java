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
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.redhat.devtools.lsp4ij.dap.client.DAPThreadRunState.EXITED;
import static com.redhat.devtools.lsp4ij.dap.client.DAPThreadRunState.RUNNING;
import static com.redhat.devtools.lsp4ij.dap.client.DAPThreadRunState.STOPPED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run state of each thread of a DAP session, as the 'stopped', 'continued' and 'thread' events, and the resumes and
 * steps of the session, change it: {@link DAPThreadRunStates}.
 */
public class DAPThreadRunStatesTest {

    private static final int MAIN = 1;
    private static final int WORKER = 2;
    // A thread which no event names
    private static final int OTHER = 3;

    @Test
    void allThreadsRunBeforeTheDebugAdapterSaysAnything() {
        var states = DAPThreadRunStates.INITIAL;

        assertStates(states, RUNNING, RUNNING, RUNNING);
    }

    @Test
    void aStopStopsOnlyItsThreadWithoutAllThreadsStopped() {
        assertStates(DAPThreadRunStates.INITIAL.stopped(stopped(MAIN, null)), STOPPED, RUNNING, RUNNING);
        assertStates(DAPThreadRunStates.INITIAL.stopped(stopped(MAIN, false)), STOPPED, RUNNING, RUNNING);
    }

    @Test
    void aStopWithAllThreadsStoppedStopsAllThreads() {
        var states = DAPThreadRunStates.INITIAL.stopped(stopped(MAIN, true));

        // Also the threads which the debug adapter hasn't reported yet
        assertStates(states, STOPPED, STOPPED, STOPPED);
    }

    @Test
    void aStopWithoutAThreadChangesNothingUnlessAllThreadsStopped() {
        var states = DAPThreadRunStates.INITIAL;

        assertSame(states, states.stopped(stopped(null, null)));
        assertStates(states.stopped(stopped(null, true)), STOPPED, STOPPED, STOPPED);
    }

    @Test
    void aContinuedEventOfAThreadAloneRunsOnlyThatThread() {
        var states = allStopped().continued(continued(WORKER, false));

        assertStates(states, STOPPED, RUNNING, STOPPED);
    }

    @Test
    void aContinuedEventRunsAllThreadsUnlessOnlyItsThreadContinued() {
        // DAP: "If omitted or set to `true`, this event signals to the client that all threads have been resumed."
        assertStates(allStopped().continued(continued(WORKER, null)), RUNNING, RUNNING, RUNNING);
        assertStates(allStopped().continued(continued(WORKER, true)), RUNNING, RUNNING, RUNNING);
    }

    @Test
    void aResumeOfTheSessionRunsAllThreads() {
        // Resume, a step which isn't a single-thread step, or a breakpoint which doesn't suspend
        var states = allStopped().resumed(MAIN, false);

        assertStates(states, RUNNING, RUNNING, RUNNING);
    }

    @Test
    void aSingleThreadStepRunsOnlyTheThreadWhichSteps() {
        var states = allStopped().resumed(MAIN, true);

        assertStates(states, RUNNING, STOPPED, STOPPED);
    }

    @Test
    void theStopOfTheThreadWhichStepsAloneLeavesTheOthersStopped() {
        var states = allStopped().resumed(MAIN, true).stopped(stopped(MAIN, null));

        assertStates(states, STOPPED, STOPPED, STOPPED);
    }

    @Test
    void aThreadWhichExitedStaysSoWhenAllThreadsStopOrRun() {
        var states = DAPThreadRunStates.INITIAL.thread(thread(ThreadEventArgumentsReason.EXITED, WORKER));
        assertStates(states, RUNNING, EXITED, RUNNING);

        states = states.stopped(stopped(MAIN, true));
        assertStates(states, STOPPED, EXITED, STOPPED);

        states = states.continued(continued(MAIN, null));
        assertStates(states, RUNNING, EXITED, RUNNING);

        states = states.resumed(MAIN, false);
        assertStates(states, RUNNING, EXITED, RUNNING);
    }

    @Test
    void aThreadWhichStartsRuns() {
        // Such as a thread whose ID the debug adapter gives again once the thread which had it exited
        var states = allStopped()
                .thread(thread(ThreadEventArgumentsReason.EXITED, WORKER))
                .thread(thread(ThreadEventArgumentsReason.STARTED, WORKER));

        assertStates(states, STOPPED, RUNNING, STOPPED);
    }

    @Test
    void aThreadEventOfAnotherReasonChangesNothing() {
        var states = allStopped();

        assertSame(states, states.thread(thread("renamed", WORKER)));
    }

    @Test
    void theRunStatesOfTheThreadsWhichTheDebugAdapterNoLongerReportsAreForgotten() {
        var states = allStopped()
                .thread(thread(ThreadEventArgumentsReason.EXITED, WORKER))
                .continued(continued(OTHER, false));

        var retained = states.retain(List.of(MAIN, OTHER), states.getVersion());

        // The debug adapter no longer reports the worker, which exited: it has the run state of all threads again
        assertStates(retained, STOPPED, STOPPED, RUNNING);
        assertEquals(states.getVersion(), retained.getVersion());
        assertSame(retained, retained.retain(List.of(MAIN, OTHER), retained.getVersion()));
    }

    @Test
    void aThreadWhichChangedAfterTheThreadsRequestIsNotForgottenByItsAnswer() {
        var asked = DAPThreadRunStates.INITIAL.stopped(stopped(MAIN, false));
        // After the 'threads' request was sent, the worker started and stopped
        var states = asked.thread(thread(ThreadEventArgumentsReason.STARTED, WORKER)).stopped(stopped(WORKER, false));

        // The answer, written before the worker started, doesn't list it
        var retained = states.retain(List.of(MAIN), asked.getVersion());

        assertStates(retained, STOPPED, STOPPED, RUNNING);
        assertSame(states, retained);
    }

    @Test
    void aChangeGivesNewRunStatesWithANewVersion() {
        var initial = DAPThreadRunStates.INITIAL;

        var stopped = initial.stopped(stopped(MAIN, true));

        assertStates(initial, RUNNING, RUNNING, RUNNING);
        assertTrue(stopped.getVersion() > initial.getVersion());
    }

    @Test
    void aThreadWhichContinuedAloneLeftTheStop() {
        var stop = allStopped();

        var states = stop.continued(continued(WORKER, false));

        assertTrue(states.leftStopSince(stop.getVersion(), WORKER));
        assertFalse(states.leftStopSince(stop.getVersion(), MAIN));
    }

    @Test
    void aThreadWhichExitedLeftTheStop() {
        var stop = allStopped();

        var states = stop.thread(thread(ThreadEventArgumentsReason.EXITED, WORKER));

        assertTrue(states.leftStopSince(stop.getVersion(), WORKER));
    }

    @Test
    void aThreadWhichContinuedAndStoppedAgainDidNotLeaveTheStop() {
        var stop = allStopped();

        var states = stop.continued(continued(WORKER, false)).stopped(stopped(WORKER, false));

        assertFalse(states.leftStopSince(stop.getVersion(), WORKER));
    }

    @Test
    void aThreadWhichRanAtAStopWithoutAllThreadsStoppedDidNotLeaveIt() {
        // It ran before the stop already: the Frames view still asks the debug adapter for its frames, as before
        var stop = DAPThreadRunStates.INITIAL.stopped(stopped(MAIN, null));

        assertFalse(stop.leftStopSince(stop.getVersion(), WORKER));
        assertTrue(stop.continued(continued(WORKER, false)).leftStopSince(stop.getVersion(), WORKER));
    }

    private static @NotNull DAPThreadRunStates allStopped() {
        return DAPThreadRunStates.INITIAL.stopped(stopped(MAIN, true));
    }

    private static void assertStates(@NotNull DAPThreadRunStates states,
                                     @NotNull DAPThreadRunState main,
                                     @NotNull DAPThreadRunState worker,
                                     @NotNull DAPThreadRunState other) {
        assertEquals(List.of(main, worker, other),
                List.of(states.getState(MAIN), states.getState(WORKER), states.getState(OTHER)),
                "The run states of the threads main, worker and other");
    }

    private static @NotNull StoppedEventArguments stopped(@Nullable Integer threadId, @Nullable Boolean allThreadsStopped) {
        var args = new StoppedEventArguments();
        args.setReason(StoppedEventArgumentsReason.BREAKPOINT);
        args.setThreadId(threadId);
        args.setAllThreadsStopped(allThreadsStopped);
        return args;
    }

    private static @NotNull ContinuedEventArguments continued(int threadId, @Nullable Boolean allThreadsContinued) {
        var args = new ContinuedEventArguments();
        args.setThreadId(threadId);
        args.setAllThreadsContinued(allThreadsContinued);
        return args;
    }

    private static @NotNull ThreadEventArguments thread(@NotNull String reason, int threadId) {
        var args = new ThreadEventArguments();
        args.setReason(reason);
        args.setThreadId(threadId);
        return args;
    }
}
