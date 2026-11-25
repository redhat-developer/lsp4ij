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
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.ProcessDataProvider;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests the directory which a user-defined language server runs in: without a configured working directory, the
 * project's directory, whose environment it then gets, as a {@code mise.toml} or an {@code .envrc} gives it with the
 * mise or direnv plugin. A configured working directory wins. The root of an instance is tested by
 * {@link UserDefinedServerInstancesTest}.
 */
public class UserDefinedServerWorkingDirectoryTest extends BasePlatformTestCase {

    /**
     * The extension point through which a plugin, as mise's, sets up the environment of a process for its working
     * directory, since IntelliJ 2025.1. Its interface doesn't exist before, so the tests reach it by name.
     */
    private static final String COMMAND_LINE_ENV_CUSTOMIZER = "com.intellij.commandLineEnvCustomizer";
    private static final String COMMAND_LINE_ENV_CUSTOMIZER_CLASS = "com.intellij.execution.process.CommandLineEnvCustomizer";
    // The variable which the environment of the working directory sets to that directory
    private static final String DIRECTORY_VARIABLE = "LSP4IJ_TEST_DIRECTORY_ENVIRONMENT";
    private static final long TIMEOUT_SECONDS = 120;

    public void testWithoutAWorkingDirectoryTheLanguageServerRunsInTheProjectDirectoryAndGetsItsEnvironment() throws Exception {
        Path projectDirectory = createProjectDirectory();
        boolean directoryEnvironments = addDirectoryEnvironment();
        var definition = definition(recorder(), null);

        var record = run(definition.createConnectionProvider(getProject()));

        assertEquals(projectDirectory, record.directory());
        if (directoryEnvironments) {
            assertEquals(projectDirectory, record.directoryEnvironment());
        }
        // else IntelliJ 2024.x, which has no environment of a directory
    }

    public void testWithABlankWorkingDirectoryTheLanguageServerRunsInTheProjectDirectory() throws IOException {
        createProjectDirectory();
        var definition = definition("my-server --stdio", " ");

        assertEquals(new File(getProject().getBasePath()), workDirectory(definition.createConnectionProvider(getProject())));
    }

    public void testAProjectDirectoryWhichIsNotOnDiskGivesNoWorkingDirectory() {
        assertFalse("the light project of the tests has no directory on disk",
                Files.exists(Path.of(getProject().getBasePath())));
        var definition = definition("my-server --stdio", null);

        // so that the language server still starts, in the IDE's working directory, as before
        assertNull(workDirectory(definition.createConnectionProvider(getProject())));
    }

    public void testAConfiguredWorkingDirectoryWinsAndGivesItsEnvironment() throws Exception {
        boolean directoryEnvironments = addDirectoryEnvironment();
        Path directory = temporaryDirectory();
        var definition = definition(recorder(), directory.toString());

        var record = run(definition.createConnectionProvider(getProject()));

        assertEquals(directory, record.directory());
        if (directoryEnvironments) {
            assertEquals(directory, record.directoryEnvironment());
        }
    }

