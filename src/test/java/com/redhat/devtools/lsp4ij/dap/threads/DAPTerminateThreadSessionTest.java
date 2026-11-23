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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.PopupHandler;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArguments;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArgumentsType;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.TerminateThreadsArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * "Terminate Thread" in the context menu of the "Threads" panel, against a DAP server in the test's process which the
 * session attaches to: it is shown when the DAP server supports 'terminateThreads', and sends it with the selected
 * threads, to the DAP session they are threads of, without the EDT waiting for the answer.
 */
public class DAPTerminateThreadSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    /**
     * How long a test waits for a request which must not come.
     */
    private static final int NO_REQUEST_SECONDS = 2;

    private SessionServer server;
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

    public void testTheThreadsPanelHasAContextMenuWithTerminateThread() throws Exception {
        debug(true);

        var table = getTable();
        assertTrue("The table of the Threads panel has a context menu",
                Arrays.stream(table.getMouseListeners()).anyMatch(PopupHandler.class::isInstance));
        var actionManager = ActionManager.getInstance();
        var group = (DefaultActionGroup) actionManager.getAction(ThreadsPanel.POPUP_GROUP_ID);
        assertEquals(List.of(DAPTerminateThreadAction.ID),
                Arrays.stream(group.getChildActionsOrStubs()).map(actionManager::getId).toList());
        assertEquals("Terminate Thread", terminateThread().getTemplatePresentation().getText());
    }

    public void testTerminateThreadSendsTerminateThreadsWithTheSelectedThreads() throws Exception {
        var root = debug(true);
        var table = getTable();
        table.setRowSelectionInterval(1, 1);

        var event = update();
        assertTrue(event.getPresentation().isEnabledAndVisible());
        // The DAP server answers once the test tells it to: the EDT doesn't wait for the answer
        terminateThread().actionPerformed(event);

        var args = root.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'terminateThreads'", args);
        assertEquals(List.of(Session.WORKER), Arrays.stream(args.getThreadIds()).boxed().toList());
        assertFalse("The DAP server hasn't answered yet", root.terminateAnswer.isDone());
        root.answerTerminateThreads();
    }

    public void testTerminateThreadSendsEachSelectedThread() throws Exception {
        var root = debug(true);
        getTable().setRowSelectionInterval(0, 1);

        terminateThread().actionPerformed(update());

        var args = root.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'terminateThreads'", args);
        assertEquals(List.of(Session.MAIN, Session.WORKER), Arrays.stream(args.getThreadIds()).boxed().toList());
        root.answerTerminateThreads();
    }

    public void testTerminateThreadIsDisabledWithoutASelectedThread() throws Exception {
        debug(true);

        var event = update();
        assertTrue(event.getPresentation().isVisible());
        assertFalse(event.getPresentation().isEnabled());
    }

    public void testTerminateThreadIsHiddenWhenTheDAPServerDoesNotSupportTerminateThreads() throws Exception {
        var root = debug(false);
        getTable().setRowSelectionInterval(1, 1);

        var event = update();
        assertFalse(event.getPresentation().isVisible());
        assertFalse(event.getPresentation().isEnabled());

        // Even when it is performed, as from a shortcut, the DAP server gets no request it doesn't support
        terminateThread().actionPerformed(event);
        assertNull(root.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
    }

    public void testTerminateThreadTerminatesTheThreadsOfTheSessionWhichTheyBelongTo() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);

        // The child session numbers its threads itself, and the panel shows them once it stops
        child.stop(Session.WORKER);
        PlatformTestUtil.waitWithEventsDispatching("the Threads panel shows the threads of the child session",
                () -> getTable().getRowCount() == Session.CHILD_THREADS.size(), BOUND_SECONDS);
        getTable().setRowSelectionInterval(1, 1);
        terminateThread().actionPerformed(update());

        var args = child.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server of the child session receives 'terminateThreads'", args);
        assertEquals(List.of(Session.WORKER), Arrays.stream(args.getThreadIds()).boxed().toList());
        assertNull("The root session gets no request", root.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
        child.answerTerminateThreads();
    }

    public void testTheRowsOfAChildSessionAreItsOwnThreads() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);

        // Thread 1 is 'main' in the root session, and 'page' in the child session
        showThreadsOf(child);
        assertEquals(List.of("page", "worker", "service worker"), getRows());
        getTable().setRowSelectionInterval(0, 0);
        terminateThread().actionPerformed(update());

        var args = child.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server of the child session receives 'terminateThreads'", args);
        assertEquals(List.of(Session.MAIN), Arrays.stream(args.getThreadIds()).boxed().toList());
        assertNull("The root session gets no request", root.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
        child.answerTerminateThreads();
    }

    public void testTheRowsOfTheRootSessionAreItsOwnThreadsAgainAfterThoseOfAChildSession() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);
        showThreadsOf(child);
        child.sendThreadEvent(ThreadEventArgumentsReason.EXITED, Session.MAIN);
        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads panel shows that 'page' exited, not " + getRows(),
                () -> getRows().get(0).equals("page [exited]"), BOUND_SECONDS);

        // The status of the child session's thread 1 is not that of the root session's thread 1
        showThreadsOf(root);
        assertEquals(List.of("main", "worker"), getRows());
        getTable().setRowSelectionInterval(0, 0);
        terminateThread().actionPerformed(update());

        var args = root.terminateThreads.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server of the root session receives 'terminateThreads'", args);
        assertEquals(List.of(Session.MAIN), Arrays.stream(args.getThreadIds()).boxed().toList());
        assertNull("The child session gets no request", child.terminateThreads.poll(NO_REQUEST_SECONDS, TimeUnit.SECONDS));
        root.answerTerminateThreads();
    }

    public void testAThreadEventOfAnotherSessionDoesNotChangeTheRows() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);

        // The panel shows the threads of the root session, and the child session's thread 1 exits
        child.sendThreadEvent(ThreadEventArgumentsReason.EXITED, Session.MAIN);
        waitUntilHandled(child);

        assertEquals(List.of("main", "worker"), getRows());
    }

    public void testAThreadWhichThePanelDoesNotShowReloadsTheThreadsOfItsSession() throws Exception {
        var root = debug(true);
        var child = startChildSession(root);
        showThreadsOf(child);

        child.startThread("web worker");

        PlatformTestUtil.waitWithEventsDispatching(() -> "the Threads panel shows the new thread of the child session, not " + getRows(),
                () -> getRows().equals(List.of("page", "worker", "service worker", "web worker")), BOUND_SECONDS);
    }

    /**
     * Starts debugging, attached to a new {@link SessionServer}, and returns the root session once the Threads panel
     * shows its threads.
     */
    private @NotNull Session debug(boolean supportsTerminateThreads) throws Exception {
        server = new SessionServer(supportsTerminateThreads);
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("terminate threads", type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
                        .buildAndExecute());
        var root = nextSession("the debug session connects");
        PlatformTestUtil.waitWithEventsDispatching("the Threads panel shows the threads of the root session", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && root.configured.isDone() && getTable().getRowCount() == Session.ROOT_THREADS.size();
        }, BOUND_SECONDS);
        return root;
    }

    /**
     * Asks the client of the given session to start a child session, and returns it once it is configured.
     */
    private @NotNull Session startChildSession(@NotNull Session parent) {
        parent.startChildSession();
        var child = nextSession("the child session connects");
        PlatformTestUtil.waitWithEventsDispatching("the child session is configured", child.configured::isDone, BOUND_SECONDS);
        return child;
    }

    private @NotNull Session nextSession(@NotNull String what) {
        PlatformTestUtil.waitWithEventsDispatching(what, () -> !server.sessions.isEmpty(), BOUND_SECONDS);
        return server.sessions.remove();
    }

    private @NotNull JTable getTable() {
        var debugProcess = (DAPDebugProcess) session.getDebugProcess();
        return (JTable) debugProcess.getThreadsPanel().getDefaultFocusedComponent();
    }

    /**
     * Makes the given session stop, and waits for the Threads panel to show its threads, as it does at each stop.
     */
    private void showThreadsOf(@NotNull Session stopped) {
        stopped.stop(Session.MAIN);
        PlatformTestUtil.waitWithEventsDispatching("the Threads panel shows the threads of the session which stopped",
                () -> getTable().getRowCount() == stopped.threads.size(), BOUND_SECONDS);
    }

    /**
     * Returns the text of each row of the Threads panel, as it shows it: the name of the thread and its status.
     */
    private @NotNull List<String> getRows() {
        var table = getTable();
        return IntStream.range(0, table.getRowCount())
                .mapToObj(row -> table.prepareRenderer(table.getCellRenderer(row, 0), row, 0).toString())
                .toList();
    }

    /**
     * Waits until the client has handled the messages which the given session sent, and the EDT what they asked of
     * it: the client handles the messages of a connection one after the other, so once it has answered a request which
     * the session sends after them, it has handled them.
     */
    private static void waitUntilHandled(@NotNull Session sender) {
        var answer = sender.ping();
        PlatformTestUtil.waitWithEventsDispatching("the client handles the messages of the session", answer::isDone, BOUND_SECONDS);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private static @NotNull AnAction terminateThread() {
        var action = ActionManager.getInstance().getAction(DAPTerminateThreadAction.ID);
        assertInstanceOf(action, DAPTerminateThreadAction.class);
        return action;
    }

    /**
     * Updates "Terminate Thread" as the context menu of the Threads panel's table does, with the data of the panel,
     * which the IDE gets from it as a UiDataProvider above the table. The headless DataManager of the tests gives no
     * data of a component.
     */
    private @NotNull AnActionEvent update() {
        var panel = ((DAPDebugProcess) session.getDebugProcess()).getThreadsPanel();
        assertTrue(SwingUtilities.isDescendingFrom(getTable(), panel));
        var action = terminateThread();
        var event = TestActionEvent.createTestEvent(action, CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT, panel::uiDataSnapshot));
        action.update(event);
        return event;
    }

    /**
     * A DAP server which accepts a connection per DAP session, as a debug adapter that starts child sessions with
     * 'startDebugging' does.
     */
    private static class SessionServer {

        private final ServerSocket serverSocket;
        // Runs the loop which accepts the connections and the message loop of each session, so that close() can stop
        // them all: the test framework reports the threads which a test leaves running
        private final ExecutorService executor = Executors.newCachedThreadPool();
        // The sessions which no test has taken yet, and all of them
        private final LinkedBlockingQueue<Session> sessions = new LinkedBlockingQueue<>();
        private final List<Session> all = new CopyOnWriteArrayList<>();
        private final Future<?> accepting;

        SessionServer(boolean supportsTerminateThreads) throws IOException {
            this.serverSocket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            this.accepting = executor.submit(() -> {
                try {
                    while (!serverSocket.isClosed()) {
                        // The first session is the root one, the next ones its children
                        var threads = all.isEmpty() ? Session.ROOT_THREADS : Session.CHILD_THREADS;
                        var session = new Session(serverSocket.accept(), executor, supportsTerminateThreads, threads);
                        all.add(session);
                        sessions.add(session);
                    }
                } catch (IOException e) {
                    // closed by the test
                }
            });
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        /**
         * Waits until the client has closed the connection of each session, as it does once the debug session has
         * stopped, then closes the server and its connections, and stops its threads.
         */
        void close() throws Exception {
            try {
                for (var session : all) {
                    // A request which a test left unanswered doesn't keep the client from disconnecting
                    session.answerTerminateThreads();
                }
                PlatformTestUtil.waitWithEventsDispatching("the client closes the connection of each session",
                        () -> all.stream().allMatch(session -> session.listening.isDone()), BOUND_SECONDS);
            } finally {
                serverSocket.close();
                // A connection accepted just before is registered by the loop: let the loop end before closing them
                accepting.get(BOUND_SECONDS, TimeUnit.SECONDS);
                for (var session : all) {
                    session.socket.close();
                }
                executor.shutdownNow();
                assertTrue("the threads of the DAP server stop", executor.awaitTermination(BOUND_SECONDS, TimeUnit.SECONDS));
            }
        }
    }

    /**
     * A DAP session of a {@link SessionServer}, over its own connection. It answers 'terminateThreads' when the test
     * tells it to, and then reports that the threads exited.
     */
    private static class Session implements IDebugProtocolServer {

        private static final int MAIN = 1;
        private static final int WORKER = 2;
        private static final List<String> ROOT_THREADS = List.of("main", "worker");
        private static final List<String> CHILD_THREADS = List.of("page", "worker", "service worker");

        private final Socket socket;
        private final IDebugProtocolClient client;
        private final boolean supportsTerminateThreads;
        private final List<String> threads;
        private final RemoteEndpoint remoteEndpoint;
        // Done once the client has closed the connection
        private final Future<Void> listening;
        private final CompletableFuture<Void> configured = new CompletableFuture<>();
        private final LinkedBlockingQueue<TerminateThreadsArguments> terminateThreads = new LinkedBlockingQueue<>();
        private final CompletableFuture<Void> terminateAnswer = new CompletableFuture<>();

        Session(@NotNull Socket socket,
                @NotNull ExecutorService executor,
                boolean supportsTerminateThreads,
                @NotNull List<String> threads) throws IOException {
            this.socket = socket;
            this.supportsTerminateThreads = supportsTerminateThreads;
            this.threads = new CopyOnWriteArrayList<>(threads);
            var launcher = DSPLauncher.createServerLauncher(this, socket.getInputStream(), socket.getOutputStream(),
                    executor, consumer -> consumer);
            this.client = launcher.getRemoteProxy();
            this.remoteEndpoint = launcher.getRemoteEndpoint();
            this.listening = launcher.startListening();
        }

        /**
         * Asks the client to start a child session, which connects to the server on its own connection.
         */
        void startChildSession() {
            var args = new StartDebuggingRequestArguments();
            args.setConfiguration(Map.of());
            args.setRequest(StartDebuggingRequestArgumentsType.ATTACH);
            client.startDebugging(args);
        }

        /**
         * Sends that the given thread stopped at a breakpoint.
         */
        void stop(int threadId) {
            var stopped = new StoppedEventArguments();
            stopped.setReason(StoppedEventArgumentsReason.BREAKPOINT);
            stopped.setThreadId(threadId);
            client.stopped(stopped);
        }

        /**
         * Sends a 'thread' event with the given reason for the given thread.
         */
        void sendThreadEvent(@NotNull String reason, int threadId) {
            var event = new ThreadEventArguments();
            event.setReason(reason);
            event.setThreadId(threadId);
            client.thread(event);
        }

        /**
         * Starts a thread with the given name, which 'threads' reports from then on, and sends that it started.
         */
        void startThread(@NotNull String name) {
            threads.add(name);
            sendThreadEvent(ThreadEventArgumentsReason.STARTED, threads.size());
        }

        /**
         * Sends the client a request which it doesn't know, and returns the future of its answer, an error: the client
         * answers it once it has handled the messages which this session sent before.
         */
        CompletableFuture<Object> ping() {
            return remoteEndpoint.request("lsp4ij/test/ping", null);
        }

        /**
         * Answers the 'terminateThreads' requests, the one received and those to come.
         */
        void answerTerminateThreads() {
            terminateAnswer.complete(null);
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            if (supportsTerminateThreads) {
                capabilities.setSupportsTerminateThreadsRequest(true);
            }
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(client::initialized);
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> configured.complete(null));
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            var response = new ThreadsResponse();
            response.setThreads(IntStream.range(0, threads.size())
                    .mapToObj(index -> thread(index + 1, threads.get(index)))
                    .toArray(Thread[]::new));
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> terminateThreads(TerminateThreadsArguments args) {
            terminateThreads.add(args);
            return terminateAnswer.thenRun(() -> {
                // As a DAP server does once the threads ended
                for (int threadId : args.getThreadIds()) {
                    var exited = new ThreadEventArguments();
                    exited.setReason(ThreadEventArgumentsReason.EXITED);
                    exited.setThreadId(threadId);
                    client.thread(exited);
                }
            });
        }

        @Override
        public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
            var frame = new StackFrame();
            frame.setId(10 + args.getThreadId());
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

        private static Thread thread(int id, String name) {
            var thread = new Thread();
            thread.setId(id);
            thread.setName(name);
            return thread;
        }
    }
}
