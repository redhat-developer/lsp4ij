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
import com.intellij.execution.impl.ConsoleTokenUtil;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
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
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * The ANSI escape sequences, such as colours and bold, in the output of the DAP 'output' events, against a DAP server
 * in the test's process which the session attaches to. The client says in 'initialize' that it supports them, and the
 * debug console shows their style instead of their escape characters, as a process console does: a sequence which
 * an event splits, and a style, continue in the next event of the same category.
 */
public class DAPOutputAnsiStylingSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final String ESC = "\u001b";

    private OutputServer server;
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

    public void testTheInitializeRequestSaysThatTheClientSupportsAnsiStyling() throws Exception {
        debug();

        var arguments = server.initializeArguments;
        assertNotNull("The DAP server got the arguments of 'initialize'", arguments);
        assertEquals("supportsANSIStyling in the arguments of 'initialize'", Boolean.TRUE, arguments.getSupportsANSIStyling());
    }

    public void testTheConsoleShowsTheColourOfTheOutputWithoutItsEscapeSequences() throws Exception {
        debug();

        server.output(OutputEventArgumentsCategory.STDOUT, ESC + "[31mred" + ESC + "[0m plain\n");

        int start = waitForConsoleLine(" plain", "red plain");
        assertForeground("the red text", ConsoleHighlighter.RED, start, "red");
        assertSame("The content type of the text after the reset, that of stdout",
                ConsoleViewContentType.LOG_INFO_OUTPUT, contentTypeAt(start + "red ".length()));
    }

    public void testTheColourOfTheOutputOfAGroupIsShownWhereTheGroupIndentsIt() throws Exception {
        debug();

        server.output(OutputEventArgumentsCategory.STDOUT, "Header\n", OutputEventArgumentsGroup.START);
        server.output(OutputEventArgumentsCategory.STDOUT, ESC + "[31mred" + ESC + "[0m inside\n", null);
        server.output(OutputEventArgumentsCategory.STDOUT, "", OutputEventArgumentsGroup.END);

        // The group indents the line (#2167), whose escape sequences are decoded after the indentation
        int start = waitForConsoleLine(" inside", DAPOutputGroups.INDENT + "red inside");
        assertForeground("the red text", ConsoleHighlighter.RED, start + DAPOutputGroups.INDENT.length(), "red");
    }

    public void testAnEscapeSequenceWhichAnOutputEventSplitsIsDecodedWithTheNextEvent() throws Exception {
        debug();

        // The sequence of green is split, and the text it colours is in two events
        server.output(OutputEventArgumentsCategory.STDOUT, ESC + "[3");
        server.output(OutputEventArgumentsCategory.STDOUT, "2mgre");
        server.output(OutputEventArgumentsCategory.STDOUT, "en" + ESC + "[0m done\n");

        int start = waitForConsoleLine(" done", "green done");
        assertForeground("the green text", ConsoleHighlighter.GREEN, start, "green");
        assertSame("The content type of the text after the reset, that of stdout",
                ConsoleViewContentType.LOG_INFO_OUTPUT, contentTypeAt(start + "green ".length()));
    }

    public void testTheStderrOutputKeepsItsContentTypeWhereItHasNoAnsiStyle() throws Exception {
        debug();

        server.output(OutputEventArgumentsCategory.STDERR, ESC + "[1mbold" + ESC + "[0m error\n");

        int start = waitForConsoleLine(" error", "bold error");
        var bold = contentTypeAt(start);
        assertNotNull("The bold text has a content type", bold);
        assertEquals("The font of the bold text", Font.BOLD, bold.getAttributes().getFontType());
        assertSame("The content type of the text after the reset, that of stderr",
                ConsoleViewContentType.LOG_ERROR_OUTPUT, contentTypeAt(start + "bold ".length()));
    }

    public void testAStyleOfOneCategoryDoesNotContinueInAnother() throws Exception {
        debug();

        // stdout stays blue, and the console output that comes in between has a style of its own
        server.output(OutputEventArgumentsCategory.STDOUT, ESC + "[34mblue ");
        server.output(OutputEventArgumentsCategory.CONSOLE, "console\n");

        int start = waitForConsoleLine("console", "blue console");
        assertForeground("the blue stdout text", ConsoleHighlighter.BLUE, start, "blue");
        assertSame("The content type of the console output, which has no style",
                ConsoleViewContentType.LOG_INFO_OUTPUT, contentTypeAt(start + "blue ".length()));
    }

    /**
     * Starts debugging, attached to a new {@link OutputServer}, and waits until the session is configured.
     */
    private void debug() throws Exception {
        server = new OutputServer();
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("ansi output", type.getConfigurationFactories()[0]);
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

    /**
     * Waits until the console shows a line which ends with the given suffix, checks that the line is the expected
     * one, and returns its offset in the console.
     */
    private int waitForConsoleLine(@NotNull String suffix, @NotNull String expected) {
        PlatformTestUtil.waitWithEventsDispatching(() -> "the console shows a line which ends with \"" + suffix + "\", not:\n" + getConsoleText(),
                () -> getConsoleText().lines().anyMatch(line -> line.endsWith(suffix)), BOUND_SECONDS);
        String text = getConsoleText();
        String line = text.lines().filter(l -> l.endsWith(suffix)).findFirst().orElseThrow();
        assertEquals("The console line, without escape sequences", expected, line);
        assertFalse("The console shows no escape character:\n" + text, text.contains(ESC));
        return text.indexOf(line + "\n");
    }

    /**
     * Checks that each character of the given text, at the given offset in the console, has the foreground of the
     * given ANSI colour.
     */
    private void assertForeground(@NotNull String what,
                                  @NotNull TextAttributesKey ansiColour,
                                  int start,
                                  @NotNull String text) {
        var expected = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(ansiColour).getForegroundColor();
        assertNotNull("The colour scheme has a foreground for " + ansiColour, expected);
        for (int offset = start; offset < start + text.length(); offset++) {
            var contentType = contentTypeAt(offset);
            assertNotNull("The content type of " + what + " at " + offset, contentType);
            var foreground = contentType.getAttributes().getForegroundColor();
            assertNotNull("The foreground of " + what + " at " + offset, foreground);
            assertEquals("The foreground of " + what + " at " + offset + ", in " + contentType,
                    expected.getRGB(), foreground.getRGB());
        }
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
     * A DAP server of a program with one thread, 'main', which sends the 'output' events that the test asks for.
     */
    private static class OutputServer implements IDebugProtocolServer {

        private final ServerSocket serverSocket;
        private final CompletableFuture<Void> configured = new CompletableFuture<>();
        private volatile @Nullable InitializeRequestArguments initializeArguments;
        private volatile IDebugProtocolClient client;

        OutputServer() throws IOException {
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
            }, "Output DAP server");
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
         * Sends an 'output' event with the given category and output.
         */
        void output(@NotNull String category, @NotNull String output) {
            output(category, output, null);
        }

        void output(@NotNull String category, @NotNull String output, @Nullable OutputEventArgumentsGroup group) {
            var args = new OutputEventArguments();
            args.setCategory(category);
            args.setOutput(output);
            args.setGroup(group);
            client.output(args);
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            initializeArguments = args;
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
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
