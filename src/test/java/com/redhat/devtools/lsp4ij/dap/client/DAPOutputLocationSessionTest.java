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
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleTokenUtil;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.LocationsArguments;
import org.eclipse.lsp4j.debug.LocationsResponse;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.eclipse.lsp4j.debug.OutputEventArgumentsGroup;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceArguments;
import org.eclipse.lsp4j.debug.SourceResponse;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The source location of the DAP 'output' events, against a DAP server in the test's process which the session
 * attaches to: the debug console shows it as a link after the output, before the line end which ends the output, and
 * the link opens that location: the local file of a source's path, or the text of a source reference, at the line and
 * column of the event, or the location which the DAP server answers to 'locations' for the event's location reference,
 * which is asked for only when the link is clicked, and only until the program runs. An output without a location is
 * printed as before.
 */
public class DAPOutputLocationSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final String ESC = "\u001b";
    // The location reference which the DAP server resolves to a function in the local file, and a source reference
    private static final int ON_CLICK_LOCATION = 6;
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
    private OutputServer server;
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

    public void testTheLinkOfAPathOpensTheLocalFileAtTheLineAndColumn() throws Exception {
        debug();

        server.output("set up\n", args -> {
            args.setSource(handlersSource());
            args.setLine(3);
            args.setColumn(22);
        });

        // The name of the source and the line, after the text, and before the line end
        int start = waitForConsoleLine("set up", "set up handlers.js:3");
        assertNull("The text of the output is no link", hyperlinkAt(start));
        var link = hyperlinkAt(start + "set up ".length());
        assertNotNull("The link of the location", link);
        assertSame("The link covers its text", link, hyperlinkAt(start + "set up handlers.js:".length()));

        link.navigate(getProject());

        // The file is opened at the line and column of the event, which start at 1, as LSP4IJ asks for them
        var editor = waitForEditor(handlersFile());
        assertEquals(new LogicalPosition(2, 21), editor.getCaretModel().getLogicalPosition());
        assertTrue(textAtCaret(editor).startsWith("function onClick"));
        assertEquals(List.of(), notifications);
    }

    public void testTheLinkOfASourceReferenceOpensTheTextOfTheSource() throws Exception {
        debug();

        server.output("generated\n", args -> {
            args.setSource(generatedSource());
            args.setLine(2);
            args.setColumn(17);
        });

        int start = waitForConsoleLine("generated", "generated generated.js:2");
        var link = hyperlinkAt(start + "generated ".length());
        assertNotNull("The link of the location", link);

        link.navigate(getProject());

        assertEquals("The DAP server is asked for the text of the source", Integer.valueOf(GENERATED_SOURCE_REFERENCE),
                PlatformTestUtil.waitForFuture(server.nextSource(), BOUND_SECONDS * 1000L));
        PlatformTestUtil.waitWithEventsDispatching("the text of the source is opened at the line and column", () -> {
            var editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
            var file = editor != null ? fileOf(editor) : null;
            return file != null && "generated.js".equals(file.getName()) && textAtCaret(editor).startsWith("() => 42");
        }, BOUND_SECONDS);
        var editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        assertEquals(GENERATED, editor.getDocument().getText());
        assertEquals(new LogicalPosition(1, 16), editor.getCaretModel().getLogicalPosition());
        assertEquals(List.of(), notifications);
    }

    public void testTheLinkOfALocationReferenceAsksForTheLocationWhenItIsClicked() throws Exception {
        debug();

        server.output("function onClick\n", args -> args.setLocationReference(ON_CLICK_LOCATION));

        String linkText = DAPBundle.message("dap.output.valueSource");
        int start = waitForConsoleLine("function onClick", "function onClick " + linkText);
        assertEquals("The DAP server isn't asked for the location until the link is clicked", List.of(),
                List.copyOf(server.locations));
        var link = hyperlinkAt(start + "function onClick ".length());
        assertNotNull("The link of the location reference", link);

        link.navigate(getProject());

        assertEquals(Integer.valueOf(ON_CLICK_LOCATION), PlatformTestUtil.waitForFuture(server.nextLocation(), BOUND_SECONDS * 1000L));
        var editor = waitForEditor(handlersFile());
        assertEquals(new LogicalPosition(2, 21), editor.getCaretModel().getLogicalPosition());
        assertEquals(List.of(), notifications);
    }

    public void testTheLinkOfALocationReferenceSendsNoRequestOnceTheProgramHasRun() throws Exception {
        debug();

        server.output("function onClick\n", args -> args.setLocationReference(ON_CLICK_LOCATION));
        // The program runs, so that the reference of the output is out of date. The output after it says that the
        // client has received the 'continued' event, which comes before it
        server.continued();
        server.output("running\n", args -> {
        });

        int start = waitForConsoleLine("function onClick", "function onClick " + DAPBundle.message("dap.output.valueSource"));
        waitForConsoleLine("running", "running");
        var link = hyperlinkAt(start + "function onClick ".length());
        assertNotNull("The link of the location reference", link);

        link.navigate(getProject());

        var expected = DAPBundle.message("dap.output.location.failed", DAPBundle.message("dap.output.location.stale"));
        PlatformTestUtil.waitWithEventsDispatching(() -> "the out of date reference is reported, not: " + notifications,
                () -> notifications.contains(expected), BOUND_SECONDS);
        assertEquals("No 'locations' request names a reference of an earlier execution", List.of(),
                List.copyOf(server.locations));
        assertNull("No file is opened", FileEditorManager.getInstance(getProject()).getSelectedTextEditor());
    }

    public void testOnceTheSessionHasEndedOnlyTheLinkOfAPathOpens() throws Exception {
        debug();
        server.output("generated\n", args -> {
            args.setSource(generatedSource());
            args.setLine(2);
        });
        server.output("set up\n", args -> {
            args.setSource(handlersSource());
            args.setLine(3);
            args.setColumn(22);
        });
        var generated = hyperlinkAt(waitForConsoleLine("generated", "generated generated.js:2") + "generated ".length());
        assertNotNull("The link of the source reference", generated);
        var setUp = hyperlinkAt(waitForConsoleLine("set up", "set up handlers.js:3") + "set up ".length());
        assertNotNull("The link of the path", setUp);

        // The console stays once the session has ended
        session.stop();
        PlatformTestUtil.waitWithEventsDispatching("the session ends", () -> session.isStopped(), BOUND_SECONDS);

        // A source reference is valid only in its session
        generated.navigate(getProject());
        var expected = DAPBundle.message("dap.output.location.ended");
        PlatformTestUtil.waitWithEventsDispatching(() -> "the ended session is reported, not: " + notifications,
                () -> notifications.contains(expected), BOUND_SECONDS);
        assertEquals("No 'source' request is sent once the session has ended", List.of(), List.copyOf(server.sources));

        // A local file can still be opened
        setUp.navigate(getProject());
        var editor = waitForEditor(handlersFile());
        assertEquals(new LogicalPosition(2, 21), editor.getCaretModel().getLogicalPosition());
    }

    public void testAnOutputWithoutALocationIsPrintedAsBefore() throws Exception {
        debug();

        server.output("plain\n", args -> {
        });

        int start = waitForConsoleLine("plain", "plain");
        var console = (ConsoleViewImpl) session.getConsoleView();
        int line = console.getEditor().getDocument().getLineNumber(start);
        assertEquals("The line of the output has no link", List.of(), console.getHyperlinks().findAllHyperlinksOnLine(line));
    }

    public void testTheLinkFollowsTheIndentedAndColouredOutputOfAGroup() throws Exception {
        debug();

        server.output("Header\n", args -> args.setGroup(OutputEventArgumentsGroup.START));
        server.output(ESC + "[31mred" + ESC + "[0m\n", args -> {
            args.setSource(handlersSource());
            args.setLine(3);
        });
        server.output("", args -> args.setGroup(OutputEventArgumentsGroup.END));

        // The group indents the line, whose colour is decoded, and the link follows its text
        int start = waitForConsoleLine(DAPOutputGroups.INDENT + "red", DAPOutputGroups.INDENT + "red handlers.js:3");
        int red = start + DAPOutputGroups.INDENT.length();
        var expected = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(ConsoleHighlighter.RED).getForegroundColor();
        assertNotNull("The colour scheme has a red foreground", expected);
        for (int offset = red; offset < red + "red".length(); offset++) {
            var contentType = contentTypeAt(offset);
            assertNotNull("The content type of the red text at " + offset, contentType);
            var foreground = contentType.getAttributes().getForegroundColor();
            assertNotNull("The foreground of the red text at " + offset, foreground);
            assertEquals("The foreground of the red text at " + offset, expected.getRGB(), foreground.getRGB());
        }
        assertNotNull("The link of the location", hyperlinkAt(red + "red ".length()));
    }

    /**
     * Starts debugging, attached to a new {@link OutputServer}, and waits until the session is configured.
     */
    private void debug() throws Exception {
        server = new OutputServer(handlers);
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("output locations", type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
                        .buildAndExecute());
        PlatformTestUtil.waitWithEventsDispatching("the debug session starts and is configured", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && server.configured.isDone();
        }, BOUND_SECONDS);
    }

    private @NotNull Source handlersSource() {
        var source = new Source();
        source.setName("handlers.js");
        source.setPath(handlers.toString());
        return source;
    }

    /**
     * Returns a source whose text the DAP server gives, for the source reference 11.
     */
    private static @NotNull Source generatedSource() {
        var source = new Source();
        source.setName("generated.js");
        // a path which isn't a local file: the text is the DAP server's
        source.setPath("<generated>/generated.js");
        source.setSourceReference(GENERATED_SOURCE_REFERENCE);
        return source;
    }

    /**
     * Waits until the console shows an ended line which starts with the given prefix, the text of an output, checks
     * that the line is the expected one, and returns its offset in the console.
     */
    private int waitForConsoleLine(@NotNull String prefix, @NotNull String expected) {
        PlatformTestUtil.waitWithEventsDispatching(() -> "the console shows a line which starts with \"" + prefix + "\", not:\n" + getConsoleText(),
                () -> findLine(getConsoleText(), prefix) >= 0, BOUND_SECONDS);
        String text = getConsoleText();
        int start = findLine(text, prefix);
        assertEquals("The console line", expected, text.substring(start, text.indexOf('\n', start)));
        return start;
    }

    /**
     * Returns the offset of the first line of the given text which starts with the given prefix and is ended, or -1 if
     * there is none.
     */
    private static int findLine(@NotNull String text, @NotNull String prefix) {
        for (int start = 0, end = text.indexOf('\n'); end >= 0; start = end + 1, end = text.indexOf('\n', start)) {
            if (text.startsWith(prefix, start)) {
                return start;
            }
        }
        return -1;
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
     * Returns the link which the console shows at the given offset, or null if it shows none there.
     */
    private @Nullable HyperlinkInfo hyperlinkAt(int offset) {
        return ((ConsoleViewImpl) session.getConsoleView()).getHyperlinks().getHyperlinkAt(offset);
    }

    /**
     * Returns the content type of the text which the console printed at the given offset, from the markup of its
     * editor, or null if it printed none there.
     */
    private @Nullable ConsoleViewContentType contentTypeAt(int offset) {
        var console = (ConsoleViewImpl) session.getConsoleView();
        var document = console.getEditor().getDocument();
        return Arrays.stream(DocumentMarkupModel.forDocument(document, getProject(), true).getAllHighlighters())
                .filter(highlighter -> highlighter.getStartOffset() <= offset && offset < highlighter.getEndOffset())
                .map(ConsoleTokenUtil::getTokenType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Waits until the given file is opened in the selected editor, and returns that editor.
     */
    private @NotNull Editor waitForEditor(@NotNull VirtualFile file) {
        PlatformTestUtil.waitWithEventsDispatching(() -> "the file " + file.getName() + " is opened, not "
                        + Arrays.toString(FileEditorManager.getInstance(getProject()).getOpenFiles()) + ", " + notifications,
                () -> {
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
     * A DAP server of a program with one thread, 'main', which sends the 'output' events that the test asks for. It
     * resolves the location reference 6 to the function 'onClick' in a local file, and gives the text of the source
     * reference 11.
     */
    private static class OutputServer implements IDebugProtocolServer {

        private final Path handlers;
        private final ServerSocket serverSocket;
        private final CompletableFuture<Void> configured = new CompletableFuture<>();
        private final LinkedBlockingQueue<Integer> locations = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<Integer> sources = new LinkedBlockingQueue<>();
        private volatile IDebugProtocolClient client;

        OutputServer(@NotNull Path handlers) throws IOException {
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
            }, "Output location DAP server");
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
         * Sends a stdout 'output' event with the given output, and what the given configurer sets, such as a location.
         */
        void output(@NotNull String output, @NotNull Consumer<OutputEventArguments> configurer) {
            var args = new OutputEventArguments();
            args.setCategory(OutputEventArgumentsCategory.STDOUT);
            args.setOutput(output);
            configurer.accept(args);
            client.output(args);
        }

        /**
         * Says that the program continued, as after a stop.
         */
        void continued() {
            var args = new ContinuedEventArguments();
            args.setThreadId(1);
            args.setAllThreadsContinued(true);
            client.continued(args);
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
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> client.initialized());
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> configured.complete(null));
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
        public CompletableFuture<LocationsResponse> locations(LocationsArguments args) {
            locations.add(args.getLocationReference());
            if (args.getLocationReference() != ON_CLICK_LOCATION) {
                return CompletableFuture.completedFuture(null);
            }
            var source = new Source();
            source.setName("handlers.js");
            source.setPath(handlers.toString());
            var location = new LocationsResponse();
            location.setSource(source);
            location.setLine(3);
            location.setColumn(22);
            return CompletableFuture.completedFuture(location);
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
    }
}
