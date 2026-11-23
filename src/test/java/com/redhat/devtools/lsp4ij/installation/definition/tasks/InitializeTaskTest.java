/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskRegistry;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the {@code initialize} task of an installer counts a language server or a debug adapter as installed only
 * once it answers {@code initialize}, that it ends the server it started then, and that it leaves no process behind
 * (#1957).
 */
public class InitializeTaskTest {

    // Long enough for a JVM to start on a slow machine, for the servers which answer
    private static final int ANSWER_TIMEOUT = 30_000;
    private static final int NO_ANSWER_TIMEOUT = 1_500;
    private static final String TRACE = "Test debug adapter listening at: ${address}:${port}";

    private Path record;

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    @Before
    public void createRecord() throws Exception {
        record = Files.createTempFile("lsp4ij-initialize", ".txt");
        Files.delete(record);
    }

    @After
    public void deleteRecord() throws Exception {
        Files.deleteIfExists(record);
    }

    // Language servers

    @Test
    public void testALanguageServerWhichAnswersInitializeIsInstalledAndGetsShutdownAndExit() throws Exception {
        var context = context(languageServer(command("lsp", "answer")));

        assertTrue(check(ANSWER_TIMEOUT, context), "the language server answers initialize: " + context.printed);
        assertEquals(List.of("initialize", "shutdown", "exit"), received());
        assertTrue(context.printed.contains("The server answered initialize (test-server 1.0)."), context.printed.toString());
        assertGone();
    }

    @Test
    public void testALanguageServerWhichAnswersInitializeWithAnErrorIsInstalledAndTheConsoleSaysSo() throws Exception {
        var context = context(languageServer(command("lsp", "error")));

        assertTrue(check(ANSWER_TIMEOUT, context), "an error shows that the language server runs: " + context.printed);
        assertTrue(context.printed.stream().anyMatch(line -> line.contains("answered initialize with an error")
                && line.contains("test error")), context.printed.toString());
        assertEquals(List.of("initialize", "shutdown", "exit"), received());
        assertGone();
    }

    @Test
    public void testALanguageServerWhichNeverAnswersIsNotInstalledAfterTheTimeoutAndIsDestroyed() throws Exception {
        var context = context(languageServer(command("lsp", "silent")));

        long start = System.nanoTime();
        assertFalse(check(NO_ANSWER_TIMEOUT, context), "a language server which doesn't answer isn't installed");
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= NO_ANSWER_TIMEOUT, "it waits for the timeout");
        assertEquals(List.of("initialize"), received());
        assertTrue(context.printed.contains("The server didn't answer initialize within " + NO_ANSWER_TIMEOUT + " ms."),
                context.printed.toString());
        assertGone();
    }

    @Test
    public void testALanguageServerWhichEndsAtOnceIsNotInstalledWithoutWaitingForTheTimeout() throws Exception {
        var context = context(languageServer(command("lsp", "exit")));

        long start = System.nanoTime();
        assertFalse(check(ANSWER_TIMEOUT, context), "a language server which ends isn't installed");
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < ANSWER_TIMEOUT, "it doesn't wait for the timeout");
        assertGone();
    }

    @Test
    public void testACommandWhichSendsBackWhatItGetsIsNotALanguageServer() throws Exception {
        var context = context(languageServer(command("lsp", "echo")));

        assertFalse(check(ANSWER_TIMEOUT, context), "the error which comes back is the task's own: " + context.printed);
        assertTrue(context.printed.stream().anyMatch(line -> line.contains("as cat does")), context.printed.toString());
        assertGone();
    }

    @Test
    public void testACommandWhichDoesNotExistIsNotInstalled() {
        var context = context(languageServer("lsp4ij-no-such-language-server --stdio"));

        assertFalse(check(ANSWER_TIMEOUT, context), "a command which doesn't exist isn't installed");
        assertTrue(context.printed.stream().anyMatch(line -> line.startsWith("The server can't be started")),
                context.printed.toString());
    }

    @Test
    public void testALanguageServerWhichDoesNotEndOnceAskedToIsDestroyed() throws Exception {
        var context = context(languageServer(command("lsp", "stay")));

        assertTrue(check(ANSWER_TIMEOUT, context), "the language server answers initialize: " + context.printed);
        assertEquals(List.of("initialize", "shutdown", "exit"), received());
        assertGone();
    }

    @Test
    public void testTheLanguageServerIsDestroyedWhenTheInstallationIsCancelled() throws Exception {
        var context = context(languageServer(command("lsp", "silent")));
        var indicator = new EmptyProgressIndicator();
        context.setProgressIndicator(indicator);
        var canceller = Executors.newSingleThreadScheduledExecutor();
        try {
            canceller.schedule(indicator::cancel, 1, TimeUnit.SECONDS);
            long start = System.nanoTime();
            assertThrows(ProcessCanceledException.class, () -> check(ANSWER_TIMEOUT, context));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < ANSWER_TIMEOUT, "the cancellation ends the wait");
        } finally {
            canceller.shutdownNow();
        }
        assertGone();
    }

    @Test
    public void testALanguageServerWhichLSP4IJStartsOverASocketIsCheckedOverTheSocket() throws Exception {
        var server = languageServer(command("lsp", "answer", "listen", UserDefinedTransport.PORT_MACRO));
        server.setTransport(UserDefinedTransport.of(UserDefinedTransport.Mode.START_AND_CONNECT, null, 0));
        var context = context(server);

        assertTrue(check(ANSWER_TIMEOUT, context), "the language server answers initialize over the socket: " + context.printed);
        assertEquals(List.of("initialize", "shutdown", "exit"), received());
        assertGone();
    }

    @Test
    public void testALanguageServerWhichLSP4IJStartsAndWhichConnectsToLSP4IJIsCheckedOverTheSocket() throws Exception {
        var server = languageServer(command("lsp", "answer", "connect", UserDefinedTransport.PORT_MACRO));
        server.setTransport(UserDefinedTransport.of(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0));
        var context = context(server);

        assertTrue(check(ANSWER_TIMEOUT, context), "the language server answers initialize over the socket: " + context.printed);
        assertEquals(List.of("initialize", "shutdown", "exit"), received());
        assertGone();
    }

    @Test
    public void testALanguageServerWhichRunsAlreadyIsNeitherStartedNorEnded() {
        var server = languageServer(command("lsp", "answer"));
        server.setTransport(UserDefinedTransport.of(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 9));
        var context = context(server);

        assertTrue(check(NO_ANSWER_TIMEOUT, context), "LSP4IJ doesn't start it, so it doesn't check it");
        assertFalse(Files.exists(record), "the command doesn't run");
    }

    @Test
    public void testAnInstallerWhichDoesNotKnowHowTheServerStartsCannotCheckIt() {
        var context = context(new CommandLineUpdater() {
            @Override
            public String getCommandLine() {
                return command("lsp", "answer");
            }

            @Override
            public void setCommandLine(String commandLine) {
            }
        });

        assertFalse(check(NO_ANSWER_TIMEOUT, context), "the task doesn't know what to start");
        assertFalse(Files.exists(record), "the command doesn't run");
    }

    // Debug adapters

    @Test
    public void testADebugAdapterWhichAnswersInitializeIsInstalledAndGetsDisconnect() throws Exception {
        var context = context(debugAdapter(command("dap", "answer"), null));

        assertTrue(check(ANSWER_TIMEOUT, context), "the debug adapter answers initialize: " + context.printed);
        assertEquals(List.of("initialize", "disconnect"), received());
        assertGone();
    }

    @Test
    public void testADebugAdapterWhichAnswersInitializeWithAnErrorIsInstalled() throws Exception {
        var context = context(debugAdapter(command("dap", "error"), null));

        assertTrue(check(ANSWER_TIMEOUT, context), "an error shows that the debug adapter runs: " + context.printed);
        assertTrue(context.printed.stream().anyMatch(line -> line.contains("answered initialize with an error")),
                context.printed.toString());
        assertGone();
    }

    @Test
    public void testADebugAdapterWhichPrintsWhereItListensIsCheckedOverASocket() throws Exception {
        var context = context(debugAdapter(command("dap", "answer", "listen", "0", "print"), TRACE));

        assertTrue(check(ANSWER_TIMEOUT, context), "the debug adapter answers initialize over the socket: " + context.printed);
        assertEquals(List.of("initialize", "disconnect"), received());
        assertGone();
    }

    @Test
    public void testADebugAdapterWhichListensOnThePortOfItsCommandIsCheckedOverASocket() throws Exception {
        var context = context(debugAdapter(command("dap", "answer", "listen", "${port}"), null));

        assertTrue(check(ANSWER_TIMEOUT, context), "the debug adapter answers initialize over the socket: " + context.printed);
        assertEquals(List.of("initialize", "disconnect"), received());
        assertGone();
    }

    @Test
    public void testADebugAdapterWhichNeverAnswersIsNotInstalledAfterTheTimeoutAndIsDestroyed() throws Exception {
        var context = context(debugAdapter(command("dap", "silent"), null));

        assertFalse(check(NO_ANSWER_TIMEOUT, context), "a debug adapter which doesn't answer isn't installed");
        assertEquals(List.of("initialize"), received());
        assertGone();
    }

    @Test
    public void testADebugAdapterWhichNeverPrintsWhereItListensIsNotInstalledAndIsDestroyed() throws Exception {
        var context = context(debugAdapter(command("dap", "silent"), TRACE));

        assertFalse(check(NO_ANSWER_TIMEOUT, context), "a debug adapter which never listens isn't installed");
        assertTrue(context.printed.contains("The server didn't start within " + NO_ANSWER_TIMEOUT + " ms."),
                context.printed.toString());
        assertGone();
    }

    @Test
    public void testACommandWhichSendsBackWhatItGetsIsNotADebugAdapter() throws Exception {
        var context = context(debugAdapter(command("dap", "echo"), null));

        assertFalse(check(ANSWER_TIMEOUT, context), "the error which comes back is the task's own: " + context.printed);
        assertGone();
    }

    @Test
    public void testADebugAdapterWithoutCommandIsNotInstalled() {
        var context = context(debugAdapter("", null));

        assertFalse(check(NO_ANSWER_TIMEOUT, context), "a debug adapter without command can't be started");
    }

    private static boolean check(int timeout, @NotNull InstallerContext context) {
        var descriptor = new ServerInstallerDescriptor("test", false, new InstallerTaskRegistry());
        return new InitializeTask(null, "Check that the server starts", null, null, timeout, descriptor).run(context);
    }

    private static @NotNull UserDefinedLanguageServerDefinition languageServer(@NotNull String command) {
        return new UserDefinedLanguageServerDefinition("initialize-test", "Initialize test", null, command, Map.of(),
                true, null, null);
    }

    private static @NotNull UserDefinedDebugAdapterServerDefinition debugAdapter(@NotNull String command,
                                                                                 @Nullable String debugServerReadyPattern) {
        var server = new UserDefinedDebugAdapterServerDefinition("initialize-test", "Initialize test", command, List.of(),
                List.of());
        server.setIncludeSystemEnvironmentVariables(true);
        server.setDebugServerReadyPattern(debugServerReadyPattern);
        return server;
    }

    private static @NotNull RecordingContext context(@NotNull CommandLineUpdater server) {
        var context = new RecordingContext();
        context.setCommandLineUpdater(server);
        return context;
    }

    private @NotNull String command(@NotNull String protocol, @NotNull String behavior, @NotNull String... args) {
        return InitializeTestServer.commandLine(protocol, behavior, record, args);
    }

    /**
     * Returns the methods or commands which the test server got.
     */
    private @NotNull List<String> received() throws Exception {
        return Files.readAllLines(record).stream()
                .filter(line -> !line.startsWith("pid "))
                .toList();
    }

    /**
     * Asserts that the process of the test server is gone.
     */
    private void assertGone() throws Exception {
        long pid = Files.readAllLines(record).stream()
                .filter(line -> line.startsWith("pid "))
                .map(line -> Long.parseLong(line.substring("pid ".length())))
                .findFirst()
                .orElseThrow();
        // The task waits for the process to end: give the system a moment to reap it
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < end) {
            Thread.sleep(20);
        }
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "the process " + pid + " must be gone");
    }

    /**
     * A context of the check of an installer which keeps what the task prints.
     */
    private static final class RecordingContext extends InstallerContext {

        private final List<String> printed = new CopyOnWriteArrayList<>();

        RecordingContext() {
            super(null, InstallerAction.CHECK);
        }

        @Override
        public void print(@Nullable String message, @NotNull ConsoleViewContentType contentType) {
            if (message != null) {
                printed.add(message);
            }
            super.print(message, contentType);
        }
    }
}
