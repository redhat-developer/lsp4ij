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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XFullValueEvaluator.XFullValueEvaluationCallback;
import com.intellij.xdebugger.frame.XInlineDebuggerDataCallback;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
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
import org.eclipse.lsp4j.debug.VariablePresentationHint;
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
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * "Jump to Source" on a variable in a debug session, against a DAP server in the test's process which the session
 * attaches to: on a variable with a 'declarationLocationReference', it asks the DAP server where the declaration is,
 * with the 'locations' request, and opens the source of the answer, a local file or the text of a source reference, at
 * its line and column. Without a reference, when the DAP server fails to answer or answers no location, and for a value
 * of an earlier stop, whose reference is no longer valid, it searches the name of the variable in the file of the frame,
 * as it did before, and as the inline values do. On a lazy variable whose value is shown, it opens the declaration of
 * that value, or else that of the lazy variable.
 */
public class DAPJumpToDeclarationSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final int FRAME_ID = 7;
    private static final int LOCALS_REFERENCE = 1;
    // The declarations which the DAP server gives: one which it locates in the frame's file, one in the text of a source
    // reference, one in the frame's file without a line, one which it fails to locate, and one which it locates nowhere
    private static final int TOTAL_DECLARATION = 5;
    private static final int ORDER_DECLARATION = 6;
    private static final int TAX_DECLARATION = 7;
    private static final int ITEM_DECLARATION = 9;
    private static final int DISCOUNT_DECLARATION = 10;
    private static final int GENERATED_SOURCE_REFERENCE = 11;
    // Another source of the same name, generated.js, with another text
    private static final int OTHER_GENERATED_SOURCE_REFERENCE = 12;
    // The declarations of two lazy variables, and that of the value of the second: the value of the first has none
    private static final int SUBTOTAL_DECLARATION = 13;
    private static final int AVERAGE_DECLARATION = 14;
    private static final int AVERAGE_VALUE_DECLARATION = 15;
    // The variablesReferences which give the values of the lazy variables
    private static final int SUBTOTAL_VALUE_REFERENCE = 2;
    private static final int AVERAGE_VALUE_REFERENCE = 3;

    private static final String SUM = """
            // Sums the items of an order
            function sum(items, discount) {
                let total = 0;
                for (const item of items) {
                    total += item;
                }
                return total - discount;
            }
            """;
    // The line where the program stops, 1-based as the DAP server gives it: the search of a name ends with this line
    private static final int FRAME_LINE = 7;
    private static final String GENERATED = """
            // generated
            var order = {};
            """;
    // The text of the other source of the same name
    private static final String OTHER_GENERATED = """
            // generated again
            var total = 1;
            """;

    private VirtualFile sumFile;
    private DeclarationServer server;
    private XDebugSession session;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var directory = Files.createDirectories(Path.of(FileUtil.getTempDirectory()));
        var sum = Files.createTempFile(directory, "sum", ".js").toRealPath();
        Files.writeString(sum, SUM);
        sumFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sum);
        assertNotNull(sumFile);
        // The name of a variable is searched in the editor of the frame's file, which the user sees when the program
        // stops
        var editor = FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), sumFile), false);
        assertNotNull(editor);
        EditorTestUtil.waitForLoading(editor);
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

    public void testJumpToSourceOpensTheDeclarationWhichTheDAPServerLocates() throws Exception {
        debug();
        var position = jumpToSource(local("total"));

        // the DAP server is asked where the declaration is
        assertEquals(List.of(TOTAL_DECLARATION), List.copyOf(server.locations));
        // and the position is that of the answer, whose line and column start at 1, as LSP4IJ asks for them
        assertEquals(sumFile, position.getFile());
        assertEquals(2, position.getLine());
        // which Jump to Source opens, as IntelliJ opens it, on the EDT
        position.createNavigatable(getProject()).navigate(true);
        var editor = waitForCaret(new LogicalPosition(2, 8));
        assertTrue(textAtCaret(editor).startsWith("total = 0"));
    }

    public void testJumpToSourceOpensADeclarationInTheTextOfASourceReference() throws Exception {
        debug();
        var position = jumpToSource(local("order"));

        assertEquals(List.of(ORDER_DECLARATION), List.copyOf(server.locations));
        // the text of the source is the DAP server's, even if the source has a path, and the position is given once
        // the DAP server gave it
        assertEquals(List.of(GENERATED_SOURCE_REFERENCE), List.copyOf(server.sources));
        assertEquals("generated.js", position.getFile().getName());
        assertEquals(1, position.getLine());
        position.createNavigatable(getProject()).navigate(true);
        var editor = waitForCaret(new LogicalPosition(1, 4));
        assertEquals(GENERATED, editor.getDocument().getText());
        assertTrue(textAtCaret(editor).startsWith("order = {}"));
    }

    public void testJumpToSourceOpensTheFirstLineOfADeclarationWithoutALine() throws Exception {
        debug();
        var position = jumpToSource(local("tax"));

        assertEquals(List.of(TAX_DECLARATION), List.copyOf(server.locations));
        assertEquals(sumFile, position.getFile());
        assertEquals(0, position.getLine());
        assertEquals(0, position.getOffset());
    }

    public void testJumpToSourceWithoutADeclarationSearchesTheNameInTheFileOfTheFrame() throws Exception {
        debug();
        var position = jumpToSource(local("items"));

        assertEquals("A variable without a 'declarationLocationReference' asks the DAP server nothing",
                List.of(), List.copyOf(server.locations));
        // where the name is last found up to the line of the frame
        assertEquals(sumFile, position.getFile());
        assertEquals(3, position.getLine());
    }

    public void testJumpToSourceSearchesTheNameWhenTheDAPServerFailsToLocateTheDeclaration() throws Exception {
        debug();
        var position = jumpToSource(local("item"));

        assertEquals(List.of(ITEM_DECLARATION), List.copyOf(server.locations));
        assertEquals(sumFile, position.getFile());
        assertEquals(4, position.getLine());
    }

    public void testJumpToSourceSearchesTheNameWhenTheDAPServerLocatesTheDeclarationNowhere() throws Exception {
        debug();
        var position = jumpToSource(local("discount"));

        assertEquals(List.of(DISCOUNT_DECLARATION), List.copyOf(server.locations));
        assertEquals(sumFile, position.getFile());
        assertEquals(FRAME_LINE - 1, position.getLine());
    }

    public void testJumpToSourceSearchesTheNameWhenTheDAPServerGivesNoTextForTheSourceOfTheDeclaration() throws Exception {
        debug();
        server.declaresTotalInTheGeneratedSource = true;
        server.givesSourceText = false;

        var position = jumpToSource(local("total"));

        assertEquals(List.of(TOTAL_DECLARATION), List.copyOf(server.locations));
        assertEquals(List.of(GENERATED_SOURCE_REFERENCE), List.copyOf(server.sources));
        // not the generated source, whose document would be empty, but where the name is last found up to the line of
        // the frame
        assertEquals(sumFile, position.getFile());
        assertEquals(FRAME_LINE - 1, position.getLine());
    }

    public void testJumpToSourceOpensTheSameSourceReferenceTwice() throws Exception {
        debug();
        var first = jumpToSource(local("order"));
        var second = jumpToSource(local("order"));

        assertEquals(List.of(ORDER_DECLARATION, ORDER_DECLARATION), List.copyOf(server.locations));
        assertEquals(List.of(GENERATED_SOURCE_REFERENCE, GENERATED_SOURCE_REFERENCE), List.copyOf(server.sources));
        // the second load finds the document read-only, with the text of the same source, which it doesn't write again
        assertEquals("generated.js", second.getFile().getName());
        assertEquals(first.getFile(), second.getFile());
        assertEquals(1, second.getLine());
    }

    public void testJumpToSourceOpensADeclarationInAnotherSourceOfTheSameName() throws Exception {
        debug();
        var first = jumpToSource(local("order"));
        assertEquals("generated.js", first.getFile().getName());
        // The declaration of 'total' is in another source of the same name, with another text, which has a file, and
        // an editor tab, of its own
        server.declaresTotalInTheGeneratedSource = true;
        server.totalSourceReference = OTHER_GENERATED_SOURCE_REFERENCE;

        var position = jumpToSource(local("total"));

        assertEquals(List.of(GENERATED_SOURCE_REFERENCE, OTHER_GENERATED_SOURCE_REFERENCE), List.copyOf(server.sources));
        assertEquals("generated.js", position.getFile().getName());
        assertNotSame(first.getFile(), position.getFile());
        assertEquals(1, position.getLine());
        position.createNavigatable(getProject()).navigate(true);
        var editor = waitForCaret(new LogicalPosition(1, 4));
        assertEquals(OTHER_GENERATED, editor.getDocument().getText());
        assertTrue(textAtCaret(editor).startsWith("total = 1"));

        // and the file of the first source keeps its text
        assertEquals(GENERATED, FileDocumentManager.getInstance().getDocument(first.getFile()).getText());
        var again = jumpToSource(local("order"));
        assertEquals(first.getFile(), again.getFile());
        assertEquals(GENERATED, FileDocumentManager.getInstance().getDocument(again.getFile()).getText());
    }

    public void testJumpToSourceSearchesTheNameWhenTheDAPServerDoesNotAnswer() throws Exception {
        debug();
        var total = local("total");
        server.answersLocations = false;
        // A wait shorter than the 3 seconds for a user leaves the search of the name nearly all of IntelliJ's 10 seconds,
        // however slow the runner is
        var timeout = Duration.ofMillis(100);
        total.setDeclarationTimeout(timeout);

        long start = System.nanoTime();
        var position = jumpToSource(total);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // The 'locations' request goes out asynchronously, and the search of the name doesn't wait until it has reached
        // the DAP server: on a slow runner, the jump can end first
        PlatformTestUtil.waitWithEventsDispatching("the DAP server got no 'locations' request",
                () -> !server.locations.isEmpty(), BOUND_SECONDS);
        assertEquals(List.of(TOTAL_DECLARATION), List.copyOf(server.locations));
        // where the name is last found up to the line of the frame, once the wait for the DAP server ends
        assertEquals(sumFile, position.getFile());
        assertEquals(FRAME_LINE - 1, position.getLine());
        assertTrue("The name is searched after the wait for the DAP server, within IntelliJ's 10 seconds: " + millis + " ms",
                millis >= timeout.toMillis() && millis < TimeUnit.SECONDS.toMillis(10));
    }

    public void testJumpToSourceOnAValueOfAnEarlierStopSendsNoRequestAndSearchesTheName() throws Exception {
        debug();
        var total = local("total");
        var firstStop = session.getSuspendContext();
        // The program runs and stops again, as after a step, and the value of the first stop is still at hand
        server.stopAgain();
        PlatformTestUtil.waitWithEventsDispatching("the session shows the next stop",
                () -> session.isSuspended() && session.getSuspendContext() != firstStop, BOUND_SECONDS);

        var position = jumpToSource(total);

        assertEquals("No 'locations' request names a reference of the earlier stop", List.of(), List.copyOf(server.locations));
        assertEquals(sumFile, position.getFile());
        assertEquals(FRAME_LINE - 1, position.getLine());
    }

    public void testTheInlineValueOfAVariableWithADeclarationIsAtItsName() throws Exception {
        debug();
        var total = local("total");
        var given = new CompletableFuture<XSourcePosition>();
        assertEquals(ThreeState.YES, total.computeInlineDebuggerData(new XInlineDebuggerDataCallback() {
            @Override
            public void computed(XSourcePosition position) {
                given.complete(position);
            }
        }));
        PlatformTestUtil.waitWithEventsDispatching("no inline position of 'total' was given", given::isDone, BOUND_SECONDS);

        assertEquals("The inline values ask the DAP server nothing", List.of(), List.copyOf(server.locations));
        assertEquals(sumFile, given.join().getFile());
        assertEquals(FRAME_LINE - 1, given.join().getLine());
    }

    public void testJumpToSourceOnALazyVariableOpensItsDeclarationAfterShowValueWhenItsValueHasNone() throws Exception {
        debug();
        var subtotal = local("subtotal");
        assertEquals("Before \"Show value\", the declaration of the lazy variable is opened", 1,
                jumpToSource(subtotal).getLine());

        showValue(subtotal);
        assertEquals("42", subtotal.getShownVariable().getValue());
        var position = jumpToSource(subtotal);

        // the value which the lazy variable shows has no declaration: the DAP server is asked for that of the lazy
        // variable, rather than the name being searched
        assertEquals(List.of(SUBTOTAL_DECLARATION, SUBTOTAL_DECLARATION), List.copyOf(server.locations));
        assertEquals(sumFile, position.getFile());
        assertEquals(1, position.getLine());
    }

    public void testJumpToSourceOnALazyVariableOpensTheDeclarationOfItsValueAfterShowValue() throws Exception {
        debug();
        var average = local("average");
        showValue(average);

        var position = jumpToSource(average);

        // the declaration of the value which the lazy variable shows, rather than that of the lazy variable
        assertEquals(List.of(AVERAGE_VALUE_DECLARATION), List.copyOf(server.locations));
        assertEquals(sumFile, position.getFile());
        assertEquals(5, position.getLine());
    }

    /**
     * Starts debugging, attached to a new {@link DeclarationServer}, and waits for the program to stop.
     */
    private void debug() throws Exception {
        server = new DeclarationServer(Path.of(sumFile.getPath()));
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("sum", type.getConfigurationFactories()[0]);
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
     * Returns the source position which "Jump to Source" opens for the given value, asked for on the EDT, as IntelliJ
     * asks for it, and checks that it is given in a read action (#2005, #2006).
     */
    private static @NotNull XSourcePosition jumpToSource(@NotNull DAPValue value) {
        var given = new CompletableFuture<XSourcePosition>();
        XNavigatable navigatable = position -> {
            if (ApplicationManager.getApplication().isReadAccessAllowed()) {
                given.complete(position);
            } else {
                given.completeExceptionally(new AssertionError("The position of '" + value.getName()
                        + "' is given outside a read action"));
            }
        };
        value.computeSourcePosition(navigatable);
        PlatformTestUtil.waitWithEventsDispatching("Jump to Source gave no position of '" + value.getName() + "'",
                given::isDone, BOUND_SECONDS);
        return given.join();
    }

    /**
     * Asks for the value of the given lazy variable, as a click on its "Show value" link in the Variables view does, and
     * waits until it is shown.
     */
    private void showValue(@NotNull DAPValue value) {
        var tree = new XDebuggerTree(getProject(), new DAPDebuggerEditorsProvider(null, null), null,
                XDebuggerActions.VARIABLES_TREE_POPUP_GROUP, null);
        Disposer.register(getTestRootDisposable(), tree);
        var node = new XValueNodeImpl(tree, null, value.getName(), value);
        PlatformTestUtil.waitWithEventsDispatching("the node of '" + value.getName() + "' shows no \"Show value\" link",
                () -> node.getFullValueEvaluator() != null, BOUND_SECONDS);
        var evaluated = new CompletableFuture<String>();
        node.getFullValueEvaluator().startEvaluation((XFullValueEvaluationCallback) Proxy.newProxyInstance(
                XFullValueEvaluationCallback.class.getClassLoader(), new Class<?>[]{XFullValueEvaluationCallback.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "evaluated" -> evaluated.complete((String) args[0]);
                    case "errorOccurred" -> evaluated.completeExceptionally(new AssertionError(args[0]));
                    case "isObsolete" -> false;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                }));
        PlatformTestUtil.waitForFuture(evaluated, BOUND_SECONDS * 1000L);
    }

    /**
     * Waits until the caret of the editor of the frame's file is at the given position, and returns that editor.
     */
    private @NotNull Editor waitForCaret(@NotNull LogicalPosition caret) {
        PlatformTestUtil.waitWithEventsDispatching(() -> "the caret didn't move to " + caret + ", it is at "
                + caretOf(selectedEditor()), () -> {
            var editor = selectedEditor();
            return editor != null && caret.equals(editor.getCaretModel().getLogicalPosition());
        }, BOUND_SECONDS);
        return selectedEditor();
    }

    private @Nullable Editor selectedEditor() {
        return FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
    }

    private static @Nullable LogicalPosition caretOf(@Nullable Editor editor) {
        return editor != null ? editor.getCaretModel().getLogicalPosition() : null;
    }

    private static @NotNull String textAtCaret(@NotNull Editor editor) {
        return editor.getDocument().getText().substring(editor.getCaretModel().getOffset());
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
        PlatformTestUtil.waitWithEventsDispatching("the DAP server gave no children", done::isDone, BOUND_SECONDS);
        return done.join();
    }

    /**
     * A DAP server of a program stopped in the frame 7, at the line 7 of a local file, whose locals each have a
     * 'declarationLocationReference', but one: 'total', whose declaration the DAP server locates in the file, 'order',
     * whose declaration it locates in the text of a source reference, 'tax', whose declaration it locates in the file
     * without a line, 'item', whose declaration it fails to locate, 'discount', whose declaration it locates nowhere,
     * and 'items', without a 'declarationLocationReference'. Two more are lazy, and declared in the file: 'subtotal',
     * whose value has no 'declarationLocationReference', and 'average', whose value has its own.
     */
    private static class DeclarationServer implements IDebugProtocolServer {

        private final Path sum;
        private final ServerSocket serverSocket;
        // The location references of the 'locations' requests, and the source references of the 'source' requests, in
        // the order received
        private final List<Integer> locations = new CopyOnWriteArrayList<>();
        private final List<Integer> sources = new CopyOnWriteArrayList<>();
        // false to leave the 'locations' requests unanswered, as a DAP server which hangs
        private volatile boolean answersLocations = true;
        // true to locate the declaration of 'total' in the text of the generated source, not in the frame's file
        private volatile boolean declaresTotalInTheGeneratedSource;
        // The source reference of that generated source
        private volatile int totalSourceReference = GENERATED_SOURCE_REFERENCE;
        // false to answer the 'source' requests without a body, so without the text of the source
        private volatile boolean givesSourceText = true;
        private volatile IDebugProtocolClient client;

        DeclarationServer(@NotNull Path sum) throws IOException {
            this.sum = sum;
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
            }, "Declaration DAP server");
            accept.setDaemon(true);
            accept.start();
        }

        /**
         * Returns the generated source, whose text the DAP server gives for its source reference, as it has a path
         * which isn't a local file.
         */
        private static Source generatedSource(int sourceReference) {
            var source = new Source();
            source.setName("generated.js");
            source.setPath("<generated>/generated.js");
            source.setSourceReference(sourceReference);
            return source;
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
            return CompletableFuture.completedFuture(null).thenRun(this::stopAgain);
        }

        /**
         * Says that the program stopped, at once after it is attached, and again when the test says so, as after a
         * step: the references of the values of an earlier stop are then out of date.
         */
        void stopAgain() {
            var stopped = new StoppedEventArguments();
            stopped.setReason(StoppedEventArgumentsReason.PAUSE);
            stopped.setThreadId(1);
            stopped.setAllThreadsStopped(true);
            client.stopped(stopped);
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
            frame.setName("sum");
            frame.setSource(source());
            frame.setLine(FRAME_LINE);
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
                case LOCALS_REFERENCE -> new Variable[]{
                        variable("items", "Array(3)", null),
                        variable("discount", "5", DISCOUNT_DECLARATION),
                        variable("total", "42", TOTAL_DECLARATION),
                        variable("item", "17", ITEM_DECLARATION),
                        variable("order", "{}", ORDER_DECLARATION),
                        variable("tax", "0.2", TAX_DECLARATION),
                        lazyVariable("subtotal", SUBTOTAL_DECLARATION, SUBTOTAL_VALUE_REFERENCE),
                        lazyVariable("average", AVERAGE_DECLARATION, AVERAGE_VALUE_REFERENCE)
                };
                // the variables with the values of the lazy variables
                case SUBTOTAL_VALUE_REFERENCE -> new Variable[]{variable("subtotal", "42", null)};
                case AVERAGE_VALUE_REFERENCE -> new Variable[]{variable("average", "14", AVERAGE_VALUE_DECLARATION)};
                default -> new Variable[0];
            });
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<LocationsResponse> locations(LocationsArguments args) {
            int reference = args.getLocationReference();
            locations.add(reference);
            if (!answersLocations) {
                return new CompletableFuture<>();
            }
            if (reference == TOTAL_DECLARATION && declaresTotalInTheGeneratedSource) {
                var location = new LocationsResponse();
                location.setSource(generatedSource(totalSourceReference));
                location.setLine(2);
                location.setColumn(5);
                return CompletableFuture.completedFuture(location);
            }
            return switch (reference) {
                case TOTAL_DECLARATION -> {
                    // 'total' in 'let total = 0;'
                    var location = new LocationsResponse();
                    location.setSource(source());
                    location.setLine(3);
                    location.setColumn(9);
                    yield CompletableFuture.completedFuture(location);
                }
                case ORDER_DECLARATION -> {
                    // 'order' in 'var order = {};'
                    var location = new LocationsResponse();
                    location.setSource(generatedSource(GENERATED_SOURCE_REFERENCE));
                    location.setLine(2);
                    location.setColumn(5);
                    yield CompletableFuture.completedFuture(location);
                }
                case TAX_DECLARATION -> {
                    var location = new LocationsResponse();
                    location.setSource(source());
                    yield CompletableFuture.completedFuture(location);
                }
                case SUBTOTAL_DECLARATION -> {
                    var location = new LocationsResponse();
                    location.setSource(source());
                    location.setLine(2);
                    location.setColumn(1);
                    yield CompletableFuture.completedFuture(location);
                }
                case AVERAGE_DECLARATION -> {
                    var location = new LocationsResponse();
                    location.setSource(source());
                    location.setLine(8);
                    location.setColumn(1);
                    yield CompletableFuture.completedFuture(location);
                }
                case AVERAGE_VALUE_DECLARATION -> {
                    var location = new LocationsResponse();
                    location.setSource(source());
                    location.setLine(6);
                    location.setColumn(5);
                    yield CompletableFuture.completedFuture(location);
                }
                case ITEM_DECLARATION -> CompletableFuture.failedFuture(new ResponseErrorException(
                        new ResponseError(ResponseErrorCode.InvalidParams, "No location for " + reference, null)));
                default -> CompletableFuture.completedFuture(null);
            };
        }

        @Override
        public CompletableFuture<SourceResponse> source(SourceArguments args) {
            sources.add(args.getSourceReference());
            if (!givesSourceText) {
                // an answer without a body
                return CompletableFuture.completedFuture(null);
            }
            var response = new SourceResponse();
            response.setContent(switch (args.getSourceReference()) {
                case GENERATED_SOURCE_REFERENCE -> GENERATED;
                case OTHER_GENERATED_SOURCE_REFERENCE -> OTHER_GENERATED;
                default -> "";
            });
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }

        private @NotNull Source source() {
            var source = new Source();
            source.setName(sum.getFileName().toString());
            source.setPath(sum.toString());
            return source;
        }

        private static Variable variable(@NotNull String name, @NotNull String value,
                                         @Nullable Integer declarationLocationReference) {
            var variable = new Variable();
            variable.setName(name);
            variable.setValue(value);
            variable.setVariablesReference(0);
            variable.setDeclarationLocationReference(declarationLocationReference);
            return variable;
        }

        /**
         * Returns a lazy variable, whose value the DAP server gives as the variable of the given variablesReference.
         */
        private static Variable lazyVariable(@NotNull String name, int declarationLocationReference,
                                             int variablesReference) {
            var variable = variable(name, "(...)", declarationLocationReference);
            var lazy = new VariablePresentationHint();
            lazy.setLazy(true);
            variable.setPresentationHint(lazy);
            variable.setVariablesReference(variablesReference);
            return variable;
        }
    }
}
