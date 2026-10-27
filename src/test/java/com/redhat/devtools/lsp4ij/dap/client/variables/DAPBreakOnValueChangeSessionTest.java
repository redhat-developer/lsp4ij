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
package com.redhat.devtools.lsp4ij.dap.client.variables;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPDataBreakpointProperties;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPDataBreakpointType;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DataBreakpoint;
import org.eclipse.lsp4j.debug.DataBreakpointAccessType;
import org.eclipse.lsp4j.debug.DataBreakpointInfoArguments;
import org.eclipse.lsp4j.debug.DataBreakpointInfoResponse;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetDataBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetDataBreakpointsResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

/**
 * "Break on Value Change" in a debug session, against a DAP server in the test's process which the session attaches
 * to: on a value of the stopped frame, as the Variables view shows it, it asks the DAP server for its data and adds a
 * data breakpoint, which is sent to the DAP server and goes with the session, since its data can't persist.
 */
public class DAPBreakOnValueChangeSessionTest extends HeavyPlatformTestCase {

    private static final int FRAME_ID = 7;
    private static final int LOCALS_REFERENCE = 1;
    private static final int COUNTER_REFERENCE = 2;

    private CounterServer server;
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
            var manager = XDebuggerManager.getInstance(getProject()).getBreakpointManager();
            WriteAction.run(() -> dataBreakpoints().forEach(manager::removeBreakpoint));
        } finally {
            super.tearDown();
        }
    }

    public void testBreakOnValueChangeAddsADataBreakpointOnTheValue() throws Exception {
        debug(true);
        var count = value("counter", "count");
        assertEquals(Integer.valueOf(COUNTER_REFERENCE), count.getParentVariablesReference());

        var event = breakOnValueChange(count);
        assertTrue(event.getPresentation().isEnabledAndVisible());
        ActionManager.getInstance().getAction("DAP.BreakOnValueChange").actionPerformed(event);

        // the DAP server is asked for the data of 'count' in the variables of 'counter'
        var info = PlatformTestUtil.waitForFuture(server.dataBreakpointInfo, 10_000);
        assertEquals(Integer.valueOf(COUNTER_REFERENCE), info.getVariablesReference());
        assertEquals("count", info.getName());
        assertEquals(FRAME_ID, info.getFrameId());

        // and given a data breakpoint on that data, which IntelliJ shows by the DAP server's description
        PlatformTestUtil.waitWithEventsDispatching("the data breakpoint is sent",
                () -> server.dataIds().equals(List.of("2:count")), 10);
        var breakpoint = dataBreakpoints().get(0);
        assertEquals("Counter.count", breakpoint.getProperties().getDescription());
        assertSame(DataBreakpointAccessType.WRITE, breakpoint.getProperties().getAccessType());
        assertFalse(breakpoint.getProperties().isCanPersist());

        // its data is valid in this session only
        session.stop();
        PlatformTestUtil.waitWithEventsDispatching("the data breakpoint goes with the session",
                () -> dataBreakpoints().isEmpty(), 10);
    }

    public void testBreakOnValueChangeIsHiddenWhenTheDAPServerHasNoDataBreakpoints() throws Exception {
        debug(false);
        var event = breakOnValueChange(value("counter", "count"));
        assertFalse(event.getPresentation().isEnabledAndVisible());
    }

    /**
     * Starts debugging, attached to a new {@link CounterServer}, and waits for the program to stop.
     */
    private void debug(boolean supportsDataBreakpoints) throws Exception {
        server = new CounterServer(supportsDataBreakpoints);
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("counter", type.getConfigurationFactories()[0]);
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
            return session != null && session.isSuspended() && session.getCurrentStackFrame() instanceof DAPStackFrame;
        }, 30);
    }

    /**
     * Returns the DAP value at the given path in the locals of the stopped frame, as the Variables view gets it.
     */
    private @NotNull DAPValue value(String... path) {
        XValueContainer container = children(session.getCurrentStackFrame()).getBottomGroups().get(0);
        for (String name : path) {
            var children = children(container);
            container = IntStream.range(0, children.size())
                    .filter(i -> children.getName(i).equals(name))
                    .mapToObj(children::getValue)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No variable " + name));
        }
        return (DAPValue) container;
    }

    /**
     * Updates "Break on Value Change" with the given value selected in the Variables view.
     */
    private @NotNull AnActionEvent breakOnValueChange(@NotNull DAPValue value) {
        var tree = new XDebuggerTree(getProject(), new DAPDebuggerEditorsProvider(null, null), null,
                XDebuggerActions.VARIABLES_TREE_POPUP_GROUP, null);
        Disposer.register(getTestRootDisposable(), tree);
        var node = new XValueNodeImpl(tree, null, value.getName(), value);
        var action = ActionManager.getInstance().getAction("DAP.BreakOnValueChange");
        var event = TestActionEvent.createTestEvent(action,
                SimpleDataContext.getSimpleContext(XDebuggerTree.SELECTED_NODES, List.of(node)));
        action.update(event);
        return event;
    }

    private @NotNull List<XBreakpoint<DAPDataBreakpointProperties>> dataBreakpoints() {
        return List.copyOf(XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .getBreakpoints(DAPDataBreakpointType.class));
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
        return PlatformTestUtil.waitForFuture(done, 10_000);
    }

    /**
     * A DAP server of a program stopped in the frame 7, whose locals are a 'counter' with a field 'count'. With data
     * breakpoints, it watches 'count', with the data id "2:count", which can't persist.
     */
    private static class CounterServer implements IDebugProtocolServer {

        private final boolean supportsDataBreakpoints;
        private final ServerSocket serverSocket;
        private final CompletableFuture<DataBreakpointInfoArguments> dataBreakpointInfo = new CompletableFuture<>();
        private final List<SetDataBreakpointsArguments> setDataBreakpoints = new CopyOnWriteArrayList<>();
        private volatile IDebugProtocolClient client;

        CounterServer(boolean supportsDataBreakpoints) throws IOException {
            this.supportsDataBreakpoints = supportsDataBreakpoints;
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
            }, "Counter DAP server");
            accept.setDaemon(true);
            accept.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        /**
         * Returns the data ids of the last 'setDataBreakpoints'.
         */
        List<String> dataIds() {
            return setDataBreakpoints.isEmpty() ? List.of() :
                    Arrays.stream(setDataBreakpoints.get(setDataBreakpoints.size() - 1).getBreakpoints())
                            .map(DataBreakpoint::getDataId)
                            .toList();
        }

        void close() throws IOException {
            serverSocket.close();
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            capabilities.setSupportsDataBreakpoints(supportsDataBreakpoints);
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> client.initialized());
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> {
                var stopped = new StoppedEventArguments();
                stopped.setReason(StoppedEventArgumentsReason.PAUSE);
                stopped.setThreadId(1);
                stopped.setAllThreadsStopped(true);
                client.stopped(stopped);
            });
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
            frame.setId(FRAME_ID);
            frame.setName("run");
            frame.setLine(5);
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
            response.setVariables(switch (args.getVariablesReference()) {
                case LOCALS_REFERENCE -> new Variable[]{variable("counter", "Counter", COUNTER_REFERENCE)};
                case COUNTER_REFERENCE -> new Variable[]{variable("count", "0", 0)};
                default -> new Variable[0];
            });
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<DataBreakpointInfoResponse> dataBreakpointInfo(DataBreakpointInfoArguments args) {
            dataBreakpointInfo.complete(args);
            var response = new DataBreakpointInfoResponse();
            response.setDataId(args.getVariablesReference() + ":" + args.getName());
            response.setDescription("Counter.count");
            response.setAccessTypes(new DataBreakpointAccessType[]{DataBreakpointAccessType.WRITE});
            response.setCanPersist(false);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<SetDataBreakpointsResponse> setDataBreakpoints(SetDataBreakpointsArguments args) {
            setDataBreakpoints.add(args);
            var response = new SetDataBreakpointsResponse();
            response.setBreakpoints(Arrays.stream(args.getBreakpoints())
                    .map(data -> {
                        var breakpoint = new Breakpoint();
                        breakpoint.setVerified(true);
                        return breakpoint;
                    })
                    .toArray(Breakpoint[]::new));
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }

        private static Variable variable(String name, String value, int variablesReference) {
            var variable = new Variable();
            variable.setName(name);
            variable.setValue(value);
            variable.setVariablesReference(variablesReference);
            return variable;
        }
    }
}
