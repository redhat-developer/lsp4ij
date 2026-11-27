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
package com.redhat.devtools.lsp4ij.dap.descriptors;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.SystemProperties;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.definition.launching.WorkingDirectoryRecorder;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getFilePath;

/**
 * Tests the directory which the Debug Adapter of a DAP run configuration runs in. A configuration created from a file
 * has no working directory, so its Debug Adapter runs in the directory of that file and gets the environment of that
 * directory, as a {@code mise.toml} or an {@code .envrc} gives it with the mise or direnv plugin, while
 * {@code ${workspaceFolder}} stays the project's directory. Without a file, it runs in the project's directory. A
 * working directory set in the configuration wins, and an existing configuration keeps the one it stored. A relative
 * working directory is relative to the project's directory, a relative file to the working directory, and without one
 * to the project's directory, and a leading {@code ~} is the home directory.
 */
public class DAPConfigurationWorkingDirectoryTest extends HeavyPlatformTestCase {

    private static final String SERVER_ID = "dap-working-directory-test";
    // The files of the Debug Adapter of the tests
    private static final String FILE_EXTENSION = "dapwd";
    /**
     * The extension point through which a plugin, as mise's, sets up the environment of a process for its working
     * directory, since IntelliJ 2025.1. Its interface doesn't exist before, so the tests reach it by name.
     */
    private static final String COMMAND_LINE_ENV_CUSTOMIZER = "com.intellij.commandLineEnvCustomizer";
    private static final String COMMAND_LINE_ENV_CUSTOMIZER_CLASS = "com.intellij.execution.process.CommandLineEnvCustomizer";
    // The variable which the environment of the working directory sets to that directory
    private static final String DIRECTORY_VARIABLE = "LSP4IJ_TEST_DAP_DIRECTORY_ENVIRONMENT";
    private static final long TIMEOUT_SECONDS = 120;

