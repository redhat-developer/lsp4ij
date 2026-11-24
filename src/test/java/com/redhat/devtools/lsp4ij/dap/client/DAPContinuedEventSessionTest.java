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

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The 'continued' event in a debug session, against a DAP server in the test's process which the session attaches to.
 * Its program has two threads, 'main' and 'worker'. The DAP server says that the program continued, as GDB does after
 * a step typed in the Debug console: the session then shows the program running, unless only another thread than the
 * stopped one continued. A stop which the DAP server says has continued before the session showed it doesn't suspend
 * the session.
 */
public class DAPContinuedEventSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final int MAIN = 1;
    private static final int WORKER = 2;

    private ContinuingServer server;
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

    public void testAContinuedEventOfAllThreadsResumesTheSession() throws Exception {
        debug();

        server.continued(MAIN, null);
        waitUntilResumed("after the DAP server says that all threads continued");

        // The next stop gets a suspend context of its own, with each thread once
        server.stop(MAIN);
        PlatformTestUtil.waitWithEventsDispatching("the session shows the next stop of the main thread",
                () -> currentFrameId() == frameId(2, MAIN), BOUND_SECONDS);
        var context = session.getSuspendContext();
        var stacks = Arrays.stream(context.getExecutionStacks()).map(XExecutionStack::getDisplayName).toList();
        assertEquals("The execution stacks of the suspend context, one per thread", List.of("main", "worker"), stacks);
        assertEquals("The threads which the Frames view lists", List.of("main", "worker"), threadsOf(context));
    }

    public void testAContinuedEventOfTheStoppedThreadAloneResumesTheSession() throws Exception {
        debug();

        server.continued(MAIN, false);
        waitUntilResumed("after the DAP server says that the stopped thread continued alone");
    }

    public void testAContinuedEventOfTheStoppedThreadAloneResumesTheSessionWhileTheFramesViewSelectsAnotherThread()
            throws Exception {
        debug();
        var context = (DAPSuspendContext) session.getSuspendContext();
        var worker = Arrays.stream(context.getExecutionStacks())
                .filter(stack -> stack.getDisplayName().equals("worker"))
                .findFirst().orElseThrow();
        // The Frames view selects the worker thread: it loads its frames, and then shows them, which makes its stack
        // the active one
        framesOf(worker);
        framesOf(worker);
        assertEquals("The thread of the active stack, once the Frames view selected the worker thread",
                Integer.valueOf(WORKER), context.getThreadId());

        server.continued(MAIN, false);
        waitUntilResumed("after the DAP server says that the stopped thread continued alone, while the worker thread is selected");
    }

    public void testAContinuedEventOfAnotherThreadAloneLeavesTheSessionSuspended() throws Exception {
        debug();
        int threadsRequests = server.threadsRequests.get();

        server.continued(WORKER, false);
        // The client asks for the threads again once it has handled the event, to refresh the Threads tab
        PlatformTestUtil.waitWithEventsDispatching("the threads are asked for again after the worker thread continued",
                () -> server.threadsRequests.get() > threadsRequests, BOUND_SECONDS);

        assertTrue("The session stays suspended where the main thread stopped", session.isSuspended());
        assertEquals(frameId(1, MAIN), currentFrameId());
    }

    public void testAStopWhichContinuedBeforeTheSessionShowedItDoesNotSuspendTheSession() throws Exception {
        debug();
        server.continued(MAIN, null);
        waitUntilResumed("after the DAP server says that all threads continued");
        // The frames where the session is suspended from now on
        var pausedFrames = new CopyOnWriteArrayList<Integer>();
        session.addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionPaused() {
                pausedFrames.add(currentFrameId());
            }
        }, getTestRootDisposable());

        // 'stopped' and 'continued' back to back: the client gets the threads, and then the stack frames, of the stop
        // only once it has read the 'continued' event
        var threadsAnswer = server.holdThreads();
        server.stop(MAIN);
        server.continued(MAIN, null);
        threadsAnswer.complete(null);
        PlatformTestUtil.waitWithEventsDispatching("the stack frames of the stop which continued are asked for",
                () -> server.stackTraces.contains(frameId(2, MAIN)), BOUND_SECONDS);

        // The next stop is shown; the one which continued never was
        server.stop(WORKER);
        PlatformTestUtil.waitWithEventsDispatching("the session shows the next stop, of the worker thread",
                () -> currentFrameId() == frameId(3, WORKER), BOUND_SECONDS);
        assertFalse("The session was suspended at the stop which continued: " + pausedFrames,
                pausedFrames.contains(frameId(2, MAIN)));

        server.continued(WORKER, null);
        waitUntilResumed("after the DAP server says that all threads continued");
    }

    /**
     * Starts debugging, attached to a new {@link ContinuingServer}, and waits for the main thread to stop.
     */
    private void debug() throws Exception {
        server = new ContinuingServer();
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("continued", type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
                        .buildAndExecute());
        PlatformTestUtil.waitWithEventsDispatching("the main thread stops", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && session.isSuspended() && currentFrameId() == frameId(1, MAIN);
        }, BOUND_SECONDS);
    }

    /**
     * Waits until the session is no longer suspended, and has cleared the frames of its stop.
     */
    private void waitUntilResumed(@NotNull String after) {
        PlatformTestUtil.waitWithEventsDispatching("the session resumes " + after + ", and clears the frames of its stop",
                () -> !session.isSuspended() && session.getCurrentStackFrame() == null, BOUND_SECONDS);
    }

    /**
     * Returns the stack frames of the given execution stack, as the Frames view asks for them when it shows the stack.
     */
    private static @NotNull List<XStackFrame> framesOf(@NotNull XExecutionStack stack) {
        var frames = new ArrayList<XStackFrame>();
        var done = new CompletableFuture<List<XStackFrame>>();
        stack.computeStackFrames(0, new XExecutionStack.XStackFrameContainer() {
            @Override
            public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last) {
                frames.addAll(stackFrames);
                if (last) {
                    done.complete(List.copyOf(frames));
                }
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                done.completeExceptionally(new AssertionError(errorMessage));
            }

            @Override
            public boolean isObsolete() {
                return false;
            }
        });
        return PlatformTestUtil.waitForFuture(done, BOUND_SECONDS * 1000L);
    }

    private int currentFrameId() {
        return session.getCurrentStackFrame() instanceof DAPStackFrame frame ? frame.getFrameId() : -1;
    }

    /**
     * Returns the ID of the frame where the given thread is at the given stop of the program, counted from 1.
     */
    private static int frameId(int stop, int threadId) {
        return stop * 10 + threadId;
    }

    /**
     * Returns the names of the threads of the given suspend context, as the Frames view gets them to list them.
     */
    private static @NotNull List<String> threadsOf(@NotNull XSuspendContext context) {
        var names = new ArrayList<String>();
        var done = new CompletableFuture<List<String>>();
        context.computeExecutionStacks(new XSuspendContext.XExecutionStackContainer() {
            @Override
            public void addExecutionStack(@NotNull List<? extends XExecutionStack> executionStacks, boolean last) {
                executionStacks.forEach(stack -> names.add(stack.getDisplayName()));
                if (last) {
                    done.complete(List.copyOf(names));
                }
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                done.completeExceptionally(new AssertionError(errorMessage));
            }

            @Override
            public boolean isObsolete() {
                return false;
            }
        });
        return PlatformTestUtil.waitForFuture(done, BOUND_SECONDS * 1000L);
    }

    /**
     * A DAP server of a program with the threads 'main' and 'worker'. The test says when a thread stops, and when
     * threads continue. At its n-th stop, counted from 1, a thread is in the frame {@code n * 10 + its id}.
     */
    private static class ContinuingServer implements IDebugProtocolServer {

        private final ServerSocket serverSocket;
        private final AtomicInteger stops = new AtomicInteger();
        // The number of 'threads' requests, and the frames of the stack traces which the server answered
        private final AtomicInteger threadsRequests = new AtomicInteger();
        private final List<Integer> stackTraces = new CopyOnWriteArrayList<>();
        // The server answers 'threads' once it completes
        private volatile CompletableFuture<Void> threadsAnswer = CompletableFuture.completedFuture(null);
        private volatile IDebugProtocolClient client;

        ContinuingServer() throws IOException {
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
            }, "Continuing DAP server");
            accept.setDaemon(true);
            accept.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        void close() throws IOException {
            threadsAnswer.complete(null);
            serverSocket.close();
        }

        /**
         * Sends that the given thread stopped, and only that thread.
         */
        void stop(int threadId) {
            stops.incrementAndGet();
            var stopped = new StoppedEventArguments();
            stopped.setReason(StoppedEventArgumentsReason.PAUSE);
            stopped.setThreadId(threadId);
            stopped.setAllThreadsStopped(false);
            client.stopped(stopped);
        }

        /**
         * Sends that the program continued: all threads, or the given thread alone for {@code allThreadsContinued: false}.
         */
        void continued(int threadId, @Nullable Boolean allThreadsContinued) {
            var continued = new ContinuedEventArguments();
            continued.setThreadId(threadId);
            continued.setAllThreadsContinued(allThreadsContinued);
            client.continued(continued);
        }

        /**
         * Holds the answers to 'threads' until the returned future completes.
         */
        CompletableFuture<Void> holdThreads() {
            var answer = new CompletableFuture<Void>();
            threadsAnswer = answer;
            return answer;
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> client.initialized());
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> stop(MAIN));
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            threadsRequests.incrementAndGet();
            var response = new ThreadsResponse();
            response.setThreads(new Thread[]{thread(MAIN, "main"), thread(WORKER, "worker")});
            return threadsAnswer.thenApply(answered -> response);
        }

        @Override
        public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
            var frame = new StackFrame();
            frame.setId(frameId(stops.get(), args.getThreadId()));
            frame.setName(args.getThreadId() == MAIN ? "run" : "work");
            frame.setLine(5);
            var response = new StackTraceResponse();
            response.setStackFrames(new StackFrame[]{frame});
            response.setTotalFrames(1);
            stackTraces.add(frame.getId());
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

        private static Thread thread(int id, String name) {
            var thread = new Thread();
            thread.setId(id);
            thread.setName(name);
            return thread;
        }
    }
}
