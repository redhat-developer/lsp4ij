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
package com.redhat.devtools.lsp4ij.dap.stepping;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.frame.XValueModifier;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointProperties;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointType;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.DAPSuspendContext;
import com.redhat.devtools.lsp4ij.dap.client.DAPThreadRunState;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValue;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileNamePatternMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerMapping;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.ContinueResponse;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.ReverseContinueArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JTable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * A Resume or a step which the DAP server refuses, against a DAP server in the test's process which the session
 * attaches to. IntelliJ shows the session running before it asks the debug process for the request; when the DAP
 * server answers it with an error, the program is still stopped where it was: the session is suspended there again,
 * at the same frame, the console says why, the Threads tab shows the thread stopped, and the values of the frame can
 * still be set. The same holds for the 'continue' or the 'reverseContinue' which resumes the program at a breakpoint
 * which doesn't suspend, in the direction it ran, unless the DAP server said meanwhile that the program stopped or
 * continued: the session then follows that newer event. A Reverse Continue which is pending meanwhile, as it leaves the
 * session suspended until the DAP server answers, keeps the stop ended until the DAP server refuses it too. When two
 * threads stop alone at such a breakpoint, one after the other before the DAP server answers the first resume, and it
 * refuses both, in either order, the Threads tab shows both threads stopped.
 */
