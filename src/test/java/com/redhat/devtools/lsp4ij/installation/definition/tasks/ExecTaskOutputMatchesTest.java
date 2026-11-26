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
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskRegistry;
import com.redhat.devtools.lsp4ij.installation.definition.PrintOutput;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exec task with {@code outputMatches} succeeds only when its command exits with 0 and its standard output matches
 * the regular expression somewhere; without it, as before, whatever its output. The commands run {@link PrintOutput},
 * which prints its arguments as {@code dotnet --list-sdks} prints its SDKs, each on a line, and exits with the code of
 * an argument {@code exit:N}. The tasks ignore the standard error, where the JVM can say that it picked up
 * {@code JAVA_TOOL_OPTIONS}.
 */
public class ExecTaskOutputMatchesTest {

    // A line of a .NET SDK 10 or later, as dotnet --list-sdks prints it
    private static final String SDK_10_OR_LATER = "(?m)^[1-9]\\d+\\.\\d+\\.\\d+\\s";

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    @Test
    public void testAnOutputWhichMatchesPasses() throws Exception {
        var context = new RecordingContext();
        assertTrue(run(SDK_10_OR_LATER, context, "8.0.404 [/usr/share/dotnet/sdk]", "10.0.100 [/usr/share/dotnet/sdk]"),
                "the second line matches: " + context.output());
        assertEquals(List.of(), context.errors());
    }

    @Test
    public void testAnOutputWhichDoesNotMatchFailsAndTheConsoleSaysWhy() throws Exception {
        var context = new RecordingContext();
        assertFalse(run(SDK_10_OR_LATER, context, "8.0.404 [C:\\Program Files\\dotnet\\sdk]",
                "9.0.300 [C:\\Program Files\\dotnet\\sdk]", "10.0.100-rc.2.25502.107 [C:\\Program Files\\dotnet\\sdk]"));
        assertEquals(1, context.errors().size(), "the console should say why the task fails: " + context.output());
        String error = context.errors().get(0);
        assertTrue(error.contains(PrintOutput.class.getName()) && error.contains("doesn't match " + SDK_10_OR_LATER),
                "the console should name the command and the regular expression: " + error);
        // The command has the lines that it prints as arguments: the warning ends where the output would start
        assertTrue(error.endsWith("doesn't match " + SDK_10_OR_LATER + ":"), "the warning of the IDE's log shouldn't "
                + "hold the output, which can hold a secret: " + error);
        assertTrue(context.failedOutput().contains("9.0.300"), "the console should show the output after the error, "
                + "logged at DEBUG only: " + context.output());
    }

    @Test
    public void testACommandWhichExitsWith0WithoutOutputFails() throws Exception {
        var context = new RecordingContext();
        assertFalse(run(SDK_10_OR_LATER, context), "a command which prints nothing doesn't match");
        assertEquals(1, context.errors().size(), context.output());
        assertTrue(context.errors().get(0).endsWith("it printed nothing"), context.errors().get(0));
    }

    @Test
    public void testACommandWhichFailsFailsAlsoWhenItsOutputMatches() throws Exception {
        var context = new RecordingContext();
        assertFalse(run(SDK_10_OR_LATER, context, "10.0.100 [/usr/share/dotnet/sdk]", "exit:1"),
                "the exit code of the command decides first");
        assertEquals(List.of(), context.errors(), "the output doesn't take part in a command which failed");
    }

    @Test
    public void testAnInvalidRegularExpressionFailsTheTaskWithoutRunningTheCommand() throws Exception {
        var context = new RecordingContext();
        assertFalse(run("(?m)^[1-9", context, "10.0.100 [/usr/share/dotnet/sdk]"));
        assertEquals(1, context.errors().size(), context.output());
        assertTrue(context.errors().get(0).contains("isn't a valid regular expression"), context.errors().get(0));
        assertFalse(context.output().contains("> "), "the command shouldn't run: " + context.output());
    }

    @Test
    public void testWithoutOutputMatchesTheExitCodeAloneDecides() throws Exception {
        assertTrue(run(null, new RecordingContext(), "anything"), "a command which exits with 0 passes");
        assertTrue(run(null, new RecordingContext()), "also without output");
        assertFalse(run(null, new RecordingContext(), "anything", "exit:1"), "a command which exits with 1 fails");
    }

