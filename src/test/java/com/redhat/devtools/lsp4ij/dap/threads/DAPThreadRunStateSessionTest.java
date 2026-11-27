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
package com.redhat.devtools.lsp4ij.dap.threads;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.DAPSuspendContext;
import com.redhat.devtools.lsp4ij.dap.client.DAPThreadRunState;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.ContinueResponse;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JTable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The Threads tab and the Frames view show which threads of a debug session run, and which are stopped, against a DAP
 * server in the test's process which the session attaches to. Its program has the threads 'main' and 'worker'; 'main'
 * stops, and the DAP server says that all threads stopped. When 'worker' then continues alone
 * ({@code allThreadsContinued: false}), the Threads tab shows it running and 'main' stopped, the Frames view no longer
 * shows the frames of the worker's stop, and the session stays suspended where 'main' stopped. When the worker was
 * selected, the selection moves to where 'main' stopped, and a step steps 'main'.
 */
public class DAPThreadRunStateSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final int MAIN = 1;
    private static final int WORKER = 2;
    /**
     * The frame where 'main' stopped, the frame of 'worker' at that stop, and the frame of 'main' which called the one
     * where it stopped.
     */
    private static final int MAIN_FRAME = 11;
    private static final int WORKER_FRAME = 12;
    private static final int MAIN_CALLER_FRAME = 13;

    private RunStateServer server;
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

    public void testTheThreadsTabShowsTheThreadsStoppedWhenAllThreadsStopped() throws Exception {
        debug();

        assertEquals(List.of("main [stopped]", "worker [stopped]"), getRows());
        assertEquals(List.of(AllIcons.Debugger.ThreadSuspended, AllIcons.Debugger.ThreadSuspended), getIcons());
    }

    public void testAThreadWhichContinuedAloneRunsAndShowsNoFramesOfItsStop() throws Exception {
        debug();
        var worker = getStack("worker");
        // The Frames view shows the frames of the worker at the stop, once it is selected
        assertEquals(List.of(WORKER_FRAME), frameIdsOf(framesOf(worker)));
        var settingsChanged = new AtomicInteger();
        session.addSessionListener(new XDebugSessionListener() {
            @Override
            public void settingsChanged() {
                settingsChanged.incrementAndGet();
            }

            /**
             * Leaves out the views which the frontend of IntelliJ builds again, as it does once it has created the
             * session tab: IntelliJ 2026.3 tells the listeners of that with this method, which calls
             * {@link #settingsChanged()} by default, at any time after the session started, as after the stop. It
             * overrides nothing in IntelliJ 2025.1 and 2025.3, which build the views of a new tab again at once, as
             * the session starts.
             */
            @SuppressWarnings("unused")
            public void settingsChangedFromFrontend() {
            }
        }, getTestRootDisposable());

        // The DAP server holds its answer to 'threads', which the client asks for once the worker continued: the
        // Threads tab shows the worker running without it
        var threadsAnswer = server.holdThreads();
        server.continued(WORKER, false);
        server.waitUntilHandled();

        assertEquals(List.of("main [stopped]", "worker [running]"), getRows());
        assertEquals(List.of(AllIcons.Debugger.ThreadSuspended, AllIcons.Debugger.ThreadRunning), getIcons());
        var workerFrames = framesOf(worker);
        assertEquals("The frames of the worker, which runs", List.of(), workerFrames.frames());
        assertEquals("Frames not available: the thread is running", workerFrames.error());
        // The views of the stop are built again: the Frames view asks again for the frames of the stack which it shows,
        // and forgets those which it kept of the others
        PlatformTestUtil.waitWithEventsDispatching("the views of the stop are built again",
                () -> settingsChanged.get() > 0, BOUND_SECONDS);
        assertEquals(List.of(MAIN_FRAME, MAIN_CALLER_FRAME), frameIdsOf(framesOf(getStack("main"))));
        assertTrue("The session stays suspended where 'main' stopped", session.isSuspended());
        assertEquals(MAIN_FRAME, currentFrameId());

        // Once the DAP server answers 'threads', the Threads tab still shows the worker running
        threadsAnswer.complete(null);
        server.waitUntilHandled();
        assertEquals(List.of("main [stopped]", "worker [running]"), getRows());
    }

    public void testAThreadWhichExitedAtAStopShowsNoFramesOfItsStopOnceTheThreadsNoLongerListIt() throws Exception {
        debug();
        var worker = getStack("worker");
        assertEquals(List.of(WORKER_FRAME), frameIdsOf(framesOf(worker)));

        server.threadExited(WORKER);
        server.waitUntilHandled();
        // The threads are asked for again, as when another thread continues alone, and the answer no longer lists the
        // worker
        var client = ((DAPSuspendContext) session.getSuspendContext()).getClient();
        client.getThreads(true);
        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads tab doesn't list the worker any more: " + getRows(),
                () -> getRows().equals(List.of("main [stopped]")), BOUND_SECONDS);

        // The stack of the worker, which the stop still holds, still says that it exited
        var workerFrames = framesOf(worker);
        assertEquals("The frames of the worker, which exited", List.of(), workerFrames.frames());
        assertEquals("Frames not available: the thread has exited", workerFrames.error());
        assertEquals(List.of(MAIN_FRAME, MAIN_CALLER_FRAME), frameIdsOf(framesOf(getStack("main"))));
        assertTrue("The session stays suspended where 'main' stopped", session.isSuspended());
    }

    public void testTheSelectionMovesToWhereTheSessionStaysSuspendedWhenTheSelectedThreadContinuesAlone() throws Exception {
        debug();
        var worker = getStack("worker");
        var workerFrame = select(worker, 0);
        assertEquals("Step and Resume act on the worker, which is selected", Integer.valueOf(WORKER), getSuspendContext().getThreadId());

        server.continued(WORKER, false);

        // The current frame is no longer the frame of the worker's stop, but the top frame of 'main', where the session
        // stays suspended
        PlatformTestUtil.waitWithEventsDispatching(
                () -> "the current frame moves to the top frame of 'main', but it is the frame " + currentFrameId(),
                () -> session.getCurrentStackFrame() != workerFrame && currentFrameId() == MAIN_FRAME, BOUND_SECONDS);
        var main = getStack("main");
        assertSame("The current frame", main.getTopFrame(), session.getCurrentStackFrame());
        assertTrue("The session stays suspended where 'main' stopped", session.isSuspended());
        assertEquals("Step and Resume act on 'main'", Integer.valueOf(MAIN), getSuspendContext().getThreadId());

        session.stepOver(false);
        assertEquals("The thread which steps", MAIN, nextThreadId());
    }

    public void testTheSelectionStaysWhenAThreadWhichIsNotSelectedContinuesAlone() throws Exception {
        debug();
        // The frame of 'main' which called the one where it stopped, which isn't the frame where the session is suspended
        var callerFrame = select(getStack("main"), 1);

        server.continued(WORKER, false);
        server.waitUntilHandled();

        assertSame("The current frame", callerFrame, session.getCurrentStackFrame());
        assertTrue("The session stays suspended where 'main' stopped", session.isSuspended());
        session.stepOver(false);
        assertEquals("The thread which steps", MAIN, nextThreadId());
    }

    public void testTheThreadsTabShowsTheThreadsRunningOnceTheSessionResumesAndAThreadWhichExited() throws Exception {
        debug();

        // Resume sends 'continue', which resumes all threads; the DAP server sends no 'continued' event for it
        session.resume();
        var resumed = server.continues.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'continue'", resumed);
        server.waitUntilHandled();
        assertEquals(List.of("main [running]", "worker [running]"), getRows());
        assertEquals(List.of(AllIcons.Debugger.ThreadRunning, AllIcons.Debugger.ThreadRunning), getIcons());

        server.threadExited(WORKER);
        server.waitUntilHandled();
        assertEquals(List.of("main [running]", "worker [exited]"), getRows());
        // The icon of an exited thread, which the panel chooses by the run state. Its look isn't checked: headless, as
        // on Linux CI, IconLoader gives the icon of a running thread itself as its disabled icon
        assertSame("The icon of the worker, which exited", ThreadsPanel.getRunStateIcon(DAPThreadRunState.EXITED),
                getIcons().get(1));
    }

    /**
     * Starts debugging, attached to a new {@link RunStateServer}, and waits for the session to be suspended where
     * 'main' stopped, and for the Threads tab to show the threads.
     */
    private void debug() throws Exception {
        server = new RunStateServer();
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("run states", type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
                        .buildAndExecute());
        PlatformTestUtil.waitWithEventsDispatching("the main thread stops, and the Threads tab shows the threads", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && session.isSuspended() && currentFrameId() == MAIN_FRAME
                    && getTable().getRowCount() == 2;
        }, BOUND_SECONDS);
        server.waitUntilHandled();
    }

    private @NotNull DAPSuspendContext getSuspendContext() {
        return assertInstanceOf(session.getSuspendContext(), DAPSuspendContext.class);
    }

    /**
     * Selects the frame of the given execution stack at the given index, as the Frames view does when the user selects
     * it: the view shows the frames of the stack, which makes it the active stack of the suspend context, whose thread
     * Step and Resume act on, once they are loaded, and the session gets the frame as its current frame.
     *
     * @return the selected frame.
     */
    private @NotNull XStackFrame select(@NotNull XExecutionStack stack, int index) {
        // The first time, the frames are loaded; shown again, as when the views are rebuilt, the stack becomes the
        // active one
        framesOf(stack);
        var frame = framesOf(stack).frames().get(index);
        session.setCurrentStackFrame(stack, frame, index == 0);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertSame("The current frame", frame, session.getCurrentStackFrame());
        return frame;
    }

    /**
     * Returns the thread of the 'next' request which the DAP server receives.
     */
    private int nextThreadId() {
        PlatformTestUtil.waitWithEventsDispatching("the DAP server receives 'next'", () -> !server.nexts.isEmpty(),
                BOUND_SECONDS);
        return server.nexts.remove().getThreadId();
    }

    private int currentFrameId() {
        return session.getCurrentStackFrame() instanceof DAPStackFrame frame ? frame.getFrameId() : -1;
    }

    private @NotNull JTable getTable() {
        var debugProcess = (DAPDebugProcess) session.getDebugProcess();
        return (JTable) debugProcess.getThreadsPanel().getDefaultFocusedComponent();
    }

    /**
     * Returns the text of each row of the Threads tab, as it shows it: the name of the thread and its run state.
     */
    private @NotNull List<String> getRows() {
        var table = getTable();
        return IntStream.range(0, table.getRowCount())
                .mapToObj(row -> table.prepareRenderer(table.getCellRenderer(row, 0), row, 0).toString())
                .toList();
    }

    /**
     * Returns the icon of each row of the Threads tab.
     */
    private @NotNull List<Icon> getIcons() {
        var table = getTable();
        return IntStream.range(0, table.getRowCount())
                .mapToObj(row -> ((SimpleColoredComponent) table.prepareRenderer(table.getCellRenderer(row, 0), row, 0)).getIcon())
                .toList();
    }

    /**
     * Returns the execution stack of the thread with the given name, in the suspend context of the session, as the
     * Frames view lists the threads.
     */
    private @NotNull XExecutionStack getStack(@NotNull String thread) {
        var context = session.getSuspendContext();
        assertNotNull("The session is suspended", context);
        return Arrays.stream(context.getExecutionStacks())
                .filter(stack -> stack.getDisplayName().equals(thread))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No execution stack of the thread " + thread));
    }

    /**
     * What an execution stack gives the Frames view: its frames, or an error message which the view shows instead.
     */
    private record Frames(@NotNull List<XStackFrame> frames, @Nullable String error) {
    }

    /**
     * Returns the stack frames of the given execution stack, or the error which it gives instead, as the Frames view
     * asks for them when it shows the stack.
     */
    private static @NotNull Frames framesOf(@NotNull XExecutionStack stack) {
        var frames = new ArrayList<XStackFrame>();
        var done = new CompletableFuture<Frames>();
        stack.computeStackFrames(0, new XExecutionStack.XStackFrameContainer() {
            @Override
            public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last) {
                frames.addAll(stackFrames);
                if (last) {
                    done.complete(new Frames(List.copyOf(frames), null));
                }
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                done.complete(new Frames(List.copyOf(frames), errorMessage));
            }

            @Override
            public boolean isObsolete() {
                return false;
            }
        });
        return PlatformTestUtil.waitForFuture(done, BOUND_SECONDS * 1000L);
    }

    private static @NotNull List<Integer> frameIdsOf(@NotNull Frames frames) {
        assertNull("The error of the execution stack", frames.error());
        return frames.frames().stream().map(frame -> ((DAPStackFrame) frame).getFrameId()).toList();
    }

    /**
     * A DAP server of a program with the threads 'main' and 'worker'. Once configured, 'main' stops in a frame which
     * another one called, and the DAP server says that all threads stopped. The test says when threads continue, and
     * when a thread exits.
     */
    private static class RunStateServer implements IDebugProtocolServer {

        private final ServerSocket serverSocket;
        // The threads which exited, which the answers to 'threads' no longer list
        private final Set<Integer> exitedThreads = ConcurrentHashMap.newKeySet();
        // The 'continue' requests which the server received
        private final LinkedBlockingQueue<ContinueArguments> continues = new LinkedBlockingQueue<>();
        // The 'next' requests which the server received
        private final LinkedBlockingQueue<NextArguments> nexts = new LinkedBlockingQueue<>();
        // The server answers 'threads' once it completes
        private volatile CompletableFuture<Void> threadsAnswer = CompletableFuture.completedFuture(null);
        // The client, and the endpoint which sends it requests, once it connected
        private volatile IDebugProtocolClient client;
        private volatile RemoteEndpoint endpoint;

        RunStateServer() throws IOException {
            this.serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            var accept = new java.lang.Thread(() -> {
                try {
                    var socket = serverSocket.accept();
                    var launcher = DSPLauncher.createServerLauncher(this, socket.getInputStream(), socket.getOutputStream());
                    client = launcher.getRemoteProxy();
                    endpoint = launcher.getRemoteEndpoint();
                    launcher.startListening();
                } catch (IOException e) {
                    // closed by the test
                }
            }, "Run state DAP server");
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
         * Sends that the program continued: all threads, or the given thread alone for {@code allThreadsContinued: false}.
         */
        void continued(int threadId, @Nullable Boolean allThreadsContinued) {
            var continued = new ContinuedEventArguments();
            continued.setThreadId(threadId);
            continued.setAllThreadsContinued(allThreadsContinued);
            client.continued(continued);
        }

        /**
         * Sends that the given thread exited.
         */
        void threadExited(int threadId) {
            exitedThreads.add(threadId);
            var exited = new ThreadEventArguments();
            exited.setReason(ThreadEventArgumentsReason.EXITED);
            exited.setThreadId(threadId);
            client.thread(exited);
        }

        /**
         * Holds the answers to 'threads' until the returned future completes.
         */
        CompletableFuture<Void> holdThreads() {
            var answer = new CompletableFuture<Void>();
            threadsAnswer = answer;
            return answer;
        }

        /**
         * Waits until the client has handled the messages which the server sent, and the EDT what they asked of it: the
         * client handles the messages of a connection one after the other, so once it has answered a request which the
         * server sends after them, it has handled them. The client answers a request which it doesn't know with an error.
         */
        void waitUntilHandled() {
            var answer = endpoint.request("lsp4ij/test/ping", null);
            PlatformTestUtil.waitWithEventsDispatching("the client answers a request sent after the messages of the server",
                    answer::isDone, BOUND_SECONDS);
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(client::initialized);
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> {
                var stopped = new StoppedEventArguments();
                stopped.setReason(StoppedEventArgumentsReason.BREAKPOINT);
                stopped.setThreadId(MAIN);
                stopped.setAllThreadsStopped(true);
                client.stopped(stopped);
            });
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            var response = new ThreadsResponse();
            // the threads which haven't exited
            response.setThreads(Stream.of(thread(MAIN, "main"), thread(WORKER, "worker"))
                    .filter(thread -> !exitedThreads.contains(thread.getId()))
                    .toArray(Thread[]::new));
            return threadsAnswer.thenApply(answered -> response);
        }

        @Override
        public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
            var frames = args.getThreadId() == MAIN ?
                    new StackFrame[]{frame(MAIN_FRAME, "run", 5), frame(MAIN_CALLER_FRAME, "main", 9)} :
                    new StackFrame[]{frame(WORKER_FRAME, "work", 5)};
            var response = new StackTraceResponse();
            response.setStackFrames(frames);
            response.setTotalFrames(frames.length);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
            var response = new ScopesResponse();
            response.setScopes(new Scope[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
            continues.add(args);
            return CompletableFuture.completedFuture(new ContinueResponse());
        }

        @Override
        public CompletableFuture<Void> next(NextArguments args) {
            nexts.add(args);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }

        private static StackFrame frame(int id, String name, int line) {
            var frame = new StackFrame();
            frame.setId(id);
            frame.setName(name);
            frame.setLine(line);
            return frame;
        }

        private static Thread thread(int id, String name) {
            var thread = new Thread();
            thread.setId(id);
            thread.setName(name);
            return thread;
        }
    }
}
