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

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPDataBreakpointProperties;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPDataBreakpointType;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPFunctionBreakpointProperties;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPFunctionBreakpointType;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPFileRegistry;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile;
import com.redhat.devtools.lsp4ij.dap.disassembly.breakpoints.DisassemblyBreakpointProperties;
import com.redhat.devtools.lsp4ij.dap.disassembly.breakpoints.DisassemblyBreakpointType;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings.FilterItemSettings;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.CapabilitiesEventArguments;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DataBreakpoint;
import org.eclipse.lsp4j.debug.DataBreakpointAccessType;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.ExceptionBreakpointsFilter;
import org.eclipse.lsp4j.debug.FunctionBreakpoint;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetDataBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetDataBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetInstructionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetInstructionBreakpointsResponse;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArguments;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArgumentsType;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A DAP 'capabilities' event which arrives once the configuration is done sends again, to the DAP server which sent it
 * only, what the configuration sent with the capabilities it changes: the function or the data breakpoints when it
 * turns their support on, the exception breakpoint filters when they or their options change, and the instruction
 * breakpoints once the DAP server supports disassembling. An event which arrives during the configuration changes what
 * the configuration sends next, and what it sent before is sent again once it is done, never twice.
 */
public class DAPClientCapabilitiesEventTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for something to happen, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    /**
     * The name of the run configuration, which is the id of its debug adapter: the user's choices of exception
     * breakpoint filters are kept under it, and the data breakpoints which can persist are sent to it.
     */
    private static final String CONFIGURATION_NAME = "capabilities-events";

    private AdapterServer server;
    private XDebugSession session;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The settings are the application's: no choice of an earlier test is kept
        UserDefinedDebugAdapterServerSettings.getInstance().setFilterSettings(CONFIGURATION_NAME, new FilterItemSettings());
        server = new AdapterServer();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (session != null && !session.isStopped()) {
                session.stop();
            }
            if (server != null) {
                server.close();
            }
            UserDefinedDebugAdapterServerSettings.getInstance().setFilterSettings(CONFIGURATION_NAME, new FilterItemSettings());
        } finally {
            super.tearDown();
        }
    }

    public void testAnEventWhichEnablesFunctionBreakpointsSendsThem() throws Exception {
        addFunctionBreakpoint("main");
        var root = debug(new Capabilities());
        assertRequests(root, List.of(), "setFunctionBreakpoints");

        // as when the debug adapter has connected to its target
        root.capabilities(functionBreakpoints());

        assertRequests(root, List.of("setFunctionBreakpoints [main]"), "setFunctionBreakpoints");
    }

    public void testAnEventWhichEnablesDataBreakpointsSendsThem() throws Exception {
        addDataBreakpoint("7:count");
        var root = debug(new Capabilities());
        assertRequests(root, List.of(), "setDataBreakpoints");

        var update = new Capabilities();
        update.setSupportsDataBreakpoints(true);
        root.capabilities(update);

        assertRequests(root, List.of("setDataBreakpoints [7:count]"), "setDataBreakpoints");
    }

    public void testOnlyTheDebugAdapterWhichSentTheEventGetsTheBreakpointsAgain() throws Exception {
        addFunctionBreakpoint("main");
        var root = debug(functionBreakpoints());
        // a child session, as vscode-js-debug starts for a worker, whose debug adapter doesn't support them yet
        var child = startChildSession(root, new Capabilities());
        assertRequests(root, List.of("setFunctionBreakpoints [main]"), "setFunctionBreakpoints");
        assertRequests(child, List.of(), "setFunctionBreakpoints");

        child.capabilities(functionBreakpoints());

        assertRequests(child, List.of("setFunctionBreakpoints [main]"), "setFunctionBreakpoints");
        // the debug adapter of the root session has them already, with its own capabilities
        assertRequests(root, List.of("setFunctionBreakpoints [main]"), "setFunctionBreakpoints");
    }

    public void testAnEventBeforeTheConfigurationSendsTheBreakpointsDoesntSendThemTwice() throws Exception {
        addFunctionBreakpoint("main");
        var root = connect(DefaultDebugExecutor.getDebugExecutorInstance(), new Capabilities());

        // the debug adapter enables them before it starts the configuration with 'initialized'
        root.capabilities(functionBreakpoints());
        configure(root);

        // the configuration sends them with the capabilities of the event, which is not applied again once it is done
        assertRequests(root, List.of("setFunctionBreakpoints [main]", "configurationDone"), "setFunctionBreakpoints", "configurationDone");
    }

    public void testAnEventAfterTheConfigurationSentTheBreakpointsSendsThemOnceItIsDone() throws Exception {
        addFunctionBreakpoint("main");
        server.onConfigurationDone = session -> session.capabilities(functionBreakpoints());

        // the debug adapter enables them while it is asked to end the configuration, after they were sent without
        var root = debug(new Capabilities());

        assertRequests(root, List.of("configurationDone", "setFunctionBreakpoints [main]"), "setFunctionBreakpoints", "configurationDone");
    }

    public void testAnEventWhichChangesTheExceptionFiltersShowsThemWithTheUsersChoicesAndSendsThem() throws Exception {
        // an earlier session kept the user's choices: 'uncaught' enabled, and 'caught' disabled
        chooseExceptionFilters(Map.of(), filter("uncaught", true), filter("caught", false));
        var root = debug(exceptionFilters(filter("uncaught", false), filter("caught", true)));
        assertRequests(root, List.of("setExceptionBreakpoints [uncaught]"), "setExceptionBreakpoints");
        var panel = getDebugProcess().getBreakpointHandler().getExceptionBreakpointsPanel();
        var changedOnTheEDT = new CopyOnWriteArrayList<Boolean>();
        ((JTable) panel.getDefaultFocusedComponent()).getModel()
                .addTableModelListener(e -> changedOnTheEDT.add(ApplicationManager.getApplication().isDispatchThread()));

        // 'caught' is gone, and 'raised' is new
        root.capabilities(exceptionFilters(filter("uncaught", false), filter("raised", true)));

        assertRequests(root, List.of("setExceptionBreakpoints [uncaught]", "setExceptionBreakpoints [raised, uncaught]"), "setExceptionBreakpoints");
        // the panel shows the filters of the event: the user's choice for 'uncaught', the default of 'raised'
        assertEquals(Set.of("uncaught", "raised"), panel.getApplicableFilters().stream()
                .map(ExceptionBreakpointsFilter::getFilter)
                .collect(Collectors.toSet()));
        assertFalse("the panel changed", changedOnTheEDT.isEmpty());
        assertEquals("the panel changed on the EDT only", List.of(true), changedOnTheEDT.stream().distinct().toList());
    }

    public void testAnEventWhichEnablesExceptionFilterOptionsSendsTheConditions() throws Exception {
        chooseExceptionFilters(Map.of("uncaught", "IllegalArgument"), conditionalFilter("uncaught", true));
        var root = debug(exceptionFilters(conditionalFilter("uncaught", false)));
        // the debug adapter can't take the condition: the filter is sent by its id
        assertRequests(root, List.of("setExceptionBreakpoints [uncaught]"), "setExceptionBreakpoints");

        var update = new Capabilities();
        update.setSupportsExceptionFilterOptions(true);
        root.capabilities(update);

        assertRequests(root, List.of("setExceptionBreakpoints [uncaught]", "setExceptionBreakpoints [] {uncaught=IllegalArgument}"),
                "setExceptionBreakpoints");
    }

    public void testAnEventWhichEnablesDisassemblingSendsTheInstructionBreakpointsOnce() throws Exception {
        var breakpoint = addInstructionBreakpoint();
        var root = debug(new Capabilities());
        assertRequests(root, List.of(), "setInstructionBreakpoints");

        root.capabilities(disassembling(true));
        // the disassembly has no instruction at the line of the breakpoint yet: the request has none
        assertRequests(root, List.of("setInstructionBreakpoints"), "setInstructionBreakpoints");

        // as when the debug adapter loses its target and finds it again
        root.capabilities(disassembling(false));
        root.capabilities(disassembling(true));
        assertRequests(root, List.of("setInstructionBreakpoints"), "setInstructionBreakpoints");

        // the handler sends the instruction breakpoints to the DAP server once for each change
        WriteAction.run(() -> XDebuggerUtil.getInstance().removeBreakpoint(getProject(), breakpoint));
        PlatformTestUtil.waitWithEventsDispatching("the instruction breakpoints are sent", () -> root.requests("setInstructionBreakpoints").size() > 1, BOUND_SECONDS);
        assertRequests(root, List.of("setInstructionBreakpoints", "setInstructionBreakpoints"), "setInstructionBreakpoints");
    }

    public void testAnEventWhichChangesNoneOfTheirCapabilitiesSendsNothingAgain() throws Exception {
        addFunctionBreakpoint("main");
        var capabilities = exceptionFilters(filter("uncaught", false));
        capabilities.setSupportsFunctionBreakpoints(true);
        var root = debug(capabilities);
        var sent = List.of("setFunctionBreakpoints [main]", "setExceptionBreakpoints []");
        assertRequests(root, sent, "setFunctionBreakpoints", "setExceptionBreakpoints");

        // the same filters again, where the user's choice replaces the default, and the same function breakpoints,
        // with a capability which changes nothing that was sent
        var update = exceptionFilters(filter("uncaught", true));
        update.setSupportsFunctionBreakpoints(true);
        update.setSupportsSetVariable(true);
        root.capabilities(update);

        assertRequests(root, sent, "setFunctionBreakpoints", "setExceptionBreakpoints");
    }

    public void testAnEventSendsNoBreakpointsToAProgramWhichRunsWithoutDebugging() throws Exception {
        addFunctionBreakpoint("main");
        addInstructionBreakpoint();
        var root = connect(DefaultRunExecutor.getRunExecutorInstance(), new Capabilities());
        configure(root);

        var update = disassembling(true);
        update.setSupportsFunctionBreakpoints(true);
        root.capabilities(update);

        assertRequests(root, List.of("configurationDone"), "setFunctionBreakpoints", "setInstructionBreakpoints", "configurationDone");
    }

    private static Capabilities functionBreakpoints() {
        var capabilities = new Capabilities();
        capabilities.setSupportsFunctionBreakpoints(true);
        return capabilities;
    }

    private static Capabilities disassembling(boolean supported) {
        var capabilities = new Capabilities();
        capabilities.setSupportsDisassembleRequest(supported);
        return capabilities;
    }

    private static Capabilities exceptionFilters(ExceptionBreakpointsFilter... filters) {
        var capabilities = new Capabilities();
        capabilities.setExceptionBreakpointFilters(filters);
        return capabilities;
    }

    private static ExceptionBreakpointsFilter filter(String id, boolean enabled) {
        var filter = new ExceptionBreakpointsFilter();
        filter.setFilter(id);
        filter.setLabel(id);
        filter.setDefault_(enabled);
        return filter;
    }

    private static ExceptionBreakpointsFilter conditionalFilter(String id, boolean enabled) {
        var filter = filter(id, enabled);
        filter.setSupportsCondition(true);
        return filter;
    }

    /**
     * Keeps the given exception breakpoint filters and conditions as the user's choices, as a debug session keeps them.
     */
    private static void chooseExceptionFilters(Map<String, String> conditions, ExceptionBreakpointsFilter... filters) {
        var settings = new FilterItemSettings();
        settings.setExceptionBreakpointsFilters(new ArrayList<>(List.of(filters)));
        settings.setExceptionBreakpointsFilterConditions(new TreeMap<>(conditions));
        UserDefinedDebugAdapterServerSettings.getInstance().setFilterSettings(CONFIGURATION_NAME, settings);
    }

    private void addFunctionBreakpoint(String name) {
        var type = XDebuggerUtil.getInstance().findBreakpointType(DAPFunctionBreakpointType.class);
        WriteAction.run(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addBreakpoint(type, new DAPFunctionBreakpointProperties(name)));
    }

    /**
     * Adds a data breakpoint whose data id can persist, which is sent to each DAP server of its configuration.
     */
    private void addDataBreakpoint(String dataId) {
        var type = XDebuggerUtil.getInstance().findBreakpointType(DAPDataBreakpointType.class);
        var properties = new DAPDataBreakpointProperties(dataId, dataId, DataBreakpointAccessType.WRITE, true, CONFIGURATION_NAME);
        WriteAction.run(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addBreakpoint(type, properties));
    }

    /**
     * Adds a breakpoint to the disassembly of the debug session.
     */
    private XLineBreakpoint<DisassemblyBreakpointProperties> addInstructionBreakpoint() {
        var file = DAPFileRegistry.getInstance().getOrCreateDAPFile(CONFIGURATION_NAME, DisassemblyFile.FILE_NAME, getProject());
        var type = XDebuggerUtil.getInstance().findBreakpointType(DisassemblyBreakpointType.class);
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addLineBreakpoint(type, file.getUrl(), 0, new DisassemblyBreakpointProperties()));
    }

    /**
     * Starts debugging, attached to a new {@link AdapterServer} whose root session answers 'initialize' with the given
     * capabilities, and returns the root session once it is configured.
     */
    private @NotNull Session debug(@NotNull Capabilities capabilities) throws Exception {
        var root = connect(DefaultDebugExecutor.getDebugExecutorInstance(), capabilities);
        configure(root);
        return root;
    }

    /**
     * Starts the debug session with the given executor, attached to a new {@link AdapterServer} whose root session
     * answers 'initialize' with the given capabilities, and returns the root session once it is connected, before it
     * starts the configuration.
     */
    private @NotNull Session connect(@NotNull Executor executor, @NotNull Capabilities capabilities) throws Exception {
        server.capabilities = capabilities;
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration(CONFIGURATION_NAME, type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), executor, configuration).buildAndExecute());
        var root = nextSession("the debug session connects");
        // the session registers the breakpoints of the project with its handlers once it starts: the configuration
        // starts after, so that it sends them all at once
        PlatformTestUtil.waitWithEventsDispatching("the debug session starts", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null;
        }, BOUND_SECONDS);
        return root;
    }

    /**
     * Starts the configuration of the given root session with 'initialized', and waits until the client is done with
     * it: it then asks for the threads.
     */
    private static void configure(@NotNull Session root) {
        root.initialized();
        PlatformTestUtil.waitWithEventsDispatching("the root session is configured", root.threadsAsked::isDone, BOUND_SECONDS);
    }

    /**
     * Asks the client of the given session to start a child session, whose debug adapter answers 'initialize' with the
     * given capabilities, and returns it once it is configured.
     */
    private @NotNull Session startChildSession(@NotNull Session parent, @NotNull Capabilities capabilities) {
        server.capabilities = capabilities;
        var started = parent.startChildSession();
        var child = nextSession("the child session connects");
        child.initialized();
        // the client answers 'startDebugging' once the child session is configured
        PlatformTestUtil.waitWithEventsDispatching("the child session is configured", started::isDone, BOUND_SECONDS);
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
     * Waits until the client has handled what the given session sent, and asserts the requests of the given kinds
     * which it then sent to the session, in their order.
     */
    private static void assertRequests(@NotNull Session session, @NotNull List<String> expected, @NotNull String... kinds) {
        waitUntilHandled(session);
        assertEquals(expected, session.requests(kinds));
    }

    /**
     * Waits until the client has handled the messages which the given session has sent, and the EDT what they gave it
     * to do: the client handles the messages of a connection one after the other, so once it has answered a request
     * which the session sends after them, it has handled them.
     */
    private static void waitUntilHandled(@NotNull Session session) {
        var answer = session.ping();
        PlatformTestUtil.waitWithEventsDispatching("the client handles the messages of the session", answer::isDone, BOUND_SECONDS);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        // what the EDT sent is there before the answer to this one
        var next = session.ping();
        PlatformTestUtil.waitWithEventsDispatching("the client handles the messages of the session", next::isDone, BOUND_SECONDS);
    }

    private @NotNull DAPDebugProcess getDebugProcess() {
        return (DAPDebugProcess) session.getDebugProcess();
    }

    /**
     * A debug adapter which accepts a connection per DAP session, as one that starts child sessions with
     * 'startDebugging' does.
     */
    private static class AdapterServer {

        private final ServerSocket serverSocket;
        // Runs the loop which accepts the connections and the message loop of each session, so that close() can stop
        // them all: the test framework reports the threads which a test leaves running
        private final ExecutorService executor = Executors.newCachedThreadPool();
        // The sessions which no test has taken yet, and all of them
        private final LinkedBlockingQueue<Session> sessions = new LinkedBlockingQueue<>();
        private final List<Session> all = new CopyOnWriteArrayList<>();
        private final Future<?> accepting;
        // What the next session answers to 'initialize'
        private volatile Capabilities capabilities = new Capabilities();
        // What a session does when it is asked to end the configuration, before it answers
        private volatile Consumer<Session> onConfigurationDone = session -> {
        };

        AdapterServer() throws IOException {
            this.serverSocket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            this.accepting = executor.submit(() -> {
                try {
                    while (!serverSocket.isClosed()) {
                        var session = new Session(this, serverSocket.accept());
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
     * A DAP session of an {@link AdapterServer}, over its own connection, which records the requests of the client.
     */
    private static class Session implements IDebugProtocolServer {

        private final AdapterServer server;
        private final Socket socket;
        private final IDebugProtocolClient client;
        private final RemoteEndpoint remoteEndpoint;
        // Done once the client has closed the connection
        private final Future<Void> listening;
        // Done once the client asks for the threads, as it does once the debug session has started
        private final CompletableFuture<Void> threadsAsked = new CompletableFuture<>();
        // The requests of the client which set something, and 'configurationDone', in their order
        private final List<String> requests = new CopyOnWriteArrayList<>();

        Session(@NotNull AdapterServer server, @NotNull Socket socket) throws IOException {
            this.server = server;
            this.socket = socket;
            var launcher = DSPLauncher.createServerLauncher(this, socket.getInputStream(), socket.getOutputStream(),
                    server.executor, consumer -> consumer);
            this.client = launcher.getRemoteProxy();
            this.remoteEndpoint = launcher.getRemoteEndpoint();
            this.listening = launcher.startListening();
        }

        /**
         * Returns the requests of the given kinds, in their order.
         */
        List<String> requests(String... kinds) {
            var names = Set.of(kinds);
            return requests.stream()
                    .filter(request -> names.contains(request.split(" ")[0]))
                    .toList();
        }

        /**
         * Starts the configuration.
         */
        void initialized() {
            client.initialized();
        }

        /**
         * Tells the client that the given capabilities changed.
         */
        void capabilities(@NotNull Capabilities capabilities) {
            var args = new CapabilitiesEventArguments();
            args.setCapabilities(capabilities);
            client.capabilities(args);
        }

        /**
         * Asks the client to start a child session, which connects to the server on its own connection, and returns
         * the future of the answer, which the client sends once the child session is configured.
         */
        CompletableFuture<Void> startChildSession() {
            var args = new StartDebuggingRequestArguments();
            args.setConfiguration(Map.of());
            args.setRequest(StartDebuggingRequestArgumentsType.ATTACH);
            return client.startDebugging(args);
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
            var capabilities = server.capabilities;
            capabilities.setSupportsConfigurationDoneRequest(true);
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            // the test starts the configuration with 'initialized'
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments args) {
            var response = new SetBreakpointsResponse();
            response.setBreakpoints(new Breakpoint[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<SetFunctionBreakpointsResponse> setFunctionBreakpoints(SetFunctionBreakpointsArguments args) {
            requests.add("setFunctionBreakpoints " + Arrays.stream(args.getBreakpoints()).map(FunctionBreakpoint::getName).toList());
            var response = new SetFunctionBreakpointsResponse();
            response.setBreakpoints(new Breakpoint[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<SetDataBreakpointsResponse> setDataBreakpoints(SetDataBreakpointsArguments args) {
            requests.add("setDataBreakpoints " + Arrays.stream(args.getBreakpoints()).map(DataBreakpoint::getDataId).toList());
            var response = new SetDataBreakpointsResponse();
            response.setBreakpoints(new Breakpoint[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<SetExceptionBreakpointsResponse> setExceptionBreakpoints(SetExceptionBreakpointsArguments args) {
            // the filters in their order of the panel, which is not the order of the debug adapter
            var request = "setExceptionBreakpoints " + Arrays.stream(args.getFilters()).sorted().toList();
            if (args.getFilterOptions() != null) {
                request += Arrays.stream(args.getFilterOptions())
                        .map(options -> options.getFilterId() + "=" + options.getCondition())
                        .sorted()
                        .collect(Collectors.joining(", ", " {", "}"));
            }
            requests.add(request);
            return CompletableFuture.completedFuture(new SetExceptionBreakpointsResponse());
        }

        @Override
        public CompletableFuture<SetInstructionBreakpointsResponse> setInstructionBreakpoints(SetInstructionBreakpointsArguments args) {
            requests.add("setInstructionBreakpoints");
            var response = new SetInstructionBreakpointsResponse();
            response.setBreakpoints(new Breakpoint[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            requests.add("configurationDone");
            server.onConfigurationDone.accept(this);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            threadsAsked.complete(null);
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
