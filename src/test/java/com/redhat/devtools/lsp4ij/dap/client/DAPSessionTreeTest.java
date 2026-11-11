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
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArguments;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArgumentsType;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.jetbrains.annotations.NotNull;

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

/**
 * The sessions of a tree, which a debug adapter starts with 'startDebugging' as vscode-js-debug does for the iframes
 * and the workers of a page, keep the debug session running while one of them is alive, and the last one to terminate
 * stops it.
 */
public class DAPSessionTreeTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for something to happen, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;

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

    public void testTheDebugSessionKeepsRunningWhenAChildSessionTerminates() throws Exception {
        var root = debug();
        var child = startChildSession(root);

        // As when an iframe or a worker of the page goes away
        child.terminated();
        assertTheDebugSessionKeepsRunning(child);

        root.terminated();
        assertTheDebugSessionStops(root);
    }

    public void testTheDebugSessionKeepsRunningWhileAChildSessionIsAlive() throws Exception {
        var root = debug();
        var child = startChildSession(root);

        root.terminated();
        assertTheDebugSessionKeepsRunning(root);

        child.terminated();
        assertTheDebugSessionStops(child);
    }

    public void testALiveSessionBelowATerminatedChildKeepsTheDebugSessionRunning() throws Exception {
        var root = debug();
        var child = startChildSession(root);
        var grandchild = startChildSession(child);

        child.terminated();
        root.terminated();
        assertTheDebugSessionKeepsRunning(child, root);

        grandchild.terminated();
        assertTheDebugSessionStops(grandchild);
    }

    /**
     * Starts debugging, attached to a new {@link SessionServer}, and returns the root session once it is configured.
     */
    private @NotNull Session debug() throws Exception {
        server = new SessionServer();
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("session tree", type.getConfigurationFactories()[0]);
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
        PlatformTestUtil.waitWithEventsDispatching("the debug session starts and its root session is configured", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && root.configured.isDone();
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

    /**
     * Returns the next session that connects to the server, dispatching events meanwhile: the debug process may need
     * them to connect.
     */
    private @NotNull Session nextSession(@NotNull String what) {
        PlatformTestUtil.waitWithEventsDispatching(what, () -> !server.sessions.isEmpty(), BOUND_SECONDS);
        return server.sessions.remove();
    }

    /**
     * Waits until the client has handled the messages which the given sessions sent, and asserts that nothing has asked
     * the debug process to stop.
     */
    private void assertTheDebugSessionKeepsRunning(@NotNull Session... senders) {
        waitUntilHandled(senders);
        assertFalse("the debug session keeps running", getDebugProcess().isStopping());
    }

    /**
     * Waits until the client has handled the messages which the given sessions sent, and asserts that they have asked
     * the debug process to stop.
     */
    private void assertTheDebugSessionStops(@NotNull Session... senders) {
        waitUntilHandled(senders);
        assertTrue("the debug session stops", getDebugProcess().isStopping());
    }

    /**
     * Waits until the client has handled the messages which each of the given sessions has sent: it handles the
     * messages of a connection one after the other, so once it has answered a request which a session sends after
     * them, it has handled them.
     */
    private static void waitUntilHandled(@NotNull Session... senders) {
        var answers = Arrays.stream(senders).map(Session::ping).toList();
        PlatformTestUtil.waitWithEventsDispatching("the client handles the messages of the sessions",
                () -> answers.stream().allMatch(CompletableFuture::isDone), BOUND_SECONDS);
    }

    private @NotNull DAPDebugProcess getDebugProcess() {
        return (DAPDebugProcess) session.getDebugProcess();
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

        SessionServer() throws IOException {
            this.serverSocket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            this.accepting = executor.submit(() -> {
                try {
                    while (!serverSocket.isClosed()) {
                        var session = new Session(serverSocket.accept(), executor);
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
     * A DAP session of a {@link SessionServer}, over its own connection.
     */
    private static class Session implements IDebugProtocolServer {

        private final Socket socket;
        private final IDebugProtocolClient client;
        private final RemoteEndpoint remoteEndpoint;
        // Done once the client has closed the connection
        private final Future<Void> listening;
        private final CompletableFuture<Void> configured = new CompletableFuture<>();

        Session(@NotNull Socket socket, @NotNull ExecutorService executor) throws IOException {
            this.socket = socket;
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

        void terminated() {
            client.terminated(new TerminatedEventArguments());
        }

        /**
         * Sends the client a request which it doesn't know, and returns the future of its answer, an error: the client
         * answers it once it has handled the messages which this session sent before.
         */
        CompletableFuture<Object> ping() {
            return remoteEndpoint.request("lsp4ij/test/ping", null);
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
            return CompletableFuture.completedFuture(null).thenRun(() -> configured.complete(null));
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            var response = new ThreadsResponse();
            response.setThreads(new org.eclipse.lsp4j.debug.Thread[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
