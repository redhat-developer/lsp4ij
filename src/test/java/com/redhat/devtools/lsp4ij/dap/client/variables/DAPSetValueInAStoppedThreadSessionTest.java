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
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.frame.XValueModifier;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.ContinueResponse;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetExpressionArguments;
import org.eclipse.lsp4j.debug.SetExpressionResponse;
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
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * "Set Value…" at a stop of a program with the threads 'main' and 'worker', against a DAP server in the test's process
 * which the session attaches to: 'main' stops, and the DAP server says that all threads stopped. When the worker then
 * continues alone ({@code allThreadsContinued: false}), as java-debug, debugpy and vscode-js-debug say, the values of
 * 'main', which stays stopped, can still be set, and those of the worker can't. A request of the session which resumes
 * a single thread ('singleThread'), a stop of all threads, and a 'continued' event or a resume of all threads still make
 * the values of every thread of the stop out of date.
 */
public class DAPSetValueInAStoppedThreadSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final int MAIN = 1;
    private static final int WORKER = 2;
    /**
     * The top frame of each thread, and the 'variablesReference' of its locals, which the DAP server gives again at
     * each stop.
     */
    private static final int MAIN_FRAME = 11;
    private static final int WORKER_FRAME = 12;
    private static final int MAIN_LOCALS = 21;
    private static final int WORKER_LOCALS = 22;

    private ThreadsServer server;
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

    public void testTheValuesOfTheStoppedThreadCanBeSetOnceAnotherThreadContinuedAlone() throws Exception {
        debug();
        var mainFrame = frameOf("main");
        var workerFrame = frameOf("worker");
        var answer = local(mainFrame, "answer");
        var count = local(workerFrame, "count");
        var mainResult = evaluate(mainFrame, "answer");
        var workerResult = evaluate(workerFrame, "count");

        server.continued(WORKER, false);
        server.waitUntilHandled();
        assertTrue("The session stays suspended where 'main' stopped", session.isSuspended());

        // 'main' is still stopped: its values are set
        assertNull("The local of 'main' is set", setValue(answer, "42"));
        var setVariable = server.setVariables.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setVariable'", setVariable);
        assertEquals("answer", setVariable.getName());
        assertEquals(MAIN_LOCALS, setVariable.getVariablesReference());
        assertEquals("42", setVariable.getValue());
        assertEquals("The local of 'main' shows its new value", "42", answer.getShownVariable().getValue());
        assertNull("The Evaluate result of 'main' is set", setValue(mainResult, "43"));
        var setExpression = server.setExpressions.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setExpression'", setExpression);
        assertEquals(Integer.valueOf(MAIN_FRAME), setExpression.getFrameId());
        assertEquals("43", mainResult.getShownVariable().getValue());

        // The worker runs: its values are out of date
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(count, "6"));
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(workerResult, "7"));
        assertEquals("The DAP server receives no other 'setVariable'", List.of(), List.copyOf(server.setVariables));
        assertEquals("The DAP server receives no other 'setExpression'", List.of(), List.copyOf(server.setExpressions));
    }

    public void testTheValuesOfEveryThreadAreOutOfDateOnceTheSessionStepsOneThreadAlone() throws Exception {
        debug();
        ((DAPDebugProcess) session.getDebugProcess()).setStepOnlyCurrentThread(true);
        var answer = local(frameOf("main"), "answer");
        var count = local(frameOf("worker"), "count");

        // Step Over resumes 'main' alone, and the DAP server holds back the 'stopped' event which ends the step: GDB,
        // which supports 'singleThread', forgets the references of every thread whenever the program resumes
        session.stepOver(false);
        var next = server.nexts.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'next'", next);
        assertEquals(Boolean.TRUE, next.getSingleThread());

        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(count, "6"));
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(answer, "42"));
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));
    }

    public void testAStopOfAllThreadsMakesTheValuesOfEveryThreadOfTheEarlierStopOutOfDate() throws Exception {
        debug();
        var answer = local(frameOf("main"), "answer");
        var count = local(frameOf("worker"), "count");
        server.continued(WORKER, false);
        server.waitUntilHandled();

        // The worker stops again, and the DAP server stops all threads, also 'main', which never left its stop: the
        // DAP server may give the references of the new stop, the same numbers, to other values
        var earlierStop = session.getSuspendContext();
        server.stop(WORKER, true);
        PlatformTestUtil.waitWithEventsDispatching("the session is suspended where the worker stopped", () ->
                session.getSuspendContext() != earlierStop && currentFrameId() == WORKER_FRAME, BOUND_SECONDS);

        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(answer, "42"));
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(count, "6"));
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));

        // The values of 'main' at the new stop can be set
        assertNull("The local of 'main' at the new stop is set", setValue(local(frameOf("main"), "answer"), "42"));
        var setVariable = server.setVariables.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setVariable'", setVariable);
        assertEquals(MAIN_LOCALS, setVariable.getVariablesReference());
    }

    public void testTheValuesOfEveryThreadAreOutOfDateOnceAllThreadsContinued() throws Exception {
        debug();
        var answer = local(frameOf("main"), "answer");
        var count = local(frameOf("worker"), "count");

        // A 'continued' event without 'allThreadsContinued' continues all threads
        server.continued(MAIN, null);
        server.waitUntilHandled();
        assertFalse("The session runs", session.isSuspended());

        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(answer, "42"));
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(count, "6"));
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));
    }

    public void testTheValuesOfEveryThreadAreOutOfDateOnceTheSessionResumes() throws Exception {
        debug();
        var answer = local(frameOf("main"), "answer");
        var count = local(frameOf("worker"), "count");

        // Resume sends 'continue', which resumes all threads; the DAP server sends no 'continued' event for it
        session.resume();
        var resumed = server.continues.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'continue'", resumed);
        assertNull("'continue' resumes all threads", resumed.getSingleThread());

        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(answer, "42"));
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(count, "6"));
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));
    }

    /**
     * Starts debugging, attached to a new {@link ThreadsServer}, and waits for the session to be suspended where 'main'
     * stopped.
     */
    private void debug() throws Exception {
        server = new ThreadsServer();
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("threads", type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
                        .buildAndExecute());
        PlatformTestUtil.waitWithEventsDispatching("the session is suspended where 'main' stopped", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && session.isSuspended() && currentFrameId() == MAIN_FRAME;
        }, BOUND_SECONDS);
        server.waitUntilHandled();
    }

    private int currentFrameId() {
        return session.getCurrentStackFrame() instanceof DAPStackFrame frame ? frame.getFrameId() : -1;
    }

    /**
     * Returns the top frame of the thread with the given name at the stop where the session is suspended, as the Frames
     * view shows it once the thread is selected.
     */
    private @NotNull DAPStackFrame frameOf(@NotNull String thread) {
        var context = session.getSuspendContext();
        assertNotNull("The session is suspended", context);
        var stack = Arrays.stream(context.getExecutionStacks())
                .filter(s -> s.getDisplayName().equals(thread))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No execution stack of the thread " + thread));
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
        var computed = await("the frames of the thread " + thread + " are computed", done);
        assertFalse("The thread " + thread + " has frames", computed.isEmpty());
        return (DAPStackFrame) computed.get(0);
    }

    /**
     * Returns the DAP value of the given name in the locals of the given frame, as the Variables view gets it.
     */
    private @NotNull DAPValue local(@NotNull DAPStackFrame frame, @NotNull String name) {
        XValueContainer locals = children(frame).getBottomGroups().get(0);
        var children = children(locals);
        return (DAPValue) IntStream.range(0, children.size())
                .filter(i -> children.getName(i).equals(name))
                .mapToObj(children::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No variable " + name));
    }

    /**
     * Returns the value of the given expression, evaluated in the given frame, as Evaluate Expression shows it.
     */
    private @NotNull DAPValue evaluate(@NotNull DAPStackFrame frame, @NotNull String expression) throws InterruptedException {
        var evaluator = frame.getEvaluator();
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
        var value = (DAPValue) await("'" + expression + "' is evaluated", result);
        assertNotNull("The DAP server receives 'evaluate'", server.evaluations.poll(BOUND_SECONDS, TimeUnit.SECONDS));
        return value;
    }

    /**
     * Sets the given value as "Set Value…" does once the user typed it and pressed Enter, and returns the error which
     * the modifier reports, or null if the value is modified.
     */
    private static @Nullable String setValue(@NotNull DAPValue value, @NotNull String text) {
        XValueModifier modifier = value.getModifier();
        assertNotNull("\"Set Value…\" is offered", modifier);
        var done = new CompletableFuture<@Nullable String>();
        modifier.setValue(XDebuggerUtil.getInstance().createExpression(text, null, null, EvaluationMode.EXPRESSION),
                new XValueModifier.XModificationCallback() {
                    @Override
                    public void valueModified() {
                        done.complete(null);
                    }

                    @Override
                    public void errorOccurred(@NotNull String errorMessage) {
                        done.complete(errorMessage);
                    }
                });
        return await("the value of '" + value.getName() + "' is set or refused", done);
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
        return await("the children are computed", done);
    }

    /**
     * Waits, dispatching the events of the EDT, until the given future is done, and returns its result.
     */
    private static <T> T await(@NotNull String what, @NotNull CompletableFuture<T> future) {
        PlatformTestUtil.waitWithEventsDispatching(what, future::isDone, BOUND_SECONDS);
        return future.join();
    }

    /**
     * A DAP server of a program with the threads 'main' and 'worker', which supports 'setVariable', 'setExpression' and
     * 'singleThread'. Once configured, 'main' stops, and the DAP server says that all threads stopped. Each thread has
     * one frame, whose locals are 'answer' = 41 in 'main' and 'count' = 5 in the worker, with the same ids at each stop.
     * The test says when threads continue and stop; a step runs until the test says that it stops.
     */
    private static class ThreadsServer implements IDebugProtocolServer {

        private final ServerSocket serverSocket;
        private final LinkedBlockingQueue<EvaluateArguments> evaluations = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<SetVariableArguments> setVariables = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<SetExpressionArguments> setExpressions = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<NextArguments> nexts = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<ContinueArguments> continues = new LinkedBlockingQueue<>();
        // The client, and the endpoint which sends it requests, once it connected
        private volatile IDebugProtocolClient client;
        private volatile RemoteEndpoint endpoint;

        ThreadsServer() throws IOException {
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
            }, "Threads DAP server");
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
         * Sends that the program continued: all threads, or the given thread alone for {@code allThreadsContinued: false}.
         */
        void continued(int threadId, @Nullable Boolean allThreadsContinued) {
            var continued = new ContinuedEventArguments();
            continued.setThreadId(threadId);
            continued.setAllThreadsContinued(allThreadsContinued);
            client.continued(continued);
        }

        /**
         * Sends that the given thread stopped, and with it all threads for {@code allThreadsStopped: true}.
         */
        void stop(int threadId, boolean allThreadsStopped) {
            var stopped = new StoppedEventArguments();
            stopped.setReason(StoppedEventArgumentsReason.BREAKPOINT);
            stopped.setThreadId(threadId);
            stopped.setAllThreadsStopped(allThreadsStopped);
            client.stopped(stopped);
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
            capabilities.setSupportsSetVariable(true);
            capabilities.setSupportsSetExpression(true);
            capabilities.setSupportsSingleThreadExecutionRequests(true);
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(client::initialized);
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> stop(MAIN, true));
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            var response = new ThreadsResponse();
            response.setThreads(new Thread[]{thread(MAIN, "main"), thread(WORKER, "worker")});
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
            var frame = new StackFrame();
            frame.setId(args.getThreadId() == MAIN ? MAIN_FRAME : WORKER_FRAME);
            frame.setName(args.getThreadId() == MAIN ? "run" : "work");
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
            locals.setVariablesReference(args.getFrameId() == MAIN_FRAME ? MAIN_LOCALS : WORKER_LOCALS);
            var response = new ScopesResponse();
            response.setScopes(new Scope[]{locals});
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
            var response = new VariablesResponse();
            response.setVariables(switch (args.getVariablesReference()) {
                case MAIN_LOCALS -> new Variable[]{variable("answer", "41")};
                case WORKER_LOCALS -> new Variable[]{variable("count", "5")};
                default -> new Variable[0];
            });
            return CompletableFuture.completedFuture(response);
        }

        private static @NotNull Variable variable(@NotNull String name, @NotNull String value) {
            var variable = new Variable();
            variable.setName(name);
            variable.setValue(value);
            variable.setEvaluateName(name);
            return variable;
        }

        @Override
        public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
            evaluations.add(args);
            var response = new EvaluateResponse();
            response.setResult(args.getFrameId() != null && args.getFrameId() == MAIN_FRAME ? "41" : "5");
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
        public CompletableFuture<SetExpressionResponse> setExpression(SetExpressionArguments args) {
            setExpressions.add(args);
            var response = new SetExpressionResponse();
            response.setValue(args.getValue());
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> next(NextArguments args) {
            // The program runs: the 'stopped' event which ends the step is held back
            nexts.add(args);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
            continues.add(args);
            return CompletableFuture.completedFuture(new ContinueResponse());
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
