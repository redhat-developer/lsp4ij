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
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.DAPSuspendContext;
import com.redhat.devtools.lsp4ij.dap.client.DAPThreadRunState;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.CapabilitiesEventArguments;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.ReverseContinueArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StepBackArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Step Back and Reverse Continue in a debug session, against a DAP server in the test's process which the session
 * attaches to, and which announces that it supports stepping back once the program runs, as CodeLLDB and Delve do. The
 * actions follow Step Out in the toolbar of the session, and are shown only when the DAP server supports them. They
 * send 'stepBack' and 'reverseContinue' for the stopped thread; the session runs once the DAP server has answered, and
 * the 'stopped' event which follows suspends it where the program stopped. A request which the DAP server refuses
 * leaves the session suspended where it was.
 */
public class DAPReverseDebuggingSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final int MAIN = 3;
    private static final String NO_REVERSE_CONTINUE = "Reverse Continue is not supported. Only \"Step back\" is supported.";

    private ReversingServer server;
    private XDebugSession session;

    @Override
    protected void tearDown() throws Exception {
        try {
            if (session != null && !session.isStopped()) {
                session.stop();
            }
            if (server != null) {
                server.close();
            }
        } finally {
            super.tearDown();
        }
    }

    public void testStepBackAndReverseContinueRunTheProgramBackwardsWhenTheDAPServerSupportsIt() throws Exception {
        debug(true, false);
        var stepBack = action(DAPStepBackAction.ID, "Step Back");
        var reverseContinue = action(DAPReverseContinueAction.ID, "Reverse Continue");

        // IntelliJ asks the debug process for the actions of the session's toolbar, which has Step Out
        var stepOut = ActionManager.getInstance().getAction("StepOut");
        assertNotNull("IntelliJ's Step Out action", stepOut);
        var topToolbar = new DefaultActionGroup(stepOut);
        session.getDebugProcess().registerAdditionalActions(new DefaultActionGroup(), topToolbar, new DefaultActionGroup());
        assertEquals("The actions of the toolbar", List.of(stepOut, stepBack, reverseContinue),
                Arrays.asList(topToolbar.getChildActionsOrStubs()));
        assertTrue("Step Back is shown and enabled while the program is stopped",
                update(stepBack).getPresentation().isEnabledAndVisible());
        assertTrue("Reverse Continue is shown and enabled while the program is stopped",
                update(reverseContinue).getPresentation().isEnabledAndVisible());

        var client = ((DAPSuspendContext) session.getSuspendContext()).getClient();
        stepBack.actionPerformed(update(stepBack));
        var stepBackArgs = (StepBackArguments) request();
        assertEquals("The thread which steps back", MAIN, stepBackArgs.getThreadId());
        assertNull(stepBackArgs.getGranularity());
        assertNull(stepBackArgs.getSingleThread());
        waitUntilResumed("once the DAP server answered Step Back");
        // The Threads tab shows the thread running once the DAP server accepted Step Back, as for a step
        PlatformTestUtil.waitWithEventsDispatching("the thread shows running once the DAP server accepted Step Back",
                () -> client.getThreadRunStates().getState(MAIN) == DAPThreadRunState.RUNNING, BOUND_SECONDS);
        var running = update(stepBack).getPresentation();
        assertTrue("Step Back is shown while the program runs", running.isVisible());
        assertFalse("Step Back is disabled while the program runs", running.isEnabled());
        server.stop(StoppedEventArgumentsReason.STEP);
        waitUntilStoppedAt(2, "where the thread stepped back to");

        reverseContinue.actionPerformed(update(reverseContinue));
        var reverseContinueArgs = (ReverseContinueArguments) request();
        assertEquals("The thread of the stop which the program runs backwards from", MAIN, reverseContinueArgs.getThreadId());
        assertNull(reverseContinueArgs.getSingleThread());
        waitUntilResumed("once the DAP server answered Reverse Continue");
        // The program ran back to the start of its recording
        server.stop(StoppedEventArgumentsReason.ENTRY);
        waitUntilStoppedAt(3, "where the program stopped running backwards");
    }

    public void testStepBackAndReverseContinueResumeOnlyTheCurrentThreadOnceStepOnlyTheCurrentThreadIsSelected() throws Exception {
        debug(true, false);
        ((DAPDebugProcess) session.getDebugProcess()).setStepOnlyCurrentThread(true);

        var stepBack = action(DAPStepBackAction.ID, "Step Back");
        stepBack.actionPerformed(update(stepBack));
        assertEquals(Boolean.TRUE, ((StepBackArguments) request()).getSingleThread());
        waitUntilResumed("once the DAP server answered Step Back");
        server.stop(StoppedEventArgumentsReason.STEP);
        waitUntilStoppedAt(2, "where the thread stepped back to");

        var reverseContinue = action(DAPReverseContinueAction.ID, "Reverse Continue");
        reverseContinue.actionPerformed(update(reverseContinue));
        assertEquals(Boolean.TRUE, ((ReverseContinueArguments) request()).getSingleThread());
        waitUntilResumed("once the DAP server answered Reverse Continue");
    }

    public void testARefusedReverseContinueLeavesTheSessionSuspendedWhereItWas() throws Exception {
        debug(true, true);
        var resumed = new AtomicInteger();
        session.addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionResumed() {
                resumed.incrementAndGet();
            }
        }, getTestRootDisposable());
        var context = (DAPSuspendContext) session.getSuspendContext();
        var client = context.getClient();

        var reverseContinue = action(DAPReverseContinueAction.ID, "Reverse Continue");
        reverseContinue.actionPerformed(update(reverseContinue));
        assertInstanceOf(request(), ReverseContinueArguments.class);
        // The execution epoch of the stop ended before the request was sent, and is the current one again once the
        // refusal is handled: the values of the stop can still be set
        PlatformTestUtil.waitWithEventsDispatching("the execution epoch of the stop is the current one again, once the DAP server refused Reverse Continue",
                () -> client.getExecutionEpoch() == context.getExecutionEpoch(), BOUND_SECONDS);
        assertTrue("The session stays suspended", session.isSuspended());
        assertSame("The session stays at the stop where it was", context, session.getSuspendContext());
        assertEquals(1, currentFrameId());
        assertEquals("The times the session resumed", 0, resumed.get());
        assertEquals("The Threads tab still shows the thread stopped", DAPThreadRunState.STOPPED,
                client.getThreadRunStates().getState(MAIN));

        // Step Back still runs from there
        var stepBack = action(DAPStepBackAction.ID, "Step Back");
        stepBack.actionPerformed(update(stepBack));
        assertInstanceOf(request(), StepBackArguments.class);
        waitUntilResumed("once the DAP server answered Step Back");
        server.stop(StoppedEventArgumentsReason.STEP);
        waitUntilStoppedAt(2, "where the thread stepped back to");
    }

    public void testStepBackAndReverseContinueAreHiddenWhenTheDAPServerDoesNotSupportStepBack() throws Exception {
        debug(false, false);
        var stepBack = action(DAPStepBackAction.ID, "Step Back");
        var reverseContinue = action(DAPReverseContinueAction.ID, "Reverse Continue");
        assertFalse("Step Back is hidden", update(stepBack).getPresentation().isVisible());
        assertFalse("Reverse Continue is hidden", update(reverseContinue).getPresentation().isVisible());

        // Performed anyway, they send nothing: the next request which the DAP server gets is the step which follows
        stepBack.actionPerformed(update(stepBack));
        reverseContinue.actionPerformed(update(reverseContinue));
        assertTrue("The session stays suspended", session.isSuspended());
        session.stepOver(false);
        assertInstanceOf(request(), NextArguments.class);
    }

    /**
     * Starts debugging, attached to a new {@link ReversingServer}, and waits for the program to stop.
     */
    private void debug(boolean supportsStepBack, boolean refusesReverseContinue) throws Exception {
        server = new ReversingServer(supportsStepBack, refusesReverseContinue);
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("reversing", type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
                        .buildAndExecute());
        PlatformTestUtil.waitWithEventsDispatching("the program stops", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && session.isSuspended() && currentFrameId() == 1;
        }, BOUND_SECONDS);
    }

    private int currentFrameId() {
        return session.getCurrentStackFrame() instanceof DAPStackFrame frame ? frame.getFrameId() : -1;
    }

    /**
     * Waits until the session is no longer suspended, and has cleared the frames of its stop, as after a step.
     */
    private void waitUntilResumed(@NotNull String after) {
        PlatformTestUtil.waitWithEventsDispatching("the session resumes " + after + ", and clears the frames of its stop",
                () -> !session.isSuspended() && session.getCurrentStackFrame() == null, BOUND_SECONDS);
    }

    /**
     * Waits until the session is suspended in the frame of the given stop of the program, counted from 1.
     */
    private void waitUntilStoppedAt(int stop, @NotNull String where) {
        PlatformTestUtil.waitWithEventsDispatching("the session is suspended " + where + ", in the frame " + stop,
                () -> session.isSuspended() && currentFrameId() == stop, BOUND_SECONDS);
    }

    /**
     * Returns the arguments of the next request which the DAP server received, other than those which describe the
     * program.
     */
    private @NotNull Object request() throws InterruptedException {
        PlatformTestUtil.waitWithEventsDispatching("the DAP server receives a request which resumes the program",
                () -> !server.requests.isEmpty(), BOUND_SECONDS);
        return server.requests.take();
    }

    private static @NotNull AnAction action(@NotNull String id, @NotNull String text) {
        var action = ActionManager.getInstance().getAction(id);
        assertInstanceOf(action, DAPReverseExecutionAction.class);
        assertEquals(text, action.getTemplatePresentation().getText());
        return action;
    }

    private @NotNull AnActionEvent update(@NotNull AnAction action) {
        var event = TestActionEvent.createTestEvent(action, SimpleDataContext.getSimpleContext(XDebugSession.DATA_KEY, session));
        action.update(event);
        return event;
    }

    /**
     * A DAP server of a program with one thread, which the test stops. At its n-th stop, counted from 1, the thread is
     * in the frame n. It supports single thread execution requests, and, when asked to, announces that it supports
     * stepping back once the configuration is done, as CodeLLDB and Delve announce it once the program runs on rr.
     */
    private static class ReversingServer implements IDebugProtocolServer {

        private final boolean supportsStepBack;
        private final boolean refusesReverseContinue;
        private final ServerSocket serverSocket;
        private final AtomicInteger stops = new AtomicInteger();
        // The arguments of the requests which resume the program
        private final LinkedBlockingQueue<Object> requests = new LinkedBlockingQueue<>();
        private volatile IDebugProtocolClient client;

        ReversingServer(boolean supportsStepBack, boolean refusesReverseContinue) throws IOException {
            this.supportsStepBack = supportsStepBack;
            this.refusesReverseContinue = refusesReverseContinue;
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
            }, "Reversing DAP server");
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
         * Sends that the thread stopped, with the given reason.
         */
        void stop(@NotNull String reason) {
            stops.incrementAndGet();
            var stopped = new StoppedEventArguments();
            stopped.setReason(reason);
            stopped.setThreadId(MAIN);
            stopped.setAllThreadsStopped(true);
            client.stopped(stopped);
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            capabilities.setSupportsSingleThreadExecutionRequests(true);
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> client.initialized());
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> {
                if (supportsStepBack) {
                    var capabilities = new Capabilities();
                    capabilities.setSupportsStepBack(true);
                    var event = new CapabilitiesEventArguments();
                    event.setCapabilities(capabilities);
                    client.capabilities(event);
                }
                stop(StoppedEventArgumentsReason.PAUSE);
            });
        }

        @Override
        public CompletableFuture<Void> stepBack(StepBackArguments args) {
            requests.add(args);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> reverseContinue(ReverseContinueArguments args) {
            requests.add(args);
            if (refusesReverseContinue) {
                // As rdbg answers
                return CompletableFuture.failedFuture(new ResponseErrorException(
                        new ResponseError(ResponseErrorCode.RequestFailed, NO_REVERSE_CONTINUE, null)));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> next(NextArguments args) {
            requests.add(args);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            var thread = new Thread();
            thread.setId(MAIN);
            thread.setName("main");
            var response = new ThreadsResponse();
            response.setThreads(new Thread[]{thread});
            return CompletableFuture.completedFuture(response);
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
            var response = new ScopesResponse();
            response.setScopes(new Scope[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
