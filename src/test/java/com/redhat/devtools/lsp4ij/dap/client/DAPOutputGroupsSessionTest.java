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
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.eclipse.lsp4j.debug.OutputEventArgumentsGroup;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArguments;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArgumentsType;
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
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.eclipse.lsp4j.debug.OutputEventArgumentsGroup.END;
import static org.eclipse.lsp4j.debug.OutputEventArgumentsGroup.START;
import static org.eclipse.lsp4j.debug.OutputEventArgumentsGroup.START_COLLAPSED;

/**
 * The groups of the DAP 'output' event, against a DAP server in the test's process which the session attaches to: the
 * console indents the lines of a group by its nesting level, as VS Code shows the output of {@code console.group()},
 * and each session of a tree started with 'startDebugging' has its own groups.
 */
public class DAPOutputGroupsSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
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

    public void testTheConsoleIndentsTheLinesOfAGroupByItsLevel() throws Exception {
        var root = debug();

        root.output("Outer\n", START);
        root.output("in outer\n", null);
        root.output("Inner\n", START_COLLAPSED);
        // a line in chunks, and several lines in one event
        root.output("in ", null);
        root.output("inner\nand again\n", null);
        root.output("", END);
        root.output("Outer done\n", END);
        root.output("outside\n", null);

        waitForConsoleLine("outside");
        assertConsoleShows("""
                Outer
                  in outer
                  Inner
                    in inner
                    and again
                Outer done
                outside
                """);
    }

    public void testEachSessionOfATreeHasItsOwnGroups() throws Exception {
        var root = debug();
        var child = startChildSession(root);

        root.output("Root group\n", START);
        waitForConsoleLine("Root group");
        child.output("child line\n", null);
        assertEquals("The line of the child session, which the group of the root session doesn't indent",
                "child line", waitForConsoleLineEndingWith("child line"));

        child.output("Child group\n", START);
        waitForConsoleLine("Child group");
        root.output("root line\n", null);
        assertEquals("The line of the root session, which the group of the child session doesn't indent further",
                "  root line", waitForConsoleLineEndingWith("root line"));
        child.output("in child group\n", null);
        assertEquals("The line of the group of the child session",
                "  in child group", waitForConsoleLineEndingWith("in child group"));
    }

    /**
     * Starts debugging, attached to a new {@link SessionServer}, and returns the root session once it is configured.
     */
    private @NotNull Session debug() throws Exception {
        server = new SessionServer();
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("output groups", type.getConfigurationFactories()[0]);
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

    private @NotNull Session nextSession(@NotNull String what) {
        PlatformTestUtil.waitWithEventsDispatching(what, () -> !server.sessions.isEmpty(), BOUND_SECONDS);
        return server.sessions.remove();
    }

    /**
     * Waits until the console of the debug session shows the given line.
     */
    private void waitForConsoleLine(@NotNull String line) {
        PlatformTestUtil.waitWithEventsDispatching(() -> "the console shows \"" + line + "\", not:\n" + getConsoleText(),
                () -> getConsoleText().lines().anyMatch(line::equals), BOUND_SECONDS);
    }

    /**
     * Waits until the console of the debug session shows a line which ends with the given text, and returns it.
     */
    private @NotNull String waitForConsoleLineEndingWith(@NotNull String end) {
        PlatformTestUtil.waitWithEventsDispatching(() -> "the console shows a line ending with \"" + end + "\", not:\n" + getConsoleText(),
                () -> getConsoleText().lines().anyMatch(line -> line.endsWith(end)), BOUND_SECONDS);
        return getConsoleText().lines().filter(line -> line.endsWith(end)).findFirst().orElseThrow();
    }

    /**
     * Asserts that the console of the debug session shows the given lines, one after the other.
     */
    private void assertConsoleShows(@NotNull String expected) {
        var text = getConsoleText();
        assertTrue("the console shows:\n" + expected + "not:\n" + text,
                Collections.indexOfSubList(text.lines().toList(), expected.lines().toList()) >= 0);
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
     * A DAP session of a {@link SessionServer}, over its own connection, of a program with one thread, 'main'.
     */
    private static class Session implements IDebugProtocolServer {

        private static final int MAIN = 1;

        private final Socket socket;
        private final IDebugProtocolClient client;
        // Done once the client has closed the connection
        private final Future<Void> listening;
        private final CompletableFuture<Void> configured = new CompletableFuture<>();

        Session(@NotNull Socket socket, @NotNull ExecutorService executor) throws IOException {
            this.socket = socket;
            var launcher = DSPLauncher.createServerLauncher(this, socket.getInputStream(), socket.getOutputStream(),
                    executor, consumer -> consumer);
            this.client = launcher.getRemoteProxy();
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
         * Sends an 'output' event of the program's standard output, in the given group, if any.
         */
        void output(@NotNull String output, @Nullable OutputEventArgumentsGroup group) {
            var args = new OutputEventArguments();
            args.setCategory(OutputEventArgumentsCategory.STDOUT);
            args.setOutput(output);
            args.setGroup(group);
            client.output(args);
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
            var thread = new Thread();
            thread.setId(MAIN);
            thread.setName("main");
            var response = new ThreadsResponse();
            response.setThreads(new Thread[]{thread});
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