    private UserDefinedDebugAdapterServerDefinition server;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The directory of the project, which isn't on disk until it is created
        getOrCreateProjectBaseDir();
        server = new UserDefinedDebugAdapterServerDefinition(SERVER_ID, "Working directory test", recorder(), List.of(),
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*." + FILE_EXTENSION), null)));
        DebugAdapterManager.getInstance().addDebugAdapterServerWithoutNotification(server);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            DebugAdapterManager.getInstance().removeDebugAdapterServer(server);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testAConfigurationCreatedFromAFileInASubdirectoryStartsItsDebugAdapterThereWithItsEnvironment() throws Exception {
        boolean directoryEnvironments = addDirectoryEnvironment();
        VirtualFile subdirectory = createChildDirectory(projectDirectory(), "sub");
        var configuration = createFromFile(createChildData(subdirectory, "test." + FILE_EXTENSION));

        var record = Record.of(new CapturingProcessHandler(commandLine(configuration))
                .runProcess((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)));

        assertEquals(realPath(subdirectory.getPath()), record.directory());
        if (directoryEnvironments) {
            assertEquals(realPath(subdirectory.getPath()), record.directoryEnvironment());
        }
        // else IntelliJ 2024.x, which has no environment of a directory
        assertTrue("no working directory is stored: " + configuration.getWorkingDirectory(),
                StringUtils.isBlank(configuration.getWorkingDirectory()));
        assertEquals("${workspaceFolder} is still the project's directory", projectDirectory().getPath(),
                dapParameters(configuration).get("cwd"));
    }

    public void testAConfigurationWithoutAFileStartsItsDebugAdapterInTheProjectDirectory() throws Exception {
        var configuration = create();
        configuration.setServerId(SERVER_ID);
        configuration.setCommand(server.getCommandLine());
        configuration.setLaunchConfiguration("{\"cwd\": \"${workspaceFolder}\"}");

        assertTrue(StringUtils.isBlank(configuration.getWorkingDirectory()));
        assertEquals(realPath(projectDirectory().getPath()), workDirectory(configuration));
        assertEquals(projectDirectory().getPath(), dapParameters(configuration).get("cwd"));
    }

    public void testAWorkingDirectorySetInTheConfigurationWins() throws Exception {
        VirtualFile subdirectory = createChildDirectory(projectDirectory(), "sub");
        var configuration = createFromFile(createChildData(subdirectory, "test." + FILE_EXTENSION));
        String directory = createTempDirectory().getPath();
        configuration.setWorkingDirectory(directory);

        assertEquals(realPath(directory), workDirectory(configuration));
        // as before: ${workspaceFolder} is the working directory which the configuration sets
        assertEquals(directory, dapParameters(configuration).get("cwd"));
    }

    public void testAnExistingConfigurationKeepsTheWorkingDirectoryItStored() throws Exception {
        VirtualFile subdirectory = createChildDirectory(projectDirectory(), "sub");
        VirtualFile file = createChildData(subdirectory, "test." + FILE_EXTENSION);
        // As a configuration created from the file stored it, when it got the project's directory as working directory
        var created = createFromFile(file);
        created.setWorkingDirectory(projectDirectory().getPath());
        var stored = new Element("configuration");
        created.writeExternal(stored);

        var existing = create();
        existing.readExternal(stored);

        assertEquals(projectDirectory().getPath(), existing.getWorkingDirectory());
        assertEquals(realPath(projectDirectory().getPath()), workDirectory(existing));

        // A configuration created from the same file copies it
        var runManager = RunManager.getInstance(getProject());
        runManager.addConfiguration(runManager.createConfiguration(existing, existing.getFactory()));
        var copy = createFromFile(file);

        assertEquals(projectDirectory().getPath(), copy.getWorkingDirectory());
        assertEquals(realPath(projectDirectory().getPath()), workDirectory(copy));
    }

    public void testAFileWhoseDirectoryIsNotOnDiskStartsItsDebugAdapterInTheProjectDirectory() throws Exception {
        var configuration = createFromFile(createChildData(projectDirectory(), "test." + FILE_EXTENSION));
        configuration.setFile(new File(createTempDirectory(), "missing/test." + FILE_EXTENSION).getPath());

        assertEquals(realPath(projectDirectory().getPath()), workDirectory(configuration));
    }

    public void testAFolderToDebugStartsItsDebugAdapterInThatFolder() throws Exception {
        // As a Go package
        VirtualFile folder = createChildDirectory(projectDirectory(), "package");
        var configuration = createFromFile(createChildData(projectDirectory(), "test." + FILE_EXTENSION));
        configuration.setFile(getFilePath(folder));

        assertEquals(realPath(folder.getPath()), workDirectory(configuration));
    }

    public void testARelativeWorkingDirectoryIsRelativeToTheProjectDirectory() throws Exception {
        VirtualFile subdirectory = createChildDirectory(projectDirectory(), "sub");
        var configuration = create();
        configuration.setServerId(SERVER_ID);
        configuration.setCommand(server.getCommandLine());
        configuration.setLaunchConfiguration("{\"cwd\": \"${workspaceFolder}\"}");
        configuration.setWorkingDirectory("sub");

        // not the IDE's working directory, where it was dropped without a word as it isn't a directory there
        assertEquals(realPath(subdirectory.getPath()), workDirectory(configuration));
        assertEquals(realPath(subdirectory.getPath()), realPath((String) dapParameters(configuration).get("cwd")));
        // no warning
        configuration.checkConfiguration();
        assertEquals("what is stored, and shown in the settings editor", "sub", configuration.getWorkingDirectory());
    }

    public void testATildeInTheWorkingDirectoryIsTheHomeDirectory() throws Exception {
        var configuration = create();
        configuration.setServerId(SERVER_ID);
        configuration.setCommand(server.getCommandLine());
        configuration.setWorkingDirectory("~");

        assertEquals(realPath(SystemProperties.getUserHome()), workDirectory(configuration));
    }

    public void testAConfigurationWithAFileRelativeToTheProjectDirectoryIsTheConfigurationOfThatFile() throws Exception {
        VirtualFile file = createChildData(createChildDirectory(projectDirectory(), "sub"), "test." + FILE_EXTENSION);

        // Without a working directory, a relative file is relative to the project's directory
        assertExistingConfigurationIsCopied(file, null, "sub/test." + FILE_EXTENSION);
    }

    public void testAConfigurationWithAFileRelativeToItsWorkingDirectoryIsTheConfigurationOfThatFile() throws Exception {
        VirtualFile subdirectory = createChildDirectory(projectDirectory(), "sub");
        VirtualFile file = createChildData(subdirectory, "test." + FILE_EXTENSION);

        // As the Debug Adapter resolves the file in its working directory
        assertExistingConfigurationIsCopied(file, getFilePath(subdirectory), "test." + FILE_EXTENSION);
        // A relative working directory is relative to the project's directory
        assertExistingConfigurationIsCopied(file, "sub", "test." + FILE_EXTENSION);
    }

    /**
     * Asserts that a configuration created from the given file copies an existing configuration with the given working
     * directory and the given relative path of that file.
     */
    private void assertExistingConfigurationIsCopied(@NotNull VirtualFile file,
                                                     @Nullable String workingDirectory,
                                                     @NotNull String relativeFile) {
        var runManager = RunManager.getInstance(getProject());
        // only the existing configuration of this call
        List.copyOf(runManager.getAllSettings()).forEach(runManager::removeConfiguration);
        var existing = createFromFile(file);
        existing.setWorkingDirectory(workingDirectory);
        existing.setFile(relativeFile);
        // which a configuration prepared for the file without the existing one doesn't get
        String launchConfiguration = "{\"name\": \"existing\"}";
        existing.setLaunchConfiguration(launchConfiguration);
        runManager.addConfiguration(runManager.createConfiguration(existing, existing.getFactory()));

        var copy = create();
        assertTrue(DebugAdapterManager.getInstance().prepareConfiguration(copy, file, getProject()));

        assertEquals("the existing configuration is copied", launchConfiguration, copy.getLaunchConfiguration());
        assertEquals(relativeFile, copy.getFile());
    }

    /**
     * Creates a DAP run configuration from the template, as a configuration that the user adds.
     */
    private @NotNull DAPRunConfiguration create() {
        var type = ConfigurationTypeUtil.findConfigurationType("DAPConfiguration");
        assertNotNull("The DAP run configuration type is registered", type);
        var factory = type.getConfigurationFactories()[0];
        return (DAPRunConfiguration) RunManager.getInstance(getProject()).createConfiguration("test", factory).getConfiguration();
    }

    /**
     * Creates a DAP run configuration from the given file, as the run configuration producer does: from the template,
     * then prepared for the file.
     */
    private @NotNull DAPRunConfiguration createFromFile(@NotNull VirtualFile file) {
        var configuration = create();
        assertTrue("The Debug Adapter of the tests debugs " + file.getName(),
                DebugAdapterManager.getInstance().prepareConfiguration(configuration, file, getProject()));
        assertEquals(getFilePath(file), configuration.getFile());
        return configuration;
    }

    private @NotNull DAPCommandLineState state(@NotNull DAPRunConfiguration configuration) throws Exception {
        var environment = ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(),
                configuration).build();
        return (DAPCommandLineState) environment.getState();
    }

