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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.LocationsArguments;
import org.eclipse.lsp4j.debug.LocationsResponse;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

/**
 * Two sources which the debug adapter gives by source reference, with the same name and different texts, in a debug
 * session against a DAP server in the test's process which the session attaches to: the program stops in a frame of
 * each, as two scripts evaluated with {@code eval} are both named {@code <anonymous>}. Each source has its own file,
 * and editor tab, named after the source, with its own text (#2196).
 */
public class DAPSameNameSourceReferencesSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final String NAME = "<anonymous>";
    private static final int FIRST_REFERENCE = 5;
    private static final int SECOND_REFERENCE = 9;
    private static final String FIRST = """
            // the first eval
            var first = 1;
            """;
    private static final String SECOND = """
            // the second eval
            var zero = 0;
            var second = 2;
            """;
    // The lines of the frames, 1-based as the DAP server gives them
    private static final int FIRST_LINE = 2;
    private static final int SECOND_LINE = 3;
    private static final int LOCALS_REFERENCE = 1;
    // The declaration of the local 'second' of the first frame, in the second source
    private static final int SECOND_DECLARATION = 21;

    private EvalServer server;
    private XDebugSession session;

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

    public void testEachFrameShowsTheTextOfItsOwnSource() throws Exception {
        debug();
        var stack = session.getSuspendContext().getActiveExecutionStack();
        assertNotNull(stack);
        var frames = framesOf(stack);
        assertEquals(2, frames.size());
        var first = frames.get(0);
        var second = frames.get(1);

        var firstEditor = show(stack, first, true, FIRST, FIRST_LINE);
        assertTrue(textAtCaret(firstEditor).startsWith("var first = 1;"));
        // The second source has the same name: its frame shows its own text, not the one of the first source
        var secondEditor = show(stack, second, false, SECOND, SECOND_LINE);
        assertTrue(textAtCaret(secondEditor).startsWith("var second = 2;"));
        // in a file, and an editor tab, of its own, with the same name
        assertNotSame(fileOf(firstEditor), fileOf(secondEditor));
        assertFalse("Each source reference has a path of its own",
                fileOf(firstEditor).getPath().equals(fileOf(secondEditor).getPath()));
        // and the first frame, shown again, shows its text, which its file kept
        var firstAgainEditor = show(stack, first, true, FIRST, FIRST_LINE);
        assertTrue(textAtCaret(firstAgainEditor).startsWith("var first = 1;"));
        assertEquals(SECOND, document(fileOf(secondEditor)).getText());

        // The DAP server is asked once for the text of each source
        assertEquals(List.of(FIRST_REFERENCE, SECOND_REFERENCE), List.copyOf(server.sources));
        assertFalse("The document stays read-only", firstAgainEditor.getDocument().isWritable());
    }

    public void testAJumpToAnotherSourceOfTheSameNameOpensItInItsOwnTabAndTheExecutionPointKeepsItsText()
            throws Exception {
        debug();
        var stack = session.getSuspendContext().getActiveExecutionStack();
        assertNotNull(stack);
        var first = framesOf(stack).get(0);
        var firstFile = fileOf(show(stack, first, true, FIRST, FIRST_LINE));

        // While the first frame is the current one, Jump to Source on a variable declared in the second source, of the
        // same name, opens its declaration in the text of the second source
        var declaration = jumpToSource(local("second"));
        assertEquals(NAME, declaration.getFile().getName());
        declaration.createNavigatable(getProject()).navigate(true);
        var jumpEditor = waitForEditor(declaration.getFile(), SECOND, SECOND_LINE);
        assertTrue(textAtCaret(jumpEditor).startsWith("second = 2;"));

        // The tab of the frame keeps the text of its source: the jump opened another one
        assertEquals("The text of the current frame's source", FIRST, document(firstFile).getText());
        assertNotSame(firstFile, declaration.getFile());
        // and Show Execution Point shows the frame in it, at its line
        session.showExecutionPoint();
        var executionPointEditor = show(stack, first, true, FIRST, FIRST_LINE);
        assertEquals(firstFile, fileOf(executionPointEditor));
        assertTrue(textAtCaret(executionPointEditor).startsWith("var first = 1;"));
    }

    public void testAFrameWhichIsNotShownLoadsItsTextInItsOwnFile() throws Exception {
        debug();
        var stack = session.getSuspendContext().getActiveExecutionStack();
        assertNotNull(stack);
        var frames = framesOf(stack);
        var first = frames.get(0);
        var second = frames.get(1);
        var editor = show(stack, first, true, FIRST, FIRST_LINE);

        // The Frames view asks for the positions of all frames, to list them
        var position = second.getSourcePosition();
        assertNotNull(position);
        assertEquals(NAME, position.getFile().getName());
        assertNotSame(fileOf(editor), position.getFile());
        // The position resolves its line once the debug adapter has answered the text of its source
        PlatformTestUtil.waitWithEventsDispatching(() -> "the file of the second frame didn't get the text of its source"
                + " at line " + (SECOND_LINE - 1) + ", its position is at line " + position.getLine() + " and it has:\n"
                + document(position.getFile()).getText(),
                () -> SECOND.equals(document(position.getFile()).getText()) && position.getLine() == SECOND_LINE - 1,
                BOUND_SECONDS);

        // The frame shown keeps its text
        assertEquals(FIRST, editor.getDocument().getText());
        assertEquals(List.of(FIRST_REFERENCE, SECOND_REFERENCE), List.copyOf(server.sources));
    }

    /**
     * Starts debugging, attached to a new {@link EvalServer}, and waits for the program to stop.
     */
    private void debug() throws Exception {
        server = new EvalServer();
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("evals", type.getConfigurationFactories()[0]);
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
     * Shows the given frame, as the Frames view does when the user selects it: the frame becomes the current one, and
     * its source position is opened in an editor. Waits until the editor shows the given text, with the caret at the
     * start of the given line, and returns that editor.
     */
    private @NotNull Editor show(@NotNull XExecutionStack stack, @NotNull XStackFrame frame, boolean isTopFrame,
                                 @NotNull String text, int line) {
        session.setCurrentStackFrame(stack, frame, isTopFrame);
        XSourcePosition position = frame.getSourcePosition();
        assertNotNull(position);
        position.createNavigatable(getProject()).navigate(true);
        var editor = waitForEditor(position.getFile(), text, line);
        assertEquals(0, editor.getCaretModel().getLogicalPosition().column);
        return editor;
    }

    /**
     * Waits until the selected editor shows the given file, with the given text, and the caret on the given line, and
     * returns that editor.
     */
    private @NotNull Editor waitForEditor(@NotNull VirtualFile file, @NotNull String text, int line) {
        PlatformTestUtil.waitWithEventsDispatching(() -> "the line " + line + " of '" + file.getPath() + "' isn't shown "
                + "in the text of its source, the editor shows " + describe(selectedEditor()), () -> {
            var editor = selectedEditor();
            return editor != null && file.equals(fileOf(editor)) && text.equals(editor.getDocument().getText())
                    && editor.getCaretModel().getLogicalPosition().line == line - 1;
        }, BOUND_SECONDS);
        return selectedEditor();
    }

    private @Nullable Editor selectedEditor() {
        return FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
    }

    private static @NotNull VirtualFile fileOf(@NotNull Editor editor) {
        var file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        assertNotNull(file);
        return file;
    }

    private static @NotNull Document document(@NotNull VirtualFile file) {
        var document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        return document;
    }

    private static @NotNull String describe(@Nullable Editor editor) {
        return editor == null ? "nothing" : "'" + fileOf(editor).getPath() + "' at "
                + editor.getCaretModel().getLogicalPosition() + ", with the text:\n" + editor.getDocument().getText();
    }

    private static @NotNull String textAtCaret(@NotNull Editor editor) {
        return editor.getDocument().getText().substring(editor.getCaretModel().getOffset());
    }

    /**
     * Returns the source position which "Jump to Source" opens for the given value.
     */
    private static @NotNull XSourcePosition jumpToSource(@NotNull XValue value) {
        var given = new CompletableFuture<XSourcePosition>();
        XNavigatable navigatable = given::complete;
        value.computeSourcePosition(navigatable);
        PlatformTestUtil.waitWithEventsDispatching("Jump to Source gave no position", given::isDone, BOUND_SECONDS);
        var position = given.join();
        assertNotNull("Jump to Source found no position", position);
        return position;
    }

    /**
     * Returns the value of the given name in the locals of the current frame, as the Variables view gets it.
     */
    private @NotNull XValue local(@NotNull String name) {
        XValueContainer locals = children(session.getCurrentStackFrame()).getBottomGroups().get(0);
        var children = children(locals);
        return IntStream.range(0, children.size())
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
        PlatformTestUtil.waitWithEventsDispatching("the DAP server gave no children", done::isDone, BOUND_SECONDS);
        return done.join();
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

    /**
     * A DAP server of a program stopped in a script which another one evaluated: the stack has a frame in each, at the
     * line 2 of the first and the line 3 of the second. The DAP server gives their texts by source reference, under the
     * same name, {@code <anonymous>}. The local 'second' of the first frame is declared in the second script.
     */
    private static class EvalServer implements IDebugProtocolServer {

        private final ServerSocket serverSocket;
        // The source references of the 'source' requests, in the order received
        private final List<Integer> sources = new CopyOnWriteArrayList<>();
        private volatile IDebugProtocolClient client;

        EvalServer() throws IOException {
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
            }, "Eval DAP server");
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
            var response = new StackTraceResponse();
            response.setStackFrames(new StackFrame[]{
                    frame(1, "first", FIRST_REFERENCE, FIRST_LINE),
                    frame(2, "second", SECOND_REFERENCE, SECOND_LINE)
            });
            response.setTotalFrames(2);
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
            var second = new Variable();
            second.setName("second");
            second.setValue("2");
            second.setVariablesReference(0);
            second.setDeclarationLocationReference(SECOND_DECLARATION);
            var response = new VariablesResponse();
            response.setVariables(args.getVariablesReference() == LOCALS_REFERENCE ? new Variable[]{second} : new Variable[0]);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<LocationsResponse> locations(LocationsArguments args) {
            if (args.getLocationReference() != SECOND_DECLARATION) {
                return CompletableFuture.completedFuture(null);
            }
            // 'second' in 'var second = 2;'
            var location = new LocationsResponse();
            location.setSource(source(SECOND_REFERENCE));
            location.setLine(SECOND_LINE);
            location.setColumn(5);
            return CompletableFuture.completedFuture(location);
        }

        @Override
        public CompletableFuture<SourceResponse> source(SourceArguments args) {
            sources.add(args.getSourceReference());
            var response = new SourceResponse();
            response.setContent(args.getSourceReference() == FIRST_REFERENCE ? FIRST : SECOND);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }

        private static Source source(int sourceReference) {
            var source = new Source();
            source.setName(NAME);
            source.setSourceReference(sourceReference);
            return source;
        }

        private static StackFrame frame(int id, @NotNull String name, int sourceReference, int line) {
            var frame = new StackFrame();
            frame.setId(id);
            frame.setName(name);
            frame.setSource(source(sourceReference));
            frame.setLine(line);
            return frame;
        }
    }
}
