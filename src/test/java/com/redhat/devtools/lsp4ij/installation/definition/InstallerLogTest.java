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
package com.redhat.devtools.lsp4ij.installation.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.installation.DeclarativeLanguageServerInstaller;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationStatus;
import com.redhat.devtools.lsp4ij.installation.download.LogRecorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * An installer writes what it prints to the IDE's log too, whatever consoles are registered, as when none is: the
 * start and the result of the installation and of each step at INFO, the errors as warnings, and the output of the
 * commands at DEBUG, without the values which can be secrets (#2156).
 */
public class InstallerLogTest extends BasePlatformTestCase {

    private static final int TIMEOUT = 30;

    private static final String SECRET = "s3cr3t-Value_42";
    private static final String GITHUB_TOKEN = "ghp_" + "x".repeat(36);

    public void testAnInstallationWithoutAConsoleLogsTheStartAndTheResultOfTheInstallationAndOfEachStepAtInfo()
            throws Exception {
        // As when a language server starts before its console was ever opened
        var installer = new TestInstaller(loadInstaller(
                exec("Check the server", command("checking", "exit:1")),
                exec("Install the server", command("installing"))));

        try (var log = new LogRecorder()) {
            var installation = installer.checkInstallation();
            PlatformTestUtil.waitWithEventsDispatching("The installation must end", installation::isDone, TIMEOUT);

            assertEquals(ServerInstallationStatus.INSTALLED, installation.get());
            assertEmpty("No console is registered", installer.getConsoleProviders());
            assertEquals(log.output(), List.of(
                            "INFO Installer 'Test server': checking the installation",
                            "INFO Installer 'Test server': - Step: Check the server",
                            "INFO Installer 'Test server': the step 'Check the server' failed",
                            "INFO Installer 'Test server': the check failed",
                            "INFO Installer 'Test server': running the installation",
                            "INFO Installer 'Test server': - Step: Install the server",
                            "INFO Installer 'Test server': the step 'Install the server' succeeded",
                            "INFO Installer 'Test server': the installation completed successfully"),
                    infoAndWarnings(log));
        }
    }

    public void testTheOutputOfACommandIsLoggedAtDebugOnly() throws Exception {
        var installer = loadInstaller(null,
                exec("Install the server", command("output-of-the-command", "err:warning-of-the-command")));

        try (var log = new LogRecorder()) {
            assertTrue(log.output(), install(installer, new InstallerContext(null, InstallerContext.InstallerAction.RUN)));

            assertTrue(log.output(), log.lines().contains("FINE Installer 'Test server': output-of-the-command"));
            assertTrue(log.output(), log.lines().contains("FINE Installer 'Test server': warning-of-the-command"));
            assertTrue(log.output(), log.lines().stream().anyMatch(line -> line.startsWith("FINE Installer 'Test server': > ")
                    && line.endsWith(PrintOutput.class.getName() + " output-of-the-command err:warning-of-the-command")));
            assertFalse("The output must not be logged at INFO or as a warning: " + log.output(),
                    infoAndWarnings(log).stream().anyMatch(line -> line.contains("of-the-command")));
        }
    }

    public void testAnErrorIsLoggedAsAWarning() throws Exception {
        var installer = loadInstaller(null, exec("Install the server", List.of("lsp4ij-no-such-program")));

        try (var log = new LogRecorder()) {
            assertFalse(log.output(), install(installer, new InstallerContext(null, InstallerContext.InstallerAction.RUN)));

            List<String> warnings = log.lines().stream().filter(line -> line.startsWith("WARNING ")).toList();
            assertSize(1, warnings);
            assertTrue(log.output(), warnings.get(0).startsWith("WARNING Installer 'Test server': "));
            assertTrue(log.output(), warnings.get(0).contains("lsp4ij-no-such-program"));
            assertEquals(log.output(), "INFO Installer 'Test server': the installation failed",
                    infoAndWarnings(log).get(infoAndWarnings(log).size() - 1));
        }
    }

    public void testASecretIsNeverLogged() throws Exception {
        var installer = loadInstaller(null, exec("Install the server", command("--token=" + SECRET,
                "Authorization: Bearer " + SECRET,
                "err:https://user:" + SECRET + "@example.com/server.zip",
                GITHUB_TOKEN)));
        var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
        var printed = new PrintedLines();
        context.setProgressIndicator(printed);

        try (var log = new LogRecorder()) {
            assertTrue(log.output(), install(installer, context));

            // The console shows the command and its output as before
            assertTrue(printed.lines.toString(), printed.lines.stream().anyMatch(line -> line.startsWith("> ")
                    && line.contains("--token=" + SECRET)));
            assertTrue(printed.lines.toString(), printed.lines.stream().anyMatch(line -> line.contains(GITHUB_TOKEN)));
            // The log has them without the secrets
            assertFalse("The log must not have the secret: " + log.output(), log.output().contains(SECRET));
            assertFalse("The log must not have the token: " + log.output(), log.output().contains(GITHUB_TOKEN));
            assertTrue(log.output(), log.lines().stream().anyMatch(line -> line.startsWith("FINE Installer 'Test server': > ")
                    && line.contains(" --token=<redacted> Authorization: <redacted>")));
            assertTrue(log.output(), log.lines().containsAll(List.of(
                    "FINE Installer 'Test server': --token=<redacted>",
                    "FINE Installer 'Test server': Authorization: <redacted>",
                    "FINE Installer 'Test server': https://<redacted>@example.com/server.zip",
                    "FINE Installer 'Test server': <redacted>")));
        }
    }

    /**
     * Runs the given installer with the given context, which has no console, without a notification, and returns
     * whether it succeeded.
     */
    private static boolean install(@NotNull ServerInstallerDescriptor installer, @NotNull InstallerContext context) {
        context.setShowNotification(false);
        return ServerInstallerManager.getInstance().install(installer, context);
    }

    /**
     * Returns the records of the log which IntelliJ writes by default, of the levels INFO and above.
     */
    private static @NotNull List<String> infoAndWarnings(@NotNull LogRecorder log) {
        return log.lines().stream().filter(line -> !line.startsWith("FINE ")).toList();
    }

    /**
     * Returns the installer named {@code Test server} with the given check and run.
     */
    private static @NotNull ServerInstallerDescriptor loadInstaller(@Nullable JsonObject check,
                                                                    @Nullable JsonObject run) {
        var installer = new JsonObject();
        installer.addProperty("name", "Test server");
        if (check != null) {
            installer.add("check", check);
        }
        if (run != null) {
            installer.add("run", run);
        }
        return ServerInstallerManager.getInstance().loadInstaller(installer);
    }

    /**
     * Returns an exec task of the given name, which runs the given command, and succeeds when it exits with 0, also if
     * it writes on its standard error.
     */
    private static @NotNull JsonObject exec(@NotNull String name, @NotNull List<String> command) {
        var exec = new JsonObject();
        exec.addProperty("name", name);
        var array = new JsonArray();
        command.forEach(array::add);
        exec.add("command", array);
        exec.addProperty("ignoreStderr", true);
        var task = new JsonObject();
        task.add("exec", exec);
        return task;
    }

    /**
     * Returns the command which runs {@link PrintOutput} with the given arguments.
     */
    private static @NotNull List<String> command(@NotNull String... args) throws Exception {
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
     * A language server installer of the given installer, which no console registers with.
     */
    private static final class TestInstaller extends DeclarativeLanguageServerInstaller {

        private final @NotNull ServerInstallerDescriptor descriptor;

        TestInstaller(@NotNull ServerInstallerDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        protected @Nullable ServerInstallerDescriptor getServerInstallerDescriptor() {
            return descriptor;
        }

        @Override
        protected boolean canExecute(@NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
            return true;
        }
    }

    /**
     * Keeps the lines which an installer prints, which go to its progress indicator as to its consoles.
     */
    private static final class PrintedLines extends EmptyProgressIndicator {

        private final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        public void setText2(@Nullable String text) {
            if (text != null) {
                lines.add(text);
            }
        }
    }
}