    /**
     * Returns the command line which starts the Debug Adapter of the given configuration.
     */
    private @NotNull GeneralCommandLine commandLine(@NotNull DAPRunConfiguration configuration) throws Exception {
        var descriptor = (DefaultDebugAdapterDescriptor) state(configuration).getServerDescriptor();
        return descriptor.createStartServerCommandLine(descriptor.options);
    }

    private @Nullable Path workDirectory(@NotNull DAPRunConfiguration configuration) throws Exception {
        File directory = commandLine(configuration).getWorkDirectory();
        return directory != null ? directory.toPath().toRealPath() : null;
    }

    private @NotNull Map<String, Object> dapParameters(@NotNull DAPRunConfiguration configuration) throws Exception {
        return state(configuration).getDAPParameters();
    }

    private @NotNull VirtualFile projectDirectory() {
        VirtualFile directory = ProjectUtil.guessProjectDir(getProject());
        assertNotNull("The project has a directory", directory);
        assertTrue("The project's directory is on disk: " + directory,
                directory.isInLocalFileSystem() && Files.isDirectory(Path.of(directory.getPath())));
        return directory;
    }

    private static @NotNull Path realPath(@NotNull String path) throws Exception {
        return Path.of(path).toRealPath();
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
                    case "toString" -> "the environment of the working directory of the DAP tests";
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

        static @NotNull Record of(@NotNull ProcessOutput output) throws Exception {
            assertFalse("The recorder didn't end within " + TIMEOUT_SECONDS + " s: " + output.getStdout(), output.isTimeout());
            assertEquals("The recorder failed: " + output.getStderr(), 0, output.getExitCode());
            var lines = output.getStdout().lines().toList();
            assertFalse("The recorder printed no directory: " + output.getStdout(), lines.isEmpty());
            String environment = lines.size() > 1 ? lines.get(1) : "";
            return new Record(realPath(lines.get(0)), environment.isEmpty() ? null : realPath(environment));
        }
    }

    /**
     * Returns the command of a Debug Adapter which runs {@link WorkingDirectoryRecorder} in a Java process, printing
     * {@link #DIRECTORY_VARIABLE}.
     */
    private static @NotNull String recorder() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return "\"" + java + "\" -cp \"" + PathManager.getJarPathForClass(WorkingDirectoryRecorder.class) + "\" "
                + WorkingDirectoryRecorder.class.getName() + " " + DIRECTORY_VARIABLE;
    }
}