    public void testOverASocketTheLanguageServerRunsInTheProjectDirectoryAndGetsItsEnvironment() throws Exception {
        Path projectDirectory = createProjectDirectory();
        boolean directoryEnvironments = addDirectoryEnvironment();
        var definition = definition(recorder() + " ${port}", null);
        definition.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0));
        var provider = definition.createConnectionProvider(getProject());

        Record record;
        try {
            provider.start();
            record = read(provider.getInputStream());
        } finally {
            stop(provider);
        }

        assertEquals(projectDirectory, record.directory());
        if (directoryEnvironments) {
            assertEquals(projectDirectory, record.directoryEnvironment());
        }
    }

    public void testTheInstallerChecksTheLanguageServerInTheProjectDirectory() throws IOException {
        createProjectDirectory();
        var definition = definition("my-server --stdio", null);

        var provider = definition.getServerStarter().createConnectionProvider(getProject());

        assertNotNull(provider);
        assertEquals(new File(getProject().getBasePath()), workDirectory(provider));
    }

    public void testTheInstallerChecksAConfiguredWorkingDirectory() throws IOException {
        Path directory = temporaryDirectory();
        var definition = definition("my-server --stdio", directory.toString());

        var provider = definition.getServerStarter().createConnectionProvider(getProject());

        assertNotNull(provider);
        assertEquals(directory.toFile(), workDirectory(provider));
    }

    public void testTheDefaultProjectGivesNoWorkingDirectory() {
        // As for the check of an installer which runs without a project
        var defaultProject = ProjectManager.getInstance().getDefaultProject();
        var definition = definition("my-server --stdio", null);

        var provider = definition.getServerStarter().createConnectionProvider(defaultProject);

        assertNotNull(provider);
        assertNull(workDirectory(provider));
    }

    /**
     * Gives each process of {@link WorkingDirectoryRecorder} with a working directory the environment of that
     * directory, {@link #DIRECTORY_VARIABLE} set to it, as a {@code CommandLineEnvCustomizer} does, when the IDE has
     * the extension point, since IntelliJ 2025.1.
     *
     * @return true if the IDE sets up the environment of a directory, and false if it doesn't, before 2025.1.
     */
    private boolean addDirectoryEnvironment() throws ClassNotFoundException {
        var area = ApplicationManager.getApplication().getExtensionArea();
        boolean directoryEnvironments = area.hasExtensionPoint(COMMAND_LINE_ENV_CUSTOMIZER);
        // so that the environment is checked wherever the IDE has it
        assertEquals("IntelliJ 2025.1 and later set up the environment of a directory",
                ApplicationInfo.getInstance().getBuild().getBaselineVersion() >= 251, directoryEnvironments);
        if (!directoryEnvironments) {
            return false;
        }
        Class<?> customizerClass = Class.forName(COMMAND_LINE_ENV_CUSTOMIZER_CLASS, true,
                GeneralCommandLine.class.getClassLoader());
        Object customizer = Proxy.newProxyInstance(customizerClass.getClassLoader(), new Class<?>[]{customizerClass},
                (proxy, method, args) -> switch (method.getName()) {
                    case "customizeEnv" -> {
                        var commandLine = (GeneralCommandLine) args[0];
                        @SuppressWarnings("unchecked")
                        var environment = (Map<String, String>) args[1];
                        if (commandLine.getWorkDirectory() != null
                                && commandLine.getParametersList().getList().contains(WorkingDirectoryRecorder.class.getName())) {
                            environment.put(DIRECTORY_VARIABLE, commandLine.getWorkDirectory().getPath());
                        }
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "the environment of the working directory of the tests";
                    default -> null;
                });
        area.getExtensionPoint(COMMAND_LINE_ENV_CUSTOMIZER).registerExtension(customizer, getTestRootDisposable());
        return true;
    }

    /**
     * What {@link WorkingDirectoryRecorder} printed: the directory it ran in, and the directory of the environment it
     * got, or null if it got none.
     */
    private record Record(@NotNull Path directory, @Nullable Path directoryEnvironment) {

        static @NotNull Record of(@NotNull String output) throws IOException {
            var lines = output.lines().toList();
            assertTrue("The recorder printed no directory: " + output, !lines.isEmpty());
            String environment = lines.size() > 1 ? lines.get(1) : "";
            return new Record(Path.of(lines.get(0)).toRealPath(), environment.isEmpty() ? null : Path.of(environment).toRealPath());
        }
    }

    /**
     * Runs the process of the given provider of the standard input and output, and returns what it printed.
     */
    private static @NotNull Record run(@NotNull StreamConnectionProvider provider) throws Exception {
        var commandLine = ((OSProcessStreamConnectionProvider) provider).getCommandLine();
        var output = new CapturingProcessHandler(commandLine).runProcess((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertFalse("The recorder didn't end within " + TIMEOUT_SECONDS + " s: " + output.getStdout(), output.isTimeout());
        assertEquals("The recorder failed: " + output.getStderr(), 0, output.getExitCode());
        return Record.of(output.getStdout());
    }

    /**
     * Reads the two lines which the recorder sends over the socket.
     */
    private static @NotNull Record read(@NotNull InputStream in) throws Exception {
        var lines = CompletableFuture.supplyAsync(() -> {
            try {
                return readLine(in) + "\n" + readLine(in);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        try {
            return Record.of(lines.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            throw new AssertionError("The recorder sent no directory within " + TIMEOUT_SECONDS + " s", e);
        }
    }

    private static @NotNull String readLine(@NotNull InputStream in) throws IOException {
        var line = new ByteArrayOutputStream();
        for (int b = in.read(); b != '\n'; b = in.read()) {
            if (b == -1) {
                throw new IOException("The connection ended before the line did: " + line);
            }
            line.write(b);
        }
        return line.toString(StandardCharsets.UTF_8);
    }

    /**
     * Stops the language server of the given provider, and waits until its process is gone.
     */
    private static void stop(@NotNull StreamConnectionProvider provider) throws Exception {
        Long pid = provider instanceof ProcessDataProvider data ? data.getPid() : null;
        provider.stop();
        if (pid == null) {
            return;
        }
        var process = ProcessHandle.of(pid);
        if (process.isPresent()) {
            process.get().destroyForcibly();
            try {
                process.get().onExit().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("The recorder " + pid + " didn't end within " + TIMEOUT_SECONDS + " s", e);
            }
        }
    }

    private static @Nullable File workDirectory(@NotNull StreamConnectionProvider provider) {
        return ((OSProcessStreamConnectionProvider) provider).getCommandLine().getWorkDirectory();
    }

    /**
     * Creates the directory of the project until the end of the test, and returns its real path: that of the light
     * project of the tests isn't on disk, and a language server only runs in a directory which is.
     */
    private @NotNull Path createProjectDirectory() throws IOException {
        Path directory = Path.of(getProject().getBasePath());
        if (!Files.isDirectory(directory)) {
            Files.createDirectories(directory);
            Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));
        }
        return directory.toRealPath();
    }

    private @NotNull Path temporaryDirectory() throws IOException {
        Path directory = Files.createTempDirectory("lsp4ij-working-directory").toRealPath();
        Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));
        return directory;
    }

    /**
     * Returns the command line which runs {@link WorkingDirectoryRecorder} in a Java process, printing
     * {@link #DIRECTORY_VARIABLE}.
     */
    private static @NotNull String recorder() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return "\"" + java + "\" -cp \"" + PathManager.getJarPathForClass(WorkingDirectoryRecorder.class) + "\" "
                + WorkingDirectoryRecorder.class.getName() + " " + DIRECTORY_VARIABLE;
    }

    private static @NotNull UserDefinedLanguageServerDefinition definition(@NotNull String commandLine,
                                                                          @Nullable String workingDir) {
        return new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(), null, "Server", null, null,
                commandLine, workingDir, Map.of(), false, null, false, null, null, null, null, null, null);
    }
}
