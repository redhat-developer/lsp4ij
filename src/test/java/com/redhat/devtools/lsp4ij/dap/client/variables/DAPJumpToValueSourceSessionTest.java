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
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
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
import org.eclipse.lsp4j.debug.LocationsArguments;
import org.eclipse.lsp4j.debug.LocationsResponse;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceArguments;
import org.eclipse.lsp4j.debug.SourceResponse;
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
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * "Jump to Value Source" in a debug session, against a DAP server in the test's process which the session attaches
 * to: on a value with a 'valueLocationReference', as the Variables view or Evaluate shows it, it asks the DAP server
 * where the reference points to with the 'locations' request, and opens that source, a local file or the text of a
 * source reference, at the line and column of the answer. It reports an error or an empty answer of the DAP server,
 * and isn't offered for a value without a 'valueLocationReference'.
 */
public class DAPJumpToValueSourceSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final int FRAME_ID = 7;
    private static final int LOCALS_REFERENCE = 1;
    // The locations which the DAP server gives: a function in a local file, a function in the text of a source
    // reference, a reference which the DAP server fails to resolve, and one which it resolves to nothing
    private static final int ON_CLICK_LOCATION = 6;
    private static final int GENERATED_LOCATION = 8;
    private static final int BROKEN_LOCATION = 9;
    private static final int NOWHERE_LOCATION = 10;
    private static final int GENERATED_SOURCE_REFERENCE = 11;

    private static final String HANDLERS = """
            // The handlers of the page
            function setup(button) {
                button.onclick = function onClick() {
                };
            }
            """;
    private static final String GENERATED = """
            // generated
            var generated = () => 42;
            """;

    private Path handlers;
    private CallbackServer server;
    private XDebugSession session;
    private final List<String> notifications = new CopyOnWriteArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var directory = Files.createDirectories(Path.of(FileUtil.getTempDirectory()));
        handlers = Files.createTempFile(directory, "handlers", ".js").toRealPath();
        Files.writeString(handlers, HANDLERS);
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                notifications.add(notification.getContent());
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            var editors = FileEditorManager.getInstance(getProject());
            Arrays.stream(editors.getOpenFiles()).forEach(editors::closeFile);
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

    public void testJumpToValueSourceOpensTheLocalFileAtTheLineAndColumnOfTheLocation() throws Exception {
        debug();
        var onClick = node(local("onClick"));
        var event = update(onClick);
        assertTrue("A value with a 'valueLocationReference' offers the action", event.getPresentation().isVisible());
        assertTrue(event.getPresentation().isEnabled());

        action().actionPerformed(event);

        // the DAP server is asked where the reference of the value points to
        assertEquals(Integer.valueOf(ON_CLICK_LOCATION), PlatformTestUtil.waitForFuture(server.nextLocation(), BOUND_SECONDS * 1000L));
        // and the file is opened at the line and column of the answer, which start at 1, as LSP4IJ asks for them
        var editor = waitForEditor(handlersFile());
        assertEquals(new LogicalPosition(2, 21), editor.getCaretModel().getLogicalPosition());
        assertTrue(textAtCaret(editor).startsWith("function onClick"));
        assertEquals(List.of(), notifications);
    }

    public void testJumpToValueSourceOpensTheTextOfASourceReference() throws Exception {
        debug();
        action().actionPerformed(update(node(local("generated"))));

        assertEquals(Integer.valueOf(GENERATED_LOCATION), PlatformTestUtil.waitForFuture(server.nextLocation(), BOUND_SECONDS * 1000L));
        // the text of the source is the DAP server's, even if the source has a path
        assertEquals(Integer.valueOf(GENERATED_SOURCE_REFERENCE), PlatformTestUtil.waitForFuture(server.nextSource(), BOUND_SECONDS * 1000L));
        PlatformTestUtil.waitWithEventsDispatching("the source is opened", () -> {
            var editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
            var file = editor != null ? fileOf(editor) : null;
            return file != null && "generated.js".equals(file.getName()) && textAtCaret(editor).startsWith("() => 42");
        }, BOUND_SECONDS);
        var editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        assertEquals(GENERATED, editor.getDocument().getText());
        assertEquals(new LogicalPosition(1, 16), editor.getCaretModel().getLogicalPosition());
        assertEquals(List.of(), notifications);
    }

    public void testJumpToValueSourceIsNotOfferedForAValueWithoutAValueLocationReference() throws Exception {
        debug();
        var event = update(node(local("count")));
        assertFalse("A value without a 'valueLocationReference' doesn't offer the action", event.getPresentation().isVisible());
        assertFalse(event.getPresentation().isEnabled());

        // Performed anyway, as from a shortcut, it asks nothing: the request which the DAP server receives first is the
        // one for the next value
        action().actionPerformed(event);
        action().actionPerformed(update(node(local("onClick"))));
        assertEquals(Integer.valueOf(ON_CLICK_LOCATION), PlatformTestUtil.waitForFuture(server.nextLocation(), BOUND_SECONDS * 1000L));
        waitForEditor(handlersFile());
        assertEquals("The DAP server is asked only for the value with a reference", List.of(), List.copyOf(server.locations));
    }

    public void testJumpToValueSourceReportsAnErrorOfTheDAPServer() throws Exception {
        debug();
        action().actionPerformed(update(node(local("broken"))));

        assertEquals(Integer.valueOf(BROKEN_LOCATION), PlatformTestUtil.waitForFuture(server.nextLocation(), BOUND_SECONDS * 1000L));
        var expected = DAPBundle.message("dap.variables.valueSource.failed", "broken", "No location for 9");
        PlatformTestUtil.waitWithEventsDispatching("the error is reported", () -> notifications.contains(expected), BOUND_SECONDS);
        assertNull("No file is opened", FileEditorManager.getInstance(getProject()).getSelectedTextEditor());
    }

    public void testJumpToValueSourceReportsAnAnswerWithoutALocation() throws Exception {
        debug();
        action().actionPerformed(update(node(local("nowhere"))));

        assertEquals(Integer.valueOf(NOWHERE_LOCATION), PlatformTestUtil.waitForFuture(server.nextLocation(), BOUND_SECONDS * 1000L));
        var expected = DAPBundle.message("dap.variables.valueSource.none", "nowhere");
        PlatformTestUtil.waitWithEventsDispatching("the empty answer is reported", () -> notifications.contains(expected), BOUND_SECONDS);
        assertNull("No file is opened", FileEditorManager.getInstance(getProject()).getSelectedTextEditor());
    }

    public void testJumpToValueSourceOfAnEvaluateResult() throws Exception {
        debug();
        var result = evaluate("onClick");
        var event = update(node(result));
        assertTrue("An Evaluate result with a 'valueLocationReference' offers the action", event.getPresentation().isEnabledAndVisible());

        action().actionPerformed(event);

        assertEquals(Integer.valueOf(ON_CLICK_LOCATION), PlatformTestUtil.waitForFuture(server.nextLocation(), BOUND_SECONDS * 1000L));
        var editor = waitForEditor(handlersFile());
        assertEquals(new LogicalPosition(2, 21), editor.getCaretModel().getLogicalPosition());
    }

    public void testSetValueGivesTheValueTheLocationReferenceOfTheNewValue() throws Exception {
        debug();
        var count = local("count");
        assertFalse(update(node(count)).getPresentation().isVisible());

        var modified = new CompletableFuture<Void>();
        var modifier = count.getModifier();
        assertNotNull(modifier);
        modifier.setValue(XDebuggerUtil.getInstance().createExpression("onClick", null, null, EvaluationMode.EXPRESSION),
                new XValueModifier.XModificationCallback() {
                    @Override
                    public void valueModified() {
                        modified.complete(null);
                    }

                    @Override
                    public void errorOccurred(@NotNull String errorMessage) {
                        modified.completeExceptionally(new AssertionError(errorMessage));
                    }
                });
        PlatformTestUtil.waitForFuture(modified, BOUND_SECONDS * 1000L);

        assertTrue("The new value of 'setVariable' has a 'valueLocationReference'",
                update(node(count)).getPresentation().isEnabledAndVisible());
    }

    public void testJumpToValueSourceIsDisabledOnceTheSessionHasEnded() throws Exception {
        debug();
        var onClick = node(local("onClick"));
        session.stop();
        PlatformTestUtil.waitWithEventsDispatching("the session ends", () -> session.isStopped(), BOUND_SECONDS);

        var event = update(onClick);
        assertFalse("The reference of a value is valid only in its session", event.getPresentation().isEnabled());
        action().actionPerformed(event);
        assertNull(FileEditorManager.getInstance(getProject()).getSelectedTextEditor());
        assertEquals(List.of(), List.copyOf(server.locations));
    }

    /**
     * Starts debugging, attached to a new {@link CallbackServer}, and waits for the program to stop.
     */
    private void debug() throws Exception {
        server = new CallbackServer(handlers);
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("callbacks", type.getConfigurationFactories()[0]);
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

    private static @NotNull AnAction action() {
        var action = ActionManager.getInstance().getAction("DAP.JumpToValueSource");
        assertNotNull("The action is registered", action);
        return action;
    }

    /**
     * Updates "Jump to Value Source" with the given node selected in a debugger tree.
     */
    private static @NotNull AnActionEvent update(@NotNull XValueNodeImpl node) {
        var action = action();
        var event = TestActionEvent.createTestEvent(action,
                SimpleDataContext.getSimpleContext(XDebuggerTree.SELECTED_NODES, List.of(node)));
        action.update(event);
        return event;
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
     * Waits until the given file is opened in the selected editor, and returns that editor.
     */
    private @NotNull Editor waitForEditor(@NotNull VirtualFile file) {
        PlatformTestUtil.waitWithEventsDispatching("the file is opened", () -> {
            var editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
            return editor != null && file.equals(fileOf(editor)) && editor.getCaretModel().getOffset() > 0;
        }, BOUND_SECONDS);
        return FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
    }

    private @NotNull VirtualFile handlersFile() {
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(handlers);
        assertNotNull(file);
        return file;
    }

    private static @Nullable VirtualFile fileOf(@NotNull Editor editor) {
        return FileDocumentManager.getInstance().getFile(editor.getDocument());
    }

    private static @NotNull String textAtCaret(@NotNull Editor editor) {
        return editor.getDocument().getText().substring(editor.getCaretModel().getOffset());
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
     * A DAP server of a program stopped in the frame 7, whose locals are functions, each with a 'valueLocationReference':
     * 'onClick', defined in a local file, 'generated', defined in the text of a source reference, 'broken', whose
     * reference the DAP server fails to resolve, and 'nowhere', whose reference it resolves to nothing; and 'count',
     * a number, without a 'valueLocationReference'. It evaluates any expression to 'onClick', and setting a variable
     * to 'onClick' gives it the location of 'onClick'.
     */
    private static class CallbackServer implements IDebugProtocolServer {

        private final Path handlers;
        private final ServerSocket serverSocket;
        private final LinkedBlockingQueue<Integer> locations = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<Integer> sources = new LinkedBlockingQueue<>();
        private volatile IDebugProtocolClient client;

        CallbackServer(@NotNull Path handlers) throws IOException {
            this.handlers = handlers;
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
            }, "Callback DAP server");
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
         * @return the future of the location reference of the next 'locations' request.
         */
        CompletableFuture<Integer> nextLocation() {
            return CompletableFuture.supplyAsync(() -> take(locations, "locations"));
        }

        /**
         * @return the future of the source reference of the next 'source' request.
         */
        CompletableFuture<Integer> nextSource() {
            return CompletableFuture.supplyAsync(() -> take(sources, "source"));
        }

        /**
         * Returns the reference of the next request of the given queue, waiting at most as long as the test, so that a
         * request which never comes fails the future, and frees the thread which waits for it.
         */
        private static int take(@NotNull LinkedBlockingQueue<Integer> queue, @NotNull String request) {
            try {
                Integer reference = queue.poll(BOUND_SECONDS, TimeUnit.SECONDS);
                if (reference == null) {
                    throw new AssertionError("The DAP server received no '" + request + "' request within "
                            + BOUND_SECONDS + " seconds");
                }
                return reference;
            } catch (InterruptedException e) {
                // java.lang's: the Thread of this class is the DAP one
                java.lang.Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            capabilities.setSupportsSetVariable(true);
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
            response.setVariables(args.getVariablesReference() == LOCALS_REFERENCE ? new Variable[]{
                    variable("onClick", "function onClick", ON_CLICK_LOCATION),
                    variable("generated", "function generated", GENERATED_LOCATION),
                    variable("broken", "function broken", BROKEN_LOCATION),
                    variable("nowhere", "function nowhere", NOWHERE_LOCATION),
                    variable("count", "0", null)
            } : new Variable[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
            var response = new EvaluateResponse();
            response.setResult("function onClick");
            response.setVariablesReference(0);
            response.setValueLocationReference(ON_CLICK_LOCATION);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<SetVariableResponse> setVariable(SetVariableArguments args) {
            var response = new SetVariableResponse();
            response.setValue(args.getValue());
            response.setValueLocationReference("onClick".equals(args.getValue()) ? ON_CLICK_LOCATION : null);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<LocationsResponse> locations(LocationsArguments args) {
            int reference = args.getLocationReference();
            locations.add(reference);
            return switch (reference) {
                case ON_CLICK_LOCATION -> {
                    var source = new Source();
                    source.setName("handlers.js");
                    source.setPath(handlers.toString());
                    yield CompletableFuture.completedFuture(location(source, 3, 22));
                }
                case GENERATED_LOCATION -> {
                    var source = new Source();
                    source.setName("generated.js");
                    // a path which isn't a local file: the text is the DAP server's
                    source.setPath("<generated>/generated.js");
                    source.setSourceReference(GENERATED_SOURCE_REFERENCE);
                    yield CompletableFuture.completedFuture(location(source, 2, 17));
                }
                case BROKEN_LOCATION -> CompletableFuture.failedFuture(new ResponseErrorException(
                        new ResponseError(ResponseErrorCode.InvalidParams, "No location for " + reference, null)));
                default -> CompletableFuture.completedFuture(null);
            };
        }

        @Override
        public CompletableFuture<SourceResponse> source(SourceArguments args) {
            sources.add(args.getSourceReference());
            var response = new SourceResponse();
            response.setContent(args.getSourceReference() == GENERATED_SOURCE_REFERENCE ? GENERATED : "");
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }

        private static LocationsResponse location(@NotNull Source source, int line, int column) {
            var location = new LocationsResponse();
            location.setSource(source);
            location.setLine(line);
            location.setColumn(column);
            return location;
        }

        private static Variable variable(@NotNull String name, @NotNull String value, @Nullable Integer valueLocationReference) {
            var variable = new Variable();
            variable.setName(name);
            variable.setValue(value);
            variable.setVariablesReference(0);
            variable.setValueLocationReference(valueLocationReference);
            return variable;
        }
    }
}
