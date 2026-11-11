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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StepOutArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * "Step Only the Current Thread" in a debug session, against a DAP server in the test's process which the session
 * attaches to: it is in the session's settings, shown when the DAP server supports single thread execution requests,
 * and makes Step Over, Step Into and Step Out send 'singleThread'.
 */
public class DAPStepOnlyCurrentThreadSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;

    private SteppingServer server;
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

    public void testAStepResumesOnlyItsThreadOnceStepOnlyTheCurrentThreadIsSelected() throws Exception {
        debug(true);
        var action = stepOnlyCurrentThread();

        // IntelliJ asks the debug process for the actions of the session's settings
        var settings = new DefaultActionGroup();
        session.getDebugProcess().registerAdditionalActions(new DefaultActionGroup(), new DefaultActionGroup(), settings);
        assertTrue(Arrays.asList(settings.getChildActionsOrStubs()).contains(action));
        var event = update(action);
        assertTrue(event.getPresentation().isEnabledAndVisible());
        assertFalse(action.isSelected(event));

        // Until it is selected, a step resumes every thread
        session.stepOver(false);
        assertNull(((NextArguments) step()).getSingleThread());

        action.setSelected(event, true);
        assertTrue(action.isSelected(event));
        session.stepOver(false);
        assertEquals(Boolean.TRUE, ((NextArguments) step()).getSingleThread());
        session.stepInto();
        assertEquals(Boolean.TRUE, ((StepInArguments) step()).getSingleThread());
        session.stepOut();
        assertEquals(Boolean.TRUE, ((StepOutArguments) step()).getSingleThread());

        action.setSelected(event, false);
        session.stepOver(false);
        assertNull(((NextArguments) step()).getSingleThread());
    }

    public void testStepOnlyTheCurrentThreadIsHiddenWhenTheDAPServerDoesNotSupportSingleThreadExecutionRequests() throws Exception {
        debug(false);
        var action = stepOnlyCurrentThread();
        var event = update(action);
        assertFalse(event.getPresentation().isEnabledAndVisible());

        // Selected anyway, a step still resumes every thread, as the DAP server can't resume one
        action.setSelected(event, true);
        session.stepOver(false);
        assertNull(((NextArguments) step()).getSingleThread());
    }

    /**
     * Starts debugging, attached to a new {@link SteppingServer}, and waits for the program to stop.
     */
    private void debug(boolean supportsSingleThreadExecutionRequests) throws Exception {
        server = new SteppingServer(supportsSingleThreadExecutionRequests);
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("stepping", type.getConfigurationFactories()[0]);
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
            return isStopped();
        }, BOUND_SECONDS);
    }

    private boolean isStopped() {
        return session != null && session.isSuspended() && session.getCurrentStackFrame() instanceof DAPStackFrame;
    }

    /**
     * Returns the arguments of the step that the DAP server received, once the program it stepped has stopped again.
     */
    private @NotNull Object step() throws InterruptedException {
        PlatformTestUtil.waitWithEventsDispatching("the step is sent and the program stops again",
                () -> !server.steps.isEmpty() && isStopped(), BOUND_SECONDS);
        return server.steps.take();
    }

    private static @NotNull ToggleAction stepOnlyCurrentThread() {
        var action = ActionManager.getInstance().getAction(DAPStepOnlyCurrentThreadAction.ID);
        assertInstanceOf(action, DAPStepOnlyCurrentThreadAction.class);
        assertEquals("Step Only the Current Thread", action.getTemplatePresentation().getText());
        return (ToggleAction) action;
    }

    private @NotNull AnActionEvent update(@NotNull ToggleAction action) {
        var event = TestActionEvent.createTestEvent(action, SimpleDataContext.getSimpleContext(XDebugSession.DATA_KEY, session));
        action.update(event);
        return event;
    }

    /**
     * A DAP server of a program with one thread, stopped in one frame, which stops again after each step.
     */
    private static class SteppingServer implements IDebugProtocolServer {

        private final boolean supportsSingleThreadExecutionRequests;
        private final ServerSocket serverSocket;
        private final LinkedBlockingQueue<Object> steps = new LinkedBlockingQueue<>();
        private volatile IDebugProtocolClient client;

        SteppingServer(boolean supportsSingleThreadExecutionRequests) throws IOException {
            this.supportsSingleThreadExecutionRequests = supportsSingleThreadExecutionRequests;
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
            }, "Stepping DAP server");
            accept.setDaemon(true);
            accept.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        void close() throws IOException {
            serverSocket.close();
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            capabilities.setSupportsSingleThreadExecutionRequests(supportsSingleThreadExecutionRequests);
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> client.initialized());
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> stop(StoppedEventArgumentsReason.PAUSE));
        }

        @Override
        public CompletableFuture<Void> next(NextArguments args) {
            return stepped(args);
        }

        @Override
        public CompletableFuture<Void> stepIn(StepInArguments args) {
            return stepped(args);
        }

        @Override
        public CompletableFuture<Void> stepOut(StepOutArguments args) {
            return stepped(args);
        }

        private CompletableFuture<Void> stepped(Object args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> {
                stop(StoppedEventArgumentsReason.STEP);
                steps.add(args);
            });
        }

        private void stop(String reason) {
            var stopped = new StoppedEventArguments();
            stopped.setReason(reason);
            stopped.setThreadId(1);
            stopped.setAllThreadsStopped(true);
            client.stopped(stopped);
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            var thread = new Thread();
            thread.setId(1);
            thread.setName("main");
            var response = new ThreadsResponse();
            response.setThreads(new Thread[]{thread});
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
            var frame = new StackFrame();
            frame.setId(7);
            frame.setName("run");
            frame.setLine(5);
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
