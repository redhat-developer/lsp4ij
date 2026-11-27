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
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.PrintOutput;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import com.redhat.devtools.lsp4ij.installation.download.LogRecorder;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Each run of an exec task leaves a trace, also when it succeeds: its console, as the {@code Installer} tab, shows the
 * command, the directory it runs in, the PATH in which it is looked up, its output and its exit code, and the IDE's log
 * has at INFO the command, the directory and the exit code on one line, and the PATH (#2296). The log has the output at
 * INFO only when the task fails, cut to its start and its end when it is long: the output of a task which succeeds
 * stays at DEBUG, since it can hold a secret that the log doesn't recognise (#2156).
 */
public class ExecTaskTraceTest extends BasePlatformTestCase {

    private static final String LOGGED = "INFO Installer 'Test server': ";
    // Without a project and a workingDir, a command runs in the directory of the IDE
    private static final String IDE_DIRECTORY = Path.of("").toAbsolutePath() + " (the directory of the IDE)";

    public void testACommandWhichSucceedsShowsItsDirectoryPathOutputAndExitCodeAndLogsThemButItsOutputAtDebugOnly()
            throws Exception {
        Path workingDir = Files.createTempDirectory("lsp4ij-exec-trace");
        try {
            List<String> command = printOutput("version-1.2.3", "path");
            var trace = run(exec(command, workingDir));

            assertTrue(trace.log.output(), trace.succeeded);
            String path = trace.printedPath();
            String commandText = commandText(command);
            // As the macros of a workingDir are expanded
            String directory = Path.of(CommandUtils.resolveCommandLine(workingDir.toString(), null)).toAbsolutePath()
                    .toString();
            // The console
            trace.assertPrinted("> " + commandText);
            trace.assertPrinted("Working directory: " + directory);
            trace.assertPrinted("PATH: " + path);
            trace.assertPrinted("version-1.2.3");
            trace.assertPrinted("Exit code: 0");
            // The log
            trace.assertLogged(LOGGED + "the command exited with 0, in " + directory + ": " + commandText);
            trace.assertLogged(LOGGED + "the PATH of the command: " + ExecTask.loggedPath(path));
            trace.assertNoOutputAtInfo();
            assertTrue("The output is logged at DEBUG: " + trace.log.output(), trace.log.lines().stream()
                    .anyMatch(line -> line.startsWith("FINE Installer 'Test server': version-1.2.3")));
        } finally {
            NioFiles.deleteRecursively(workingDir);
        }
    }

    public void testThePathIsLoggedWithoutASecretWhichTheCutWouldSplit() {
        // A GitHub token which starts just before the end of the start that the cut keeps: cut first, its start, ghp_
        // and six characters, would be too short for the redaction to recognise
        String token = "ghp_" + "A".repeat(36);
        String path = "x".repeat(ExecTask.maxLoggedLength() / 2 - 10) + File.pathSeparator + token + File.pathSeparator
                + "y".repeat(ExecTask.maxLoggedLength());

        String logged = ExecTask.loggedPath(path);

        // The cut can split the <redacted> which replaced the token, but no part of the token itself is left
        assertFalse("No part of the token is logged: " + logged, logged.contains("ghp_") || logged.contains("AAAAA"));
        assertTrue("The PATH is cut: " + logged.length(), logged.length() < path.length());
        assertEquals("none", ExecTask.loggedPath(null));
    }

    public void testTheLoggedLengthIsTheSystemPropertyOrElseTheEnvironmentVariableOrElse4000() {
        assertEquals(4000, ExecTask.DEFAULT_MAX_LOGGED_LENGTH);
        assertEquals("Neither is set", 4000, ExecTask.maxLoggedLength(null, null));
        assertEquals("The environment variable", 800, ExecTask.maxLoggedLength(null, "800"));
        assertEquals("The system property", 600, ExecTask.maxLoggedLength(" 600 ", "800"));
        assertEquals("A system property which isn't a number", 800, ExecTask.maxLoggedLength("many", "800"));
        assertEquals("Neither is a positive number", 4000, ExecTask.maxLoggedLength("0", "-5"));
        assertEquals("Blank values", 4000, ExecTask.maxLoggedLength("", " "));
    }

    public void testACommandWhichFailsShowsItsExitCodeAndOutputAndLogsThemWithItsOutput() throws Exception {
        List<String> command = printOutput("found-the-wrong-tool", "err:broken-installation", "exit:3");
        var trace = run(exec(command, null));

        assertFalse(trace.log.output(), trace.succeeded);
        String commandText = commandText(command);
        trace.assertPrinted("Working directory: " + IDE_DIRECTORY);
        trace.assertPrinted("Exit code: 3");
        trace.assertLogged(LOGGED + "the command exited with 3, in " + IDE_DIRECTORY + ": " + commandText);
        trace.assertLogged(LOGGED + "the standard output of the command:\nfound-the-wrong-tool");
        // The JVM can say on its standard error that it picked up JAVA_TOOL_OPTIONS
        assertTrue(trace.log.output(), trace.log.lines().stream().anyMatch(line ->
                line.startsWith(LOGGED + "the standard error of the command:\n") && line.endsWith("broken-installation")));
        assertTrue(trace.log.output(), trace.log.lines().contains(LOGGED + "the step 'Install the server' failed"));
    }

    public void testAnOutputWhichDoesNotMatchFailsTheTaskAndIsLogged() throws Exception {
        List<String> command = printOutput("version-1.2.3");
        var exec = exec(command, null);
        exec.getAsJsonObject("exec").addProperty("outputMatches", "version-2\\.");
        var trace = run(exec);

        assertFalse(trace.log.output(), trace.succeeded);
        trace.assertLogged(LOGGED + "the command exited with 0, in " + IDE_DIRECTORY + ": " + commandText(command));
        trace.assertLogged(LOGGED + "the standard output of the command:\nversion-1.2.3");
    }

    public void testAStandardErrorWhichFailsTheTaskIsLogged() throws Exception {
        List<String> command = printOutput("err:a-warning");
        var exec = exec(command, null);
        exec.getAsJsonObject("exec").addProperty("ignoreStderr", false);
        var trace = run(exec);

        assertFalse(trace.log.output(), trace.succeeded);
        trace.assertLogged(LOGGED + "the command exited with 0, in " + IDE_DIRECTORY + ": " + commandText(command));
        assertTrue(trace.log.output(), trace.log.lines().stream().anyMatch(line ->
                line.startsWith(LOGGED + "the standard error of the command:\n") && line.endsWith("a-warning")));
    }

    public void testACommandWhichDoesNotStartShowsAndLogsItsDirectoryAndPath() throws Exception {
        var trace = run(exec(List.of("lsp4ij-no-such-program"), null));

        assertFalse(trace.log.output(), trace.succeeded);
        assertTrue(trace.printed.toString(), trace.printed.stream().anyMatch(line -> line.startsWith("PATH: ")));
        trace.assertLogged(LOGGED + "the command didn't start, in " + IDE_DIRECTORY + ": lsp4ij-no-such-program");
        assertTrue(trace.log.output(), trace.log.lines().stream()
                .anyMatch(line -> line.startsWith(LOGGED + "the PATH of the command: ")));
        assertFalse(trace.log.output(), trace.log.output().contains("exited with"));
    }

    public void testTheLogGetsTheStartAndTheEndOfALongOutputOfAFailedTaskAndTheConsoleAllOfIt() throws Exception {
        String longLine = "start-" + "x".repeat(10_000) + "-end";
        List<String> command = printOutput(longLine, "exit:1");
        var trace = run(exec(command, null));

        assertFalse(trace.log.output(), trace.succeeded);
        // The output can come in several parts
        assertTrue("The console must show the whole output", String.join("", trace.printed).contains(longLine));
        int half = ExecTask.maxLoggedLength() / 2;
        trace.assertLogged(LOGGED + "the standard output of the command:\n" + longLine.substring(0, half)
                + "\n[... " + (longLine.length() - 2 * half) + " characters left out ...]\n"
                + longLine.substring(longLine.length() - half));
        assertEquals("A text which isn't longer than the limit is logged as it is", "short", ExecTask.truncate("short"));
    }

    /**
     * Runs the given exec task as the run of the installer {@code Test server}, without a project, as the installer
     * of a language server which no client started, and returns what it printed and logged.
     */
    private static @NotNull Trace run(@NotNull JsonObject exec) {
        var installer = new JsonObject();
        installer.addProperty("name", "Test server");
        installer.add("run", exec);
        ServerInstallerDescriptor descriptor = ServerInstallerManager.getInstance().loadInstaller(installer);
        var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
        context.setShowNotification(false);
        var printed = new CopyOnWriteArrayList<String>();
        // What the context prints goes to its progress indicator too, as to its consoles
        context.setProgressIndicator(new EmptyProgressIndicator() {
            @Override
            public void setText2(@Nullable String text) {
                if (text != null) {
                    printed.add(text);
                }
            }
        });
        try (var log = new LogRecorder()) {
            boolean succeeded = ServerInstallerManager.getInstance().install(descriptor, context);
            return new Trace(succeeded, printed, log);
        }
    }

    /**
     * Returns the exec task {@code Install the server}, which runs the given command in the given working directory,
     * or without one, and ignores its standard error, where the JVM can say that it picked up JAVA_TOOL_OPTIONS.
     */
    private static @NotNull JsonObject exec(@NotNull List<String> command, @Nullable Path workingDir) {
        var exec = new JsonObject();
        exec.addProperty("name", "Install the server");
        var array = new JsonArray();
        command.forEach(array::add);
        exec.add("command", array);
        if (workingDir != null) {
            exec.addProperty("workingDir", workingDir.toString());
        }
        exec.addProperty("ignoreStderr", true);
        var task = new JsonObject();
        task.add("exec", exec);
        return task;
    }

    /**
     * Returns the given command as the console and the log show it, with its macros expanded.
     */
    private static @NotNull String commandText(@NotNull List<String> command) {
        return command.stream()
                .map(arg -> CommandUtils.resolveCommandLine(arg, null))
                .collect(Collectors.joining(" "));
    }

    /**
     * Returns the command which runs {@link PrintOutput} with the given arguments.
     */
    private static @NotNull List<String> printOutput(@NotNull String... args) throws Exception {
        String java = ProcessHandle.current().info().command().orElseThrow();
        // The directory of the compiled test classes, above the folders of the package of PrintOutput
        Path classes = Path.of(PrintOutput.class.getResource("PrintOutput.class").toURI());
        for (int i = PrintOutput.class.getName().split("\\.").length; i > 0; i--) {
            classes = classes.getParent();
        }
        return Stream.concat(Stream.of(java, "-cp", classes.toString(), PrintOutput.class.getName()), Stream.of(args))
                .toList();
    }

    /**
     * What a run of an installer printed in its consoles, each print as it came, and wrote to the IDE's log, and
     * whether it succeeded.
     */
    private record Trace(boolean succeeded, @NotNull List<String> printed, @NotNull LogRecorder log) {

        void assertPrinted(@NotNull String line) {
            // The output of a command comes with its line separators, a line or more at a time
            assertTrue("The console must show '" + line + "': " + printed,
                    printed.stream().flatMap(String::lines).anyMatch(printedLine -> printedLine.strip().equals(line)));
        }

        void assertLogged(@NotNull String record) {
            assertTrue("The log must have '" + record + "':\n" + log.output(), log.lines().contains(record));
        }

        void assertNoOutputAtInfo() {
            assertFalse("The output of a task which succeeds must not be logged at INFO:\n" + log.output(),
                    log.lines().stream().anyMatch(line -> line.startsWith(LOGGED + "the standard output of the command:")
                            || line.startsWith(LOGGED + "the standard error of the command:")));
        }

        /**
         * Returns the PATH which {@link PrintOutput} printed, as the process got it.
         */
        @NotNull String printedPath() {
            return printed.stream()
                    .flatMap(String::lines)
                    .filter(line -> line.startsWith("PATH="))
                    .map(line -> line.substring("PATH=".length()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("The command must print its PATH: " + printed));
        }
    }
}