public class DAPRefusedResumeSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final int MAIN = 1;
    private static final int WORKER = 2;
    private static final int LOCALS_REFERENCE = 1000;
    private static final int BREAKPOINT_ID = 7;
    private static final String SERVER_ID = "dap-refused-resume-test";
    private static final String REFUSAL = "Thread 1 cannot run alone: it waits for a lock that another thread holds";

    private RefusingServer server;
    private XDebugSession session;
    // The client of the session, once the program first stopped
    private DAPClient client;
    private @Nullable DebugAdapterServerDefinition serverDefinition;
    private @Nullable Path dir;

    @Override
    protected void tearDown() throws Exception {
        try {
            if (session != null && !session.isStopped()) {
                session.stop();
            }
            if (server != null) {
                server.close();
            }
            if (serverDefinition != null) {
                DebugAdapterManager.getInstance().removeDebugAdapterServer(serverDefinition);
            }
            if (dir != null) {
                FileUtil.delete(dir.toFile());
            }
        } finally {
            super.tearDown();
        }
    }

    public void testARefusedStepOverLeavesTheSessionSuspendedAtItsFrameAndSaysWhy() throws Exception {
        debug(false, "next");
        var context = (DAPSuspendContext) session.getSuspendContext();
        var frame = (DAPStackFrame) session.getCurrentStackFrame();
        var paused = recordPauses();

        session.stepOver(false);
        assertInstanceOf(request(), NextArguments.class);

        waitUntilSuspendedAgainAt(context, frame, paused, "once the DAP server refused Step Over");
        waitForConsoleLine("Cannot step over: " + REFUSAL);
        assertStopRestored(context);

        // The values of the frame can still be set: the program has not run since they were shown
        var answer = local(frame, "answer");
        assertNull("Set Value at the frame of the stop", setValue(answer, "42"));
        var setVariable = server.setVariables.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setVariable'", setVariable);
        assertEquals("answer", setVariable.getName());
        assertEquals(LOCALS_REFERENCE, setVariable.getVariablesReference());
        assertEquals("42", setVariable.getValue());

        // The steps work again from there
        session.stepInto();
        assertInstanceOf(request(), StepInArguments.class);
        server.stop(StoppedEventArgumentsReason.STEP);
        waitUntilStoppedAt(2, "where the thread stepped into");
    }

    public void testARefusedResumeLeavesTheSessionSuspendedAtItsFrameAndSaysWhy() throws Exception {
        debug(false, "continue");
        var context = (DAPSuspendContext) session.getSuspendContext();
        var frame = (DAPStackFrame) session.getCurrentStackFrame();
        var paused = recordPauses();

        session.resume();
        assertInstanceOf(request(), ContinueArguments.class);

        waitUntilSuspendedAgainAt(context, frame, paused, "once the DAP server refused Resume Program");
        waitForConsoleLine("Cannot resume the program: " + REFUSAL);
        assertStopRestored(context);

        // Step Over works again from there
        session.stepOver(false);
        assertInstanceOf(request(), NextArguments.class);
        server.stop(StoppedEventArgumentsReason.STEP);
        waitUntilStoppedAt(2, "where the thread stepped over to");
    }

    public void testARefusedResumeAtABreakpointWhichDoesNotSuspendSuspendsTheSessionThere() throws Exception {
        // The program runs to a breakpoint which only logs, and the DAP server refuses to resume it from there: the
        // session, which IntelliJ didn't suspend at the breakpoint, is suspended there
        var paused = runToBreakpointWhichDoesNotSuspend(false, null);

        waitForConsoleLine("Cannot resume the program: " + REFUSAL);
        waitUntilStoppedAt(2, "at the breakpoint, once the DAP server refused to resume the program from there");
        assertStopRestored((DAPSuspendContext) session.getSuspendContext());
        assertPausedAt(paused, "since it ran to the breakpoint", 2);
    }

    public void testARefusedReverseContinueAtABreakpointWhichDoesNotSuspendSuspendsTheSessionThere() throws Exception {
        // Reverse Continue runs the program backwards to a breakpoint which only logs, where the client continues it
        // backwards, which the DAP server refuses
        var paused = runToBreakpointWhichDoesNotSuspend(true, null);

        waitForConsoleLine("Cannot reverse continue: " + REFUSAL);
        waitUntilStoppedAt(2, "at the breakpoint, once the DAP server refused to run the program backwards from there");
        assertStopRestored((DAPSuspendContext) session.getSuspendContext());
        assertPausedAt(paused, "since it ran to the breakpoint", 2);
    }

    public void testAStopBeforeARefusedResumeAtABreakpointWhichDoesNotSuspendIsWhereTheSessionIsSuspended() throws Exception {
        assertSuspendedAtTheNewerStop(false);
    }

    public void testAStopBeforeARefusedReverseContinueAtABreakpointWhichDoesNotSuspendIsWhereTheSessionIsSuspended() throws Exception {
        assertSuspendedAtTheNewerStop(true);
    }

    public void testAContinuedEventBeforeARefusedResumeAtABreakpointWhichDoesNotSuspendLeavesTheSessionRunning() throws Exception {
        assertRunningAfterTheContinuedEvent(false);
    }

    public void testAContinuedEventBeforeARefusedReverseContinueAtABreakpointWhichDoesNotSuspendLeavesTheSessionRunning() throws Exception {
        assertRunningAfterTheContinuedEvent(true);
    }

    public void testAResumeRefusedWhileReverseContinueIsPendingKeepsTheStopEndedUntilTheDAPServerRefusesReverseContinueToo() throws Exception {
        var paused = refuseResumeWhileReverseContinueIsPending();
        var context = (DAPSuspendContext) session.getSuspendContext();

        server.answer(true);
        waitForConsoleLine("Cannot reverse continue: " + REFUSAL);
        assertStopRestored(context);
        assertPausedAt(paused, "since Resume Program", 1);
    }

    public void testAResumeRefusedWhileReverseContinueIsPendingLetsTheSessionRunOnceTheDAPServerAcceptsReverseContinue() throws Exception {
        var paused = refuseResumeWhileReverseContinueIsPending();

        // The session, which is suspended again at the stop where Reverse Continue was performed, runs once the DAP
        // server accepts it, and so does the thread
        server.answer(false);
        PlatformTestUtil.waitWithEventsDispatching("the session runs once the DAP server accepted Reverse Continue",
                () -> !session.isSuspended(), BOUND_SECONDS);
        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads tab shows the thread running: " + getRows(),
                () -> getRows().equals(List.of("main [running]")), BOUND_SECONDS);
        assertEquals(DAPThreadRunState.RUNNING, client.getThreadRunStates().getState(MAIN));
        server.stop(StoppedEventArgumentsReason.ENTRY);
        waitUntilStoppedAt(2, "where the program stopped running backwards");
        assertPausedAt(paused, "since Resume Program", 1, 2);
    }

    public void testAReverseContinueRefusedWhileAResumeIsPendingKeepsTheStopEndedUntilTheDAPServerRefusesTheResumeToo() throws Exception {
        debug(false);
        var context = (DAPSuspendContext) session.getSuspendContext();
        var frame = (DAPStackFrame) session.getCurrentStackFrame();
        // The DAP server answers both requests when the test says
        server.held.addAll(Set.of("reverseContinue", "continue"));
        ((DAPDebugProcess) session.getDebugProcess()).startReverseContinue(context);
        assertInstanceOf(request(), ReverseContinueArguments.class);
        var paused = recordPauses();
        // The session stays suspended until the DAP server answers Reverse Continue: Resume Program can be performed
        session.resume();
        assertInstanceOf(request(), ContinueArguments.class);

        // The DAP server refuses Reverse Continue: the program may still run, as Resume Program asked
        server.answer(true);
        waitForConsoleLine("Cannot reverse continue: " + REFUSAL);
        assertTrue("The execution epoch of the stop stays ended while Resume Program is pending",
                client.getExecutionEpoch() != context.getExecutionEpoch());
        assertFalse("The session runs, as IntelliJ resumed it for Resume Program", session.isSuspended());

        // ... and then Resume Program: the program is still where it was
        server.answer(true);
        waitUntilSuspendedAgainAt(context, frame, paused, "once the DAP server refused Resume Program too");
        waitForConsoleLine("Cannot resume the program: " + REFUSAL);
        assertStopRestored(context);
    }

    public void testTwoResumesRefusedAtBreakpointsWhichDoNotSuspendLeaveBothThreadsStopped() throws Exception {
        assertBothThreadsStoppedOnceBothResumesAreRefused(false);
    }

    public void testTwoResumesRefusedAtBreakpointsWhichDoNotSuspendNewestFirstLeaveBothThreadsStopped() throws Exception {
        assertBothThreadsStoppedOnceBothResumesAreRefused(true);
    }

    public void testAResumeRefusedAtABreakpointWhichDoesNotSuspendWhileANewerOneIsAcceptedLeavesBothThreadsRunning() throws Exception {
        resumeAtTheBreakpointInEachThreadAlone();
        var paused = recordPauses();

        server.answer(true);
        waitForConsoleLine("Cannot resume the program: " + REFUSAL);
        server.answer(false);
        waitForAnswersSoFar();
        // The newer request resumed all threads, the one whose resume the DAP server refused too
        assertFalse("The session isn't suspended: the DAP server accepted the newer resume", session.isSuspended());
        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads tab shows both threads running: " + getRows(),
                () -> getRows().equals(List.of("main [running]", "worker [running]")), BOUND_SECONDS);
        assertPausedAt(paused, "since the threads stopped at the breakpoint");
    }

    /**
     * Each thread stops alone at the breakpoint which doesn't suspend, where the client resumes the program, and the
     * DAP server refuses both requests, the older or the newer one first: both threads are still stopped where they
     * were, which the Threads tab shows, and the session is suspended where the worker stopped, the newer stop.
     */
    private void assertBothThreadsStoppedOnceBothResumesAreRefused(boolean newestFirst) throws Exception {
        resumeAtTheBreakpointInEachThreadAlone();
        var paused = recordPauses();

        var refusal = "Cannot resume the program: " + REFUSAL;
        if (newestFirst) {
            server.answerNewest(true);
        } else {
            server.answer(true);
        }
        // The first refusal is handled before the second request is answered, so that they come in the order the test
        // gives, also when the first request's answer was given before the DAP server attached its refusal to it
        waitForConsoleLine(refusal);
        server.answer(true);
        PlatformTestUtil.waitWithEventsDispatching(() -> "the console shows \"" + refusal + "\" twice, not:\n"
                        + getConsoleText(),
                () -> getConsoleText().lines().filter(refusal::equals).count() == 2, BOUND_SECONDS);
        waitUntilStoppedAt(3, "where the worker stopped, once the DAP server refused both resumes");
        var context = (DAPSuspendContext) session.getSuspendContext();
        PlatformTestUtil.waitWithEventsDispatching(() -> "the execution epoch of the stop, "
                        + context.getExecutionEpoch() + ", is the current one again, not " + client.getExecutionEpoch(),
                () -> client.getExecutionEpoch() == context.getExecutionEpoch(), BOUND_SECONDS);
        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads tab shows both threads stopped: " + getRows(),
                () -> getRows().equals(List.of("main [stopped]", "worker [stopped]")), BOUND_SECONDS);
        var states = client.getThreadRunStates();
        assertEquals(DAPThreadRunState.STOPPED, states.getState(MAIN));
        assertEquals(DAPThreadRunState.STOPPED, states.getState(WORKER));
        assertPausedAt(paused, "since the threads stopped at the breakpoint", 3);
    }

    /**
     * Starts debugging with a breakpoint which doesn't suspend, in a program with the threads 'main' and 'worker', and
     * resumes the program, which the DAP server accepts. The main thread then stops alone at the breakpoint, where the
     * client resumes the program, and so does the worker, before the DAP server answers the first resume: it holds the
     * answers of both until the test gives them.
     */
    private void resumeAtTheBreakpointInEachThreadAlone() throws Exception {
        debug(true);
        server.withWorker = true;
        session.resume();
        assertInstanceOf(request(), ContinueArguments.class);
        PlatformTestUtil.waitWithEventsDispatching("the session runs once the DAP server accepted Resume Program",
                () -> !session.isSuspended(), BOUND_SECONDS);
        server.held.add("continue");
        server.stopAlone(MAIN);
        assertEquals("The client resumes the program where the main thread stopped", MAIN,
                (int) ((ContinueArguments) request()).getThreadId());
        server.stopAlone(WORKER);
        assertEquals("The client resumes the program where the worker stopped", WORKER,
                (int) ((ContinueArguments) request()).getThreadId());
    }

    /**
     * Runs the program to the breakpoint which doesn't suspend, forward or backwards, where the DAP server says that the
     * program stopped again before it refuses to resume it from the breakpoint: the session is suspended at that newer
     * stop, and not at the breakpoint.
     */
    private void assertSuspendedAtTheNewerStop(boolean reverse) throws Exception {
        var paused = runToBreakpointWhichDoesNotSuspend(reverse, () -> server.stop(StoppedEventArgumentsReason.PAUSE));

        waitForConsoleLine((reverse ? "Cannot reverse continue: " : "Cannot resume the program: ") + REFUSAL);
        waitUntilStoppedAt(3, "at the stop which the DAP server sent before it refused");
        assertStopRestored((DAPSuspendContext) session.getSuspendContext());
        // A pause at the breakpoint would come from the refusal, which the client handles before the answers which
        // it needs to suspend the session at the newer stop: it would be among the frames, before the newer stop
        assertPausedAt(paused, "since it ran to the breakpoint: only at the newer stop", 3);
    }

    /**
     * Runs the program to the breakpoint which doesn't suspend, forward or backwards, where the DAP server says that the
     * program continued before it refuses to resume it from the breakpoint: the session runs, and is suspended where the
     * program stops next.
     */
    private void assertRunningAfterTheContinuedEvent(boolean reverse) throws Exception {
        var paused = runToBreakpointWhichDoesNotSuspend(reverse, () -> server.continued());

        waitForConsoleLine((reverse ? "Cannot reverse continue: " : "Cannot resume the program: ") + REFUSAL);
        waitForAnswersSoFar();
        assertFalse("The session isn't suspended at the breakpoint: the program continued", session.isSuspended());
        assertPausedAt(paused, "since it ran to the breakpoint");
        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads tab shows the thread running: " + getRows(),
                () -> getRows().equals(List.of("main [running]")), BOUND_SECONDS);

        // The session is suspended where the program stops next
        server.stop(StoppedEventArgumentsReason.PAUSE);
        waitUntilStoppedAt(3, "where the program stopped after it continued");
        assertStopRestored((DAPSuspendContext) session.getSuspendContext());
        assertPausedAt(paused, "since it ran to the breakpoint", 3);
    }

    /**
     * Starts debugging with a breakpoint which doesn't suspend, and runs the program from where it first stopped,
     * forward with Resume Program or backwards with Reverse Continue, which the DAP server accepts, until the program
     * stops at that breakpoint. The client then resumes it from there in the same direction, which the DAP server
     * refuses, once it ran the given newer event, if any.
     *
     * @return the frames where the session paused since the program first stopped.
     */
    private @NotNull List<Integer> runToBreakpointWhichDoesNotSuspend(boolean reverse,
                                                                      @Nullable Runnable newerEvent) throws Exception {
        debug(true);
        var paused = recordPauses();
        if (reverse) {
            ((DAPDebugProcess) session.getDebugProcess()).startReverseContinue(session.getSuspendContext());
            assertInstanceOf(request(), ReverseContinueArguments.class);
        } else {
            session.resume();
            assertInstanceOf(request(), ContinueArguments.class);
        }
        // Reverse Continue resumes the session once the DAP server answered it, before the program stops at the
        // breakpoint
        PlatformTestUtil.waitWithEventsDispatching("the session runs once the DAP server accepted the request",
                () -> !session.isSuspended(), BOUND_SECONDS);
        server.beforeRefusing = newerEvent;
        server.refused.add(reverse ? "reverseContinue" : "continue");
        server.stop(StoppedEventArgumentsReason.BREAKPOINT, new Integer[]{BREAKPOINT_ID});
        // The client resumes the program at the breakpoint, in the direction it ran
        Class<?> resumedAtTheBreakpoint = reverse ? ReverseContinueArguments.class : ContinueArguments.class;
        assertInstanceOf(request(), resumedAtTheBreakpoint);
        return paused;
    }

    /**
     * Starts debugging, and performs Reverse Continue, whose answer the DAP server holds until the test gives it, and
     * then Resume Program, which the DAP server refuses: the session is suspended again at the stop where it was, while
     * the execution epoch of the stop stays ended, since Reverse Continue may still run the program.
     *
     * @return the frames where the session paused since Resume Program.
     */
    private @NotNull List<Integer> refuseResumeWhileReverseContinueIsPending() throws Exception {
        debug(false, "continue");
        var context = (DAPSuspendContext) session.getSuspendContext();
        var frame = (DAPStackFrame) session.getCurrentStackFrame();
        server.held.add("reverseContinue");
        ((DAPDebugProcess) session.getDebugProcess()).startReverseContinue(context);
        assertInstanceOf(request(), ReverseContinueArguments.class);
        var paused = recordPauses();

        // The session stays suspended until the DAP server answers Reverse Continue: Resume Program can be performed
        assertTrue("The session stays suspended while Reverse Continue is pending", session.isSuspended());
        session.resume();
        assertInstanceOf(request(), ContinueArguments.class);
        waitUntilSuspendedAgainAt(context, frame, paused, "once the DAP server refused Resume Program");
        waitForConsoleLine("Cannot resume the program: " + REFUSAL);
        assertTrue("The execution epoch of the stop stays ended while Reverse Continue is pending",
                client.getExecutionEpoch() != context.getExecutionEpoch());
        assertEquals("The Threads tab shows the thread stopped", DAPThreadRunState.STOPPED,
                client.getThreadRunStates().getState(MAIN));
        return paused;
    }

    /**
     * Starts debugging, attached to a new {@link RefusingServer} which refuses the given requests, and waits for the
     * session to be suspended where the program first stopped. With a breakpoint which doesn't suspend, the debug
     * adapter is the {@link RefusingServerDefinition} of the file which has that breakpoint, and the DAP server has it.
     */
    private void debug(boolean withBreakpointWhichDoesNotSuspend, @NotNull String... refused) throws Exception {
        server = new RefusingServer(Set.of(refused));
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("refusing", type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        if (withBreakpointWhichDoesNotSuspend) {
            addBreakpointWhichDoesNotSuspend();
            configuration.setServerId(SERVER_ID);
        }
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
                        .buildAndExecute());
        if (withBreakpointWhichDoesNotSuspend) {
            // The session sends its breakpoints as it starts, which may be once the configuration is done: the program
            // stops at the breakpoint once the DAP server has it. The client handles the answer to 'setBreakpoints'
            // before those of the requests which a stop sends
            PlatformTestUtil.waitWithEventsDispatching("the DAP server is configured, and has the breakpoint which doesn't suspend",
                    () -> server.configured && server.setBreakpoints.stream().anyMatch(args -> args.getBreakpoints().length > 0),
                    BOUND_SECONDS);
        }
        PlatformTestUtil.waitWithEventsDispatching("the program stops, and the session is suspended where it stopped", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && session.isSuspended() && currentFrameId() == 1;
        }, BOUND_SECONDS);
        client = ((DAPSuspendContext) session.getSuspendContext()).getClient();
    }

    /**
     * Adds a breakpoint which doesn't suspend to a file of the {@link RefusingServerDefinition}, whose session sends
     * it to the DAP server.
     */
    private void addBreakpointWhichDoesNotSuspend() throws Exception {
        serverDefinition = new RefusingServerDefinition();
        DebugAdapterManager.getInstance().addDebugAdapterServerWithoutNotification(serverDefinition);
        dir = Files.createTempDirectory("dap-refused-resume-");
        var path = dir.resolve("program.refused");
        Files.writeString(path, "run\nlog\nend\n");
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        var breakpointType = XDebuggerUtil.getInstance().findBreakpointType(DAPBreakpointType.class);
        WriteAction.run(() -> {
            var breakpoint = XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint(breakpointType, file.getUrl(), 1, new DAPBreakpointProperties());
            breakpoint.setSuspendPolicy(SuspendPolicy.NONE);
        });
    }

    /**
     * Returns the frames where the session pauses from now on, in order: the frame where it is suspended when IntelliJ
     * tells its listeners that it paused.
     */
    private @NotNull List<Integer> recordPauses() {
        var paused = new CopyOnWriteArrayList<Integer>();
        session.addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionPaused() {
                paused.add(currentFrameId());
            }
        }, getTestRootDisposable());
        return paused;
    }

    /**
     * Waits until IntelliJ has told the listeners of the session of as many pauses as the given frames, and asserts
     * that the session paused at these frames, in that order, since the given moment. IntelliJ tells them only after
     * the session shows the stop, on the thread which suspended the session, so that a stop which shows may not be
     * counted yet. A pause elsewhere which comes before the expected ones, such as at the breakpoint before a newer
     * stop, is among the frames.
     *
     * @param paused the frames where the session paused, as {@link #recordPauses()} records them.
     * @param since  since when the session paused.
     * @param frames the frames where the session paused, none if it didn't pause.
     */
    private static void assertPausedAt(@NotNull List<Integer> paused, @NotNull String since, Integer... frames) {
        var expected = List.of(frames);
        PlatformTestUtil.waitWithEventsDispatching(() -> "IntelliJ tells the listeners of the session that it paused "
                        + since + " at the frames " + expected + ", not only at " + paused,
                () -> paused.size() >= expected.size(), BOUND_SECONDS);
        assertEquals("The frames where the session paused " + since, expected, List.copyOf(paused));
    }

    /**
     * Waits until the session, which IntelliJ resumed for a request, is suspended again at the given suspend context
     * of the stop where it was, in the given frame of that stop, and IntelliJ has told the listeners of the session
     * that it paused there.
     */
    private void waitUntilSuspendedAgainAt(@NotNull DAPSuspendContext context,
                                           @NotNull XStackFrame frame,
                                           @NotNull List<Integer> paused,
                                           @NotNull String after) {
        PlatformTestUtil.waitWithEventsDispatching(() -> "the session is suspended again " + after
                        + ", at the stop where it was and in the same frame: paused at the frames " + paused + ", at "
                        + session.getSuspendContext() + " in " + session.getCurrentStackFrame(),
                () -> paused.size() == 1 && session.isSuspended() && session.getSuspendContext() == context
                        && session.getCurrentStackFrame() == frame, BOUND_SECONDS);
    }

    /**
     * Waits until the stop of the given suspend context is the current one, as before the refused request, or as the
     * newer stop which the DAP server sent before it refused: its execution epoch is the current one, so that its
     * values can be set, and the Threads tab shows the thread stopped.
     */
    private void assertStopRestored(@NotNull DAPSuspendContext context) {
        var client = context.getClient();
        PlatformTestUtil.waitWithEventsDispatching(() -> "the execution epoch of the stop, " + context.getExecutionEpoch()
                        + ", is the current one again, not " + client.getExecutionEpoch(),
                () -> client.getExecutionEpoch() == context.getExecutionEpoch(), BOUND_SECONDS);
        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads tab shows the thread stopped: " + getRows(),
                () -> getRows().equals(List.of("main [stopped]")), BOUND_SECONDS);
        assertEquals(DAPThreadRunState.STOPPED, client.getThreadRunStates().getState(MAIN));
        assertTrue("The session is suspended", session.isSuspended());
        assertSame("The session is suspended at the stop", context, session.getSuspendContext());
    }

    private int currentFrameId() {
        return session.getCurrentStackFrame() instanceof DAPStackFrame frame ? frame.getFrameId() : -1;
    }

    /**
     * Waits until the session is suspended in the frame of the given stop of the program, counted from 1.
     */
    private void waitUntilStoppedAt(int stop, @NotNull String where) {
        PlatformTestUtil.waitWithEventsDispatching("the session is suspended " + where + ", in the frame " + stop,
                () -> session.isSuspended() && currentFrameId() == stop, BOUND_SECONDS);
    }

    /**
     * Returns the arguments of the next request which resumes the program that the DAP server received.
     */
    private @NotNull Object request() throws InterruptedException {
        PlatformTestUtil.waitWithEventsDispatching("the DAP server receives a request which resumes the program",
                () -> !server.requests.isEmpty(), BOUND_SECONDS);
        return server.requests.take();
    }

    /**
     * Waits until the client has handled the answers and events which the DAP server sent so far: it handles them in
     * order, before the answer to a 'threads' request which it sends now.
     */
    private void waitForAnswersSoFar() {
        var threads = client.getDebugProtocolServer().threads();
        PlatformTestUtil.waitWithEventsDispatching("the DAP server answers 'threads', after the answers which it sent before",
                threads::isDone, BOUND_SECONDS);
    }

    /**
     * Waits until the console of the debug session shows the given line.
     */
    private void waitForConsoleLine(@NotNull String line) {
        PlatformTestUtil.waitWithEventsDispatching(() -> "the console shows \"" + line + "\", not:\n" + getConsoleText(),
                () -> getConsoleText().lines().anyMatch(line::equals), BOUND_SECONDS);
    }

    private @NotNull String getConsoleText() {
        var console = (ConsoleViewImpl) session.getConsoleView();
        if (console == null) {
            return "";
        }
        // The console's editor exists once its component does
        console.getComponent();
        console.flushDeferredText();
        var editor = console.getEditor();
        return editor != null ? editor.getDocument().getText() : "";
    }

    /**
     * Returns the text of each row of the Threads tab, as it shows it: the name of the thread and its run state.
     */
    private @NotNull List<String> getRows() {
        var debugProcess = (DAPDebugProcess) session.getDebugProcess();
        var table = (JTable) debugProcess.getThreadsPanel().getDefaultFocusedComponent();
        return IntStream.range(0, table.getRowCount())
                .mapToObj(row -> table.prepareRenderer(table.getCellRenderer(row, 0), row, 0).toString())
                .toList();
    }

    /**
     * Returns the DAP value of the given name in the locals of the given frame, as the Variables view gets it.
     */
    private static @NotNull DAPValue local(@NotNull XStackFrame frame, @NotNull String name) {
        XValueContainer locals = children(frame).getBottomGroups().get(0);
        var children = children(locals);
        return (DAPValue) IntStream.range(0, children.size())
                .filter(i -> children.getName(i).equals(name))
                .mapToObj(children::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No variable " + name));
    }

    /**
     * Sets the given value with "Set Value…", and returns the error which it reports, or null once the value is set.
     */
    private static @Nullable String setValue(@NotNull DAPValue value, @NotNull String newValue) {
        var modifier = value.getModifier();
        assertNotNull("\"Set Value…\" is offered", modifier);
        var done = new CompletableFuture<@Nullable String>();
        modifier.setValue(XDebuggerUtil.getInstance().createExpression(newValue, null, null, EvaluationMode.EXPRESSION),
                new XValueModifier.XModificationCallback() {
                    @Override
                    public void valueModified() {
                        done.complete(null);
                    }

                    @Override
                    public void errorOccurred(@NotNull String errorMessage) {
                        done.complete(errorMessage);
                    }
                });
        return PlatformTestUtil.waitForFuture(done, BOUND_SECONDS * 1000L);
    }

    private static @NotNull XValueChildrenList children(@NotNull XValueContainer container) {
        var all = new XValueChildrenList();
        var done = new CompletableFuture<XValueChildrenList>();
        container.computeChildren(new XCompositeNode() {
            @Override
            public void addChildren(@NotNull XValueChildrenList children, boolean last) {
                IntStream.range(0, children.size()).forEach(i -> all.add(children.getName(i), children.getValue(i)));
                children.getTopGroups().forEach(all::addTopGroup);
                children.getBottomGroups().forEach(all::addBottomGroup);
                if (last) {
                    done.complete(all);
                }
            }

            @Override
            public void tooManyChildren(int remaining) {
            }

            @Override
            public void setAlreadySorted(boolean alreadySorted) {
            }

            @Override
            public void setErrorMessage(@NotNull String errorMessage) {
                done.completeExceptionally(new AssertionError(errorMessage));
            }

            @Override
            public void setErrorMessage(@NotNull String errorMessage, @Nullable XDebuggerTreeNodeHyperlink link) {
                setErrorMessage(errorMessage);
            }

            @Override
            public void setMessage(@NotNull String message, @Nullable Icon icon, @NotNull SimpleTextAttributes attributes,
                                   @Nullable XDebuggerTreeNodeHyperlink link) {
            }
        });
        return PlatformTestUtil.waitForFuture(done, BOUND_SECONDS * 1000L);
    }

    /**
     * The debug adapter of the files '*.refused', whose sessions send the breakpoints of these files to the DAP server.
     * It has no installer.
     */
    private static class RefusingServerDefinition extends DebugAdapterServerDefinition {

        RefusingServerDefinition() {
            super(SERVER_ID, "Refusing");
        }

        @Override
        protected @NotNull DebugAdapterDescriptorFactory createFactory() {
            return new DebugAdapterDescriptorFactory() {
            };
        }

        @Override
        protected List<ServerMapping> getServerMappings() {
            return List.of(new ServerFileNamePatternMapping(List.of("*.refused"), SERVER_ID, null, (file, project) -> true));
        }
    }

    /**
     * A DAP server of a program with one thread, 'main', or with 'worker' too when the test says, which stops once
     * configured, and then when the test says: all threads, or one alone at the breakpoint. At its n-th stop, counted
     * from 1, the thread is in the frame n, whose locals are 'answer' = 41. It answers the requests which resume the
     * program that the test names with an error, and can set variables. It supports stepping back, and answers the
     * requests which the test names only when the test says.
     */
    private static class RefusingServer implements IDebugProtocolServer {

        // The commands of the requests which resume the program that the DAP server refuses, and of those whose
        // answers it holds until the test gives them
        private final Set<String> refused = ConcurrentHashMap.newKeySet();
        private final Set<String> held = ConcurrentHashMap.newKeySet();
        private final ServerSocket serverSocket;
        private final AtomicInteger stops = new AtomicInteger();
        // The arguments of the requests which resume the program, and of the 'setVariable' requests
        private final LinkedBlockingQueue<Object> requests = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<SetVariableArguments> setVariables = new LinkedBlockingQueue<>();
        // The 'setBreakpoints' requests
        private final LinkedBlockingQueue<SetBreakpointsArguments> setBreakpoints = new LinkedBlockingQueue<>();
        // The answers which the DAP server holds, in the order of the requests: true to refuse the request
        private final LinkedBlockingDeque<CompletableFuture<Boolean>> heldAnswers = new LinkedBlockingDeque<>();
        private volatile IDebugProtocolClient client;
        private volatile boolean configured;
        // Set by the test: the program has the thread 'worker' too
        private volatile boolean withWorker;
        // Set by the test: sends a newer event, such as 'stopped', before the DAP server refuses a request
        private volatile @Nullable Runnable beforeRefusing;

        RefusingServer(@NotNull Set<String> refused) throws IOException {
            this.refused.addAll(refused);
            this.serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            var accept = new java.lang.Thread(() -> {
                try {
                    var socket = serverSocket.accept();
                    var launcher = DSPLauncher.createServerLauncher(this, socket.getInputStream(), socket.getOutputStream());
                    client = launcher.getRemoteProxy();
                    launcher.startListening();
                } catch (IOException e) {
                    // closed by the test
                }
            }, "Refusing DAP server");
            accept.setDaemon(true);
            accept.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        void close() throws IOException {
            serverSocket.close();
        }

        /**
         * Sends that all threads stopped, with the given reason.
         */
        void stop(@NotNull String reason) {
            stop(reason, null);
        }

        /**
         * Sends that all threads stopped, with the given reason, at the given breakpoints.
         */
        void stop(@NotNull String reason, Integer @Nullable [] hitBreakpointIds) {
            stop(reason, hitBreakpointIds, MAIN, true);
        }

        /**
         * Sends that the given thread stopped alone at the breakpoint, while the other threads run.
         */
        void stopAlone(int threadId) {
            stop(StoppedEventArgumentsReason.BREAKPOINT, new Integer[]{BREAKPOINT_ID}, threadId, false);
        }

        private void stop(@NotNull String reason,
                          Integer @Nullable [] hitBreakpointIds,
                          int threadId,
                          boolean allThreadsStopped) {
            stops.incrementAndGet();
            var stopped = new StoppedEventArguments();
            stopped.setReason(reason);
            stopped.setThreadId(threadId);
            stopped.setAllThreadsStopped(allThreadsStopped);
            stopped.setHitBreakpointIds(hitBreakpointIds);
            client.stopped(stopped);
        }

        /**
         * Sends that all threads continued.
         */
        void continued() {
            var continued = new ContinuedEventArguments();
            continued.setThreadId(MAIN);
            continued.setAllThreadsContinued(true);
            client.continued(continued);
        }

        /**
         * Gives the answer to the oldest request whose answer the DAP server holds.
         *
         * @param refuse true to refuse the request, and false to accept it.
         */
        void answer(boolean refuse) {
            var answer = heldAnswers.pollFirst();
            assertNotNull("A request whose answer the DAP server holds", answer);
            answer.complete(refuse);
        }

        /**
         * Gives the answer to the newest request whose answer the DAP server holds.
         *
         * @param refuse true to refuse the request, and false to accept it.
         */
        void answerNewest(boolean refuse) {
            var answer = heldAnswers.pollLast();
            assertNotNull("A request whose answer the DAP server holds", answer);
            answer.complete(refuse);
        }

        /**
         * Returns the answer to a request which resumes the program: an error when the test said to refuse it, once the
         * newer event which the test gives, if any, is sent. The answer of a request which the test said to hold is
         * given once the test says whether to refuse it.
         */
        private <T> @NotNull CompletableFuture<T> resume(@NotNull String command, @NotNull Object args, @Nullable T answer) {
            // Decided before the test sees the request, as the test may then change what the DAP server does
            CompletableFuture<Boolean> refuses;
            if (held.contains(command)) {
                refuses = new CompletableFuture<>();
                heldAnswers.add(refuses);
            } else {
                refuses = CompletableFuture.completedFuture(refused.contains(command));
            }
            var newerEvent = beforeRefusing;
            requests.add(args);
            return refuses.thenCompose(refuse -> {
                if (!refuse) {
                    return CompletableFuture.completedFuture(answer);
                }
                if (newerEvent != null) {
                    // Sent before the answer, which the client then handles after the event
                    newerEvent.run();
                }
                return CompletableFuture.failedFuture(new ResponseErrorException(
                        new ResponseError(ResponseErrorCode.RequestFailed, REFUSAL, null)));
            });
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            capabilities.setSupportsSetVariable(true);
            capabilities.setSupportsStepBack(true);
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> client.initialized());
        }

        @Override
        public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
            setBreakpoints.add(args);
            var breakpoints = Arrays.stream(args.getBreakpoints())
                    .map(sourceBreakpoint -> {
                        var breakpoint = new Breakpoint();
                        breakpoint.setId(BREAKPOINT_ID);
                        breakpoint.setVerified(true);
                        breakpoint.setLine(sourceBreakpoint.getLine());
                        return breakpoint;
                    })
                    .toArray(Breakpoint[]::new);
            var response = new SetBreakpointsResponse();
            response.setBreakpoints(breakpoints);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> {
                configured = true;
                stop(StoppedEventArgumentsReason.PAUSE);
            });
        }

        @Override
        public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
            return resume("continue", args, new ContinueResponse());
        }

        @Override
        public CompletableFuture<Void> next(NextArguments args) {
            return resume("next", args, null);
        }

        @Override
        public CompletableFuture<Void> stepIn(StepInArguments args) {
            return resume("stepIn", args, null);
        }

        @Override
        public CompletableFuture<Void> reverseContinue(ReverseContinueArguments args) {
            return resume("reverseContinue", args, null);
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            var response = new ThreadsResponse();
            response.setThreads(withWorker ?
                    new Thread[]{thread(MAIN, "main"), thread(WORKER, "worker")} :
                    new Thread[]{thread(MAIN, "main")});
            return CompletableFuture.completedFuture(response);
        }

        private static @NotNull Thread thread(int id, @NotNull String name) {
            var thread = new Thread();
            thread.setId(id);
            thread.setName(name);
            return thread;
        }

        @Override
        public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
            var frame = new StackFrame();
            frame.setId(stops.get());
            frame.setName("run");
            frame.setLine(stops.get());
            var response = new StackTraceResponse();
            response.setStackFrames(new StackFrame[]{frame});
            response.setTotalFrames(1);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
            var locals = new Scope();
            locals.setName("Locals");
            locals.setPresentationHint("locals");
            locals.setVariablesReference(LOCALS_REFERENCE);
            var response = new ScopesResponse();
            response.setScopes(new Scope[]{locals});
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
            var response = new VariablesResponse();
            if (args.getVariablesReference() == LOCALS_REFERENCE) {
                var answer = new Variable();
                answer.setName("answer");
                answer.setValue("41");
                answer.setVariablesReference(0);
                response.setVariables(new Variable[]{answer});
            } else {
                response.setVariables(new Variable[0]);
            }
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<SetVariableResponse> setVariable(SetVariableArguments args) {
            setVariables.add(args);
            var response = new SetVariableResponse();
            response.setValue(args.getValue());
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
