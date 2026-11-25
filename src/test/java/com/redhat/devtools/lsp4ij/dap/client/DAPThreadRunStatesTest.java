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
    void aRefusedResumeGivesTheThreadsTheirRunStatesBack() {
        var stop = allStopped();
        var resumed = stop.resumed(MAIN, false);

        var refused = resumed.resumeRefused(stop, resumed);

        assertStates(refused, STOPPED, STOPPED, STOPPED);
        // The threads didn't leave the stop, whose frames the session shows again
        assertFalse(refused.leftStopSince(stop.getVersion(), MAIN));
        assertFalse(refused.leftStopSince(stop.getVersion(), WORKER));
        assertTrue(refused.getVersion() > resumed.getVersion());
    }

    @Test
    void aRefusedSingleThreadStepGivesTheThreadWhichStepsItsRunStateBack() {
        // The worker ran, as at a stop without allThreadsStopped
        var stop = DAPThreadRunStates.INITIAL.stopped(stopped(MAIN, false));
        var resumed = stop.resumed(MAIN, true);

        assertStates(resumed.resumeRefused(stop, resumed), STOPPED, RUNNING, RUNNING);
    }

    @Test
    void aRefusedResumeKeepsWhatANewerEventChanged() {
        var stop = allStopped();
        var resumed = stop.resumed(MAIN, false);

        // The worker exited after the request was sent
        var exited = resumed.thread(thread(ThreadEventArgumentsReason.EXITED, WORKER));
        assertStates(exited.resumeRefused(stop, resumed), STOPPED, EXITED, STOPPED);

        // All threads stopped, or continued, after the request was sent
        var stoppedAgain = resumed.stopped(stopped(WORKER, true));
        assertSame(stoppedAgain, stoppedAgain.resumeRefused(stop, resumed));
        var continued = resumed.continued(continued(MAIN, null));
        assertSame(continued, continued.resumeRefused(stop, resumed));
    }

    @Test
    void twoRefusedResumesGiveTheThreadsTheirRunStatesBackInEitherOrder() {
        var resumes = new TwoResumes();

        // The debug adapter hasn't answered the other request yet, which resumes all threads
        assertStates(resumes.second.resumeRefused(resumes.mainStop, resumes.first), RUNNING, RUNNING, RUNNING);
        assertStates(resumes.second.resumeRefused(resumes.workerStop, resumes.second), RUNNING, STOPPED, RUNNING);

        // Once it refused both, both threads are where they stopped, and the other thread, which didn't stop, runs
        for (var refused : List.of(resumes.refuseFirstThenSecond(), resumes.refuseSecondThenFirst())) {
            assertStates(refused, STOPPED, STOPPED, RUNNING);
            assertFalse(refused.leftStopSince(resumes.mainStop.getVersion(), MAIN));
            assertFalse(refused.leftStopSince(resumes.workerStop.getVersion(), MAIN));
            assertFalse(refused.leftStopSince(resumes.workerStop.getVersion(), WORKER));
        }
    }

    @Test
    void aRefusedStepDoesNotGiveBackWhatAnOlderRefusedResumeChanged() {
        // The main thread stops alone before the debug adapter answers Resume, and steps alone from there
        var stop = allStopped();
        var resume = stop.resumed(MAIN, false);
        var mainStop = resume.stopped(stopped(MAIN, false));
        var step = mainStop.resumed(MAIN, true);

        var resumeThenStep = step.resumeRefused(stop, resume).resumeRefused(mainStop, step);
        var stepThenResume = step.resumeRefused(mainStop, step).resumeRefused(stop, resume);

        // The debug adapter refused both: no thread ran since all threads stopped
        assertStates(resumeThenStep, STOPPED, STOPPED, STOPPED);
        assertStates(stepThenResume, STOPPED, STOPPED, STOPPED);
    }

    @Test
    void aRefusedResumeKeepsWhatANewerResumeWhichTheDebugAdapterAcceptedChanged() {
        var resumes = new TwoResumes();

        // The second resumes all threads, the main thread too
        assertStates(resumes.second.resumeRefused(resumes.mainStop, resumes.first), RUNNING, RUNNING, RUNNING);
        // The first resumed all threads, and the worker stopped after it
        assertStates(resumes.second.resumeRefused(resumes.workerStop, resumes.second), RUNNING, STOPPED, RUNNING);
    }

    @Test
    void twoRefusedResumesKeepWhatANewerEventChanged() {
        var resumes = new TwoResumes();

        // All threads stopped, or continued, after the second request was sent
        var stoppedAgain = resumes.second.stopped(stopped(WORKER, true));
        assertSame(stoppedAgain, resumes.refuseFirstThenSecond(stoppedAgain));
        assertSame(stoppedAgain, resumes.refuseSecondThenFirst(stoppedAgain));
        var continued = resumes.second.continued(continued(MAIN, null));
        assertSame(continued, resumes.refuseFirstThenSecond(continued));
        assertSame(continued, resumes.refuseSecondThenFirst(continued));

        // The main thread continued alone after the second request was sent
        var mainContinued = resumes.second.continued(continued(MAIN, false));
        assertStates(resumes.refuseFirstThenSecond(mainContinued), RUNNING, STOPPED, RUNNING);
        assertStates(resumes.refuseSecondThenFirst(mainContinued), RUNNING, STOPPED, RUNNING);
    }

    /**
     * The main thread stops alone, at a breakpoint which doesn't suspend, where the session resumes the program; the
     * worker then stops alone at such a breakpoint too, where the session resumes the program again, before the debug
     * adapter answers the first request.
     */
    private static final class TwoResumes {

        private final DAPThreadRunStates mainStop = DAPThreadRunStates.INITIAL.stopped(stopped(MAIN, false));
        private final DAPThreadRunStates first = mainStop.resumed(MAIN, false);
        private final DAPThreadRunStates workerStop = first.stopped(stopped(WORKER, false));
        private final DAPThreadRunStates second = workerStop.resumed(WORKER, false);

        @NotNull DAPThreadRunStates refuseFirstThenSecond() {
            return refuseFirstThenSecond(second);
        }

        @NotNull DAPThreadRunStates refuseSecondThenFirst() {
            return refuseSecondThenFirst(second);
        }

        /**
         * Returns the given run states, which followed the second request, once the debug adapter refused the first
         * request, and then the second.
         */
        @NotNull DAPThreadRunStates refuseFirstThenSecond(@NotNull DAPThreadRunStates states) {
            return states.resumeRefused(mainStop, first).resumeRefused(workerStop, second);
        }

        /**
         * Returns the given run states, which followed the second request, once the debug adapter refused the second
         * request, and then the first.
         */
        @NotNull DAPThreadRunStates refuseSecondThenFirst(@NotNull DAPThreadRunStates states) {
            return states.resumeRefused(workerStop, second).resumeRefused(mainStop, first);
        }
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