    @Test
    public void testACommandWhichStillRunsAtItsTimeoutPassesOnlyIfWhatItPrintedMatches() throws Exception {
        // HoldFile prints nothing and runs until the test releases it, which it does only once the timeout of 500 ms
        // has ended the command, whatever the machine
        Path directory = Files.createTempDirectory("lsp4ij-exec");
        Path release = directory.resolve("release");
        try {
            var descriptor = new ServerInstallerDescriptor("test", false, new InstallerTaskRegistry());
            var context = new RecordingContext();
            assertFalse(new ExecTask(null, "Check the .NET SDK 10 or later", null, null,
                    java(HoldFile.class.getName(), directory.resolve("held").toString(), release.toString()), null,
                    500, true, SDK_10_OR_LATER, descriptor).run(context), "nothing printed until the timeout matches");
            assertEquals(1, context.errors().size(), context.output());
            // Another file: the program that the timeout ended couldn't delete its own
            assertTrue(new ExecTask(null, "Wait", null, null,
                    java(HoldFile.class.getName(), directory.resolve("held-again").toString(), release.toString()),
                    null, 500, true, descriptor).run(new RecordingContext()),
                    "without outputMatches, a command which still runs at its timeout passes");
        } finally {
            // Releases a program that outlived its command, then removes the files
            Files.createFile(release);
            NioFiles.deleteRecursively(directory);
        }
    }

    @Test
    public void testTheFactoryReadsOutputMatchesFromTheInstaller() throws Exception {
        JsonArray command = new JsonArray();
        printOutput("9.0.300 [/usr/share/dotnet/sdk]").forEach(command::add);
        JsonObject exec = new JsonObject();
        exec.addProperty("name", "Check the .NET SDK 10 or later");
        exec.add("command", command);
        exec.addProperty("ignoreStderr", true);
        exec.addProperty("outputMatches", SDK_10_OR_LATER);
        JsonObject check = new JsonObject();
        check.add("exec", exec);
        JsonObject installer = new JsonObject();
        installer.addProperty("name", "test");
        installer.add("check", check);
        var registry = new InstallerTaskRegistry();
        registry.registerFactory("exec", new ExecTaskFactory());
        var descriptor = registry.loadInstaller(installer.toString());

        var context = new RecordingContext();
        assertFalse(descriptor.getCheck().execute(context), "only an SDK 9 is there: " + context.output());
        assertTrue(context.output().contains("doesn't match " + SDK_10_OR_LATER), context.output());
    }

    /**
     * Runs an exec task of {@link PrintOutput} with the given lines and the given {@code outputMatches}, and returns
     * whether it succeeded.
     */
    private static boolean run(@Nullable String outputMatches, @NotNull InstallerContext context,
                               @NotNull String... lines) throws Exception {
        var descriptor = new ServerInstallerDescriptor("test", false, new InstallerTaskRegistry());
        var task = new ExecTask(null, "Check the .NET SDK 10 or later", null, null, printOutput(lines), null, null,
                true, outputMatches, descriptor);
        return task.run(context);
    }

    /**
     * Returns the command which runs {@link PrintOutput} with the given arguments.
     */
    private static @NotNull List<String> printOutput(@NotNull String... args) throws Exception {
        return java(PrintOutput.class.getName(), args);
    }

    /**
     * Returns the command which runs the given main class of the test classes with the given arguments.
     */
    private static @NotNull List<String> java(@NotNull String mainClass, @NotNull String... args) throws Exception {
        String java = ProcessHandle.current().info().command().orElseThrow();
        // The directory of the compiled test classes, above the folders of the package of PrintOutput
        Path classes = Path.of(PrintOutput.class.getResource("PrintOutput.class").toURI());
        for (int i = PrintOutput.class.getName().split("\\.").length; i > 0; i--) {
            classes = classes.getParent();
        }
        return Stream.concat(Stream.of(java, "-cp", classes.toString(), mainClass), Stream.of(args)).toList();
    }

    /**
     * Records what an exec task prints in its console, and its errors.
     */
    private static final class RecordingContext extends InstallerContext {

        private final List<String> output = new CopyOnWriteArrayList<>();
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private final List<String> failedOutput = new CopyOnWriteArrayList<>();

        RecordingContext() {
            super(null, InstallerAction.CHECK);
        }

        @Override
        public void print(@Nullable String message, @NotNull ConsoleViewContentType contentType,
                          @NotNull Level logLevel) {
            if (message != null) {
                output.add(message);
                // The standard error of the command, which the task ignores, as the JVM's note of JAVA_TOOL_OPTIONS,
                // has the level FINE; the errors of the task itself are logged as warnings
                if (contentType == ConsoleViewContentType.ERROR_OUTPUT && logLevel != Level.FINE) {
                    errors.add(message);
                } else if (contentType == ConsoleViewContentType.ERROR_OUTPUT && !errors.isEmpty()) {
                    failedOutput.add(message);
                }
            }
            super.print(message, contentType, logLevel);
        }

        @NotNull String output() {
            return String.join("\n", output);
        }

        @NotNull List<String> errors() {
            return errors;
        }

        /**
         * Returns what was printed as an error at DEBUG after the first error, as the output of a command which doesn't
         * match.
         */
        @NotNull String failedOutput() {
            return String.join("\n", failedOutput);
        }
    }
}
