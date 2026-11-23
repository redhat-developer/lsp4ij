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
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * "Set Value…" (F2) in a debug session, against a DAP server in the test's process which the session attaches to.
 * The DAP server supports 'setVariable' and not 'setExpression'. A variable of the Variables view has the
 * 'variablesReference' of its parent, which 'setVariable' needs. An Evaluate result has none: 'setVariable' can't set
 * it, so "Set Value…" isn't offered for it.
 */
public class DAPSetValueOfEvaluateResultSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final int FRAME_ID = 7;
    private static final int LOCALS_REFERENCE = 1;

    private AnswerServer server;
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

    public void testSetValueOfAVariableSendsSetVariableWithTheReferenceOfItsParent() throws Exception {
        debug();
        var answer = local("answer");
        assertEquals(Integer.valueOf(LOCALS_REFERENCE), answer.getParentVariablesReference());

        var node = node(answer);
        assertTrue("\"Set Value…\" is offered for a variable", isSetValueEnabled(node));
        var errors = new CopyOnWriteArrayList<String>();
        // As SetValueInplaceEditor.doOKAction, once the user typed a value in "Set Value…" and pressed Enter
        DebuggerUIUtil.setTreeNodeValue(node, expression("42"), errors::add);

        var args = server.setVariables.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setVariable'", args);
        assertEquals("answer", args.getName());
        assertEquals(LOCALS_REFERENCE, args.getVariablesReference());
        assertEquals("42", args.getValue());
        assertEquals(List.of(), errors);
    }

    public void testSetValueIsNotOfferedForAnEvaluateResult() throws Exception {
        debug();
        var result = evaluate("answer");
        var evaluate = server.evaluations.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'evaluate'", evaluate);
        assertNull("An Evaluate result has no parent", result.getParentVariablesReference());

        assertFalse("\"Set Value…\" isn't offered for an Evaluate result", isSetValueEnabled(node(result)));
        assertNull("'setVariable' can't set a value without a parent", result.getModifier());
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));
    }

    /**
     * Starts debugging, attached to a new {@link AnswerServer}, and waits for the program to stop.
     */
    private void debug() throws Exception {
        server = new AnswerServer();
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("answer", type.getConfigurationFactories()[0]);
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
        }, BOUND_SECONDS);
    }

    /**
     * Returns the value of the given expression, evaluated in the stopped frame.
     */
    private @NotNull DAPValue evaluate(@NotNull String expression) {
        var evaluator = session.getCurrentStackFrame().getEvaluator();
        assertNotNull(evaluator);
        var result = new CompletableFuture<XValue>();
        evaluator.evaluate(expression, new XDebuggerEvaluator.XEvaluationCallback() {
            @Override
            public void evaluated(@NotNull XValue value) {
                result.complete(value);
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                result.completeExceptionally(new AssertionError(errorMessage));
            }
        }, null);
        return (DAPValue) PlatformTestUtil.waitForFuture(result, BOUND_SECONDS * 1000L);
    }

    /**
     * Returns the DAP value of the given name in the locals of the stopped frame, as the Variables view gets it.
     */
    private @NotNull DAPValue local(@NotNull String name) {
        XValueContainer locals = children(session.getCurrentStackFrame()).getBottomGroups().get(0);
        var children = children(locals);
        return (DAPValue) IntStream.range(0, children.size())
                .filter(i -> children.getName(i).equals(name))
                .mapToObj(children::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No variable " + name));
    }

    /**
     * Returns a node of a debugger tree which shows the given value, as the Variables or Watches view does.
     */
    private @NotNull XValueNodeImpl node(@NotNull DAPValue value) {
        var tree = new XDebuggerTree(getProject(), new DAPDebuggerEditorsProvider(null, null), null,
                XDebuggerActions.VARIABLES_TREE_POPUP_GROUP, null);
        Disposer.register(getTestRootDisposable(), tree);
        return new XValueNodeImpl(tree, null, value.getName(), value);
    }

    /**
     * Returns whether "Set Value…" (F2) is enabled with the given node selected in a debugger tree.
     */
    private static boolean isSetValueEnabled(@NotNull XValueNodeImpl node) {
        var action = ActionManager.getInstance().getAction(XDebuggerActions.SET_VALUE);
        assertNotNull(action);
        var event = TestActionEvent.createTestEvent(action,
                SimpleDataContext.getSimpleContext(XDebuggerTree.SELECTED_NODES, List.of(node)));
        action.update(event);
        return event.getPresentation().isEnabledAndVisible();
    }

    private static @NotNull XExpression expression(@NotNull String text) {
        return XDebuggerUtil.getInstance().createExpression(text, null, null, EvaluationMode.EXPRESSION);
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
     * A DAP server of a program stopped in the frame 7, whose locals are 'answer' = 41. It can set variables, but not
     * expressions, and evaluates any expression to 41.
     */
    private static class AnswerServer implements IDebugProtocolServer {

        private final ServerSocket serverSocket;
        private final LinkedBlockingQueue<EvaluateArguments> evaluations = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<SetVariableArguments> setVariables = new LinkedBlockingQueue<>();
        private volatile IDebugProtocolClient client;

        AnswerServer() throws IOException {
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
            }, "Answer DAP server");
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
            capabilities.setSupportsSetVariable(true);
            capabilities.setSupportsSetExpression(false);
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
        public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
            evaluations.add(args);
            var response = new EvaluateResponse();
            response.setResult("41");
            response.setVariablesReference(0);
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
