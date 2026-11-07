/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link CommandUtils}.
 */
public class CommandUtilsTest {

    @Test
    public void testCommand() {
        assertCommand("foo bar",
                "foo",
                "bar"
        );
    }

    @Test
    public void testCommandWithSpaces() {
        assertCommand("foo",
                "foo");
        assertCommand("foo bar",
                "foo",
                "bar");
        assertCommand("foo   bar",
                "foo",
                "bar");
        assertCommand("   foo   bar   ",
                "foo",
                "bar");
    }

    @Test
    public void testGolpsCommand() {
        assertCommand("sh -c gopls -mode=stdio",
                "sh",
                "-c",
                "gopls",
                "-mode=stdio"
        );
    }

    @Test
    public void testGolpsCommandWithSpaces() {
        assertCommand("    sh    -c    gopls    -mode=stdio",
                "sh",
                "-c",
                "gopls",
                "-mode=stdio"
        );
    }

    @Test
    public void testCommandWithQuote() {
        assertCommand("    \"C:\\Users\\USERNAME\\A FOLDER WITH SPACE\\node_modules/.bin/typescript-language-server.cmd\"    --stdio   ",
                "C:\\Users\\USERNAME\\A FOLDER WITH SPACE\\node_modules/.bin/typescript-language-server.cmd",
                "--stdio"
        );
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testOnWindowsAUserVariableReplacesTheSystemVariableOfTheSameNameInAnotherCase() {
        String systemName = systemPathName();
        String userName = otherCase(systemName);

        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", null, Map.of(userName, "user-path"), true);

        // Windows ignores the case of a name: there is one variable, which the user's replaces
        Assertions.assertEquals(1, pathNames(cmd));
        Assertions.assertEquals("user-path", cmd.getEnvironment().get(systemName));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testOutsideWindowsAUserVariableInAnotherCaseIsASecondVariable() {
        String systemName = systemPathName();
        String userName = otherCase(systemName);

        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", null, Map.of(userName, "user-path"), true);

        Assertions.assertEquals(2, pathNames(cmd));
        Assertions.assertEquals("user-path", cmd.getEnvironment().get(userName));
        Assertions.assertEquals(EnvironmentUtil.getEnvironmentMap().get(systemName), cmd.getEnvironment().get(systemName));
    }

    @Test
    public void testCreateCommandLineWithWorkingDirectory() {
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", Collections.emptyMap(), false);
        Assertions.assertNotNull(cmd.getWorkDirectory());
        Assertions.assertEquals(new File("C:/test/dir").getAbsolutePath(), cmd.getWorkDirectory().getAbsolutePath());
    }

    @Test
    public void testCreateCommandLineWithNullOrBlankWorkingDirectory() {
        GeneralCommandLine cmdNull = CommandUtils.createCommandLine("node server.js", null, Collections.emptyMap(), false);
        Assertions.assertNull(cmdNull.getWorkDirectory());

        GeneralCommandLine cmdBlank = CommandUtils.createCommandLine("node server.js", "   ", Collections.emptyMap(), false);
        Assertions.assertNull(cmdBlank.getWorkDirectory());
    }

    @Test
    public void testIncludeParentEnvironmentTruePreservesParentVars() {
        Map<String, String> userEnv = Map.of("USER_VAR", "user_val");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", userEnv, true);
        Assertions.assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, cmd.getParentEnvironmentType());
        Assertions.assertEquals("user_val", cmd.getEnvironment().get("USER_VAR"));
        Assertions.assertFalse(cmd.getEnvironment().isEmpty());
    }

    @Test
    public void testUserEnvironmentVariablesOverrideSystemEnvironmentVariables() {
        Map<String, String> userEnv = Map.of("PATH", "custom-path-override");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", null, userEnv, true);
        Assertions.assertEquals("custom-path-override", cmd.getEnvironment().get("PATH"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testOnWindowsAUserVariableOverridesTheSystemVariableWhateverTheCaseOfItsName() {
        Map<String, String> userEnv = Map.of("path", "custom-path-override");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", null, userEnv, true);
        Assertions.assertEquals("custom-path-override", cmd.getEnvironment().get("path"));
        Assertions.assertEquals("custom-path-override", cmd.getEnvironment().get("PATH"));
        Assertions.assertEquals("custom-path-override", cmd.getEnvironment().get("Path"));
    }

    @Test
    public void testWithoutTheSystemVariablesTheProcessStillInheritsTheIdeEnvironment() {
        // As on main: the system variables aren't copied into the command line, but its process inherits them
        Map<String, String> userEnv = Map.of("USER_VAR", "user_val");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", userEnv, false);
        Assertions.assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, cmd.getParentEnvironmentType());
        Assertions.assertEquals(userEnv, cmd.getEnvironment());
    }

    @Test
    public void testTheExecutableIsLookedUpInTheEnvironmentTheProcessGets() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            Path tool = createTool(directory);
            // Adds the directory to the PATH when the environment is set up, as a CommandLineEnvCustomizer does for
            // the working directory of a process since 2025.1
            GeneralCommandLine commandLine = new GeneralCommandLine(tool.getFileName().toString()) {
                @Override
                protected void setupEnvironment(@NotNull Map<String, String> environment) {
                    super.setupEnvironment(environment);
                    String path = environment.get("PATH");
                    environment.put("PATH", path != null ? directory + File.pathSeparator + path : directory.toString());
                }
            };

            CommandUtils.resolveExecutable(commandLine);

            Assertions.assertEquals(tool.toFile().getCanonicalPath(), new File(commandLine.getExePath()).getCanonicalPath());
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    @Test
    public void testAnExecutableGivenWithItsPathIsNotLookedUp() {
        String executable = new File("bin", "tool").getPath();
        GeneralCommandLine commandLine = new GeneralCommandLine(executable);

        CommandUtils.resolveExecutable(commandLine);

        Assertions.assertEquals(executable, commandLine.getExePath());
    }

    @Test
    public void testAnExecutableThatIsNotInThePathIsLeftAsItIs() {
        GeneralCommandLine commandLine = new GeneralCommandLine("lsp4ij-no-such-tool")
                .withEnvironment("PATH", System.getProperty("java.io.tmpdir"));

        CommandUtils.resolveExecutable(commandLine);

        Assertions.assertEquals("lsp4ij-no-such-tool", commandLine.getExePath());
    }

    @Test
    public void testACommandLineWithAWorkingDirectoryLooksUpItsExecutable() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            Path tool = createTool(directory);
            Map<String, String> environment = Map.of("PATH", directory.toString());

            GeneralCommandLine withoutWorkingDirectory = CommandUtils.createCommandLine(
                    tool.getFileName().toString(), null, environment, true);
            GeneralCommandLine withWorkingDirectory = CommandUtils.createCommandLine(
                    tool.getFileName().toString(), directory.toString(), environment, true);

            // Without a working directory, the platform looks it up when the process starts, as before
            Assertions.assertEquals(tool.getFileName().toString(), withoutWorkingDirectory.getExePath());
            Assertions.assertEquals(tool.toFile().getCanonicalPath(), new File(withWorkingDirectory.getExePath()).getCanonicalPath());
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    @Test
    public void testTheWindowsLookupTriesTheExtensionsOfPathextForANameWithoutOne() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            // As Node.js ships npx: a shell script without an extension, which Windows can't start, next to npx.cmd
            createFile(directory, "lsp4ij-tool");
            Path cmd = createFile(directory, "lsp4ij-tool.cmd");

            File found = CommandUtils.findInWindowsPath("lsp4ij-tool", directory.toString(), ".COM;.EXE;.BAT;.CMD");

            assertSameFile(cmd, found);
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    @Test
    public void testTheWindowsLookupGoesThroughThePathBeforeTheOrderOfPathext() throws Exception {
        Path first = Files.createTempDirectory("tool-dir");
        Path second = Files.createTempDirectory("tool-dir");
        try {
            Path cmd = createFile(first, "lsp4ij-tool.cmd");
            createFile(second, "lsp4ij-tool.exe");

            File found = CommandUtils.findInWindowsPath("lsp4ij-tool", first + File.pathSeparator + second, ".EXE;.CMD");

            assertSameFile(cmd, found);
        } finally {
            FileUtil.delete(first.toFile());
            FileUtil.delete(second.toFile());
        }
    }

    @Test
    public void testTheWindowsLookupTriesTheExtensionsOfADirectoryInTheOrderOfPathext() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            createFile(directory, "lsp4ij-tool.cmd");
            Path exe = createFile(directory, "lsp4ij-tool.exe");

            File found = CommandUtils.findInWindowsPath("lsp4ij-tool", directory.toString(), ".EXE;.CMD");

            assertSameFile(exe, found);
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    @Test
    public void testTheWindowsLookupTakesANameWithAnExecutableExtensionAsItIs() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            Path cmd = createFile(directory, "lsp4ij-tool.cmd");
            createFile(directory, "lsp4ij-tool.cmd.exe");

            File found = CommandUtils.findInWindowsPath("lsp4ij-tool.cmd", directory.toString(), ".EXE;.CMD");

            assertSameFile(cmd, found);
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    @Test
    public void testTheWindowsLookupWithoutPathextTriesTheDefaultExtensions() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            Path bat = createFile(directory, "lsp4ij-tool.bat");

            File found = CommandUtils.findInWindowsPath("lsp4ij-tool", directory.toString(), null);

            assertSameFile(bat, found);
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    @Test
    public void testTheWindowsLookupFindsNoFileWithoutAnExtension() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            createFile(directory, "lsp4ij-tool");

            Assertions.assertNull(CommandUtils.findInWindowsPath("lsp4ij-tool", directory.toString(), ".EXE;.CMD"));
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testOnWindowsACommandWithoutItsExtensionIsLookedUpInTheEnvironmentTheProcessGets() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            createFile(directory, "lsp4ij-tool");
            Path bat = createTool(directory);
            // As a CommandLineEnvCustomizer does for the working directory of a process since 2025.1
            GeneralCommandLine commandLine = new GeneralCommandLine("lsp4ij-tool") {
                @Override
                protected void setupEnvironment(@NotNull Map<String, String> environment) {
                    super.setupEnvironment(environment);
                    String path = environment.get("PATH");
                    environment.put("PATH", path != null ? directory + File.pathSeparator + path : directory.toString());
                }
            };

            CommandUtils.resolveExecutable(commandLine);

            assertSameFile(bat, new File(commandLine.getExePath()));
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testOnWindowsTheScriptFoundForACommandWithoutItsExtensionStarts() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            // Windows can't start the script without an extension, so the .bat next to it must be the one that starts
            createFile(directory, "lsp4ij-tool");
            createTool(directory);
            String path = directory + File.pathSeparator + Objects.requireNonNullElse(System.getenv("PATH"), "");
            GeneralCommandLine commandLine = CommandUtils.createCommandLine("lsp4ij-tool", directory.toString(),
                    Map.of("PATH", path), true);

            Process process = commandLine.createProcess();
            String output;
            try {
                output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            } finally {
                Assertions.assertTrue(process.waitFor(30, TimeUnit.SECONDS), "The script must end");
            }

            Assertions.assertEquals("ok", output);
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    private static String systemPathName() {
        return EnvironmentUtil.getEnvironmentMap().keySet().stream()
                .filter(name -> name.equalsIgnoreCase("PATH"))
                .findFirst()
                .orElseThrow();
    }

    private static String otherCase(String pathName) {
        return pathName.equals("PATH") ? "Path" : "PATH";
    }

    private static long pathNames(GeneralCommandLine cmd) {
        return cmd.getEnvironment().keySet().stream().filter(name -> name.equalsIgnoreCase("PATH")).count();
    }

    private static Path createFile(Path directory, String name) throws Exception {
        Path file = Files.writeString(directory.resolve(name), "");
        Assertions.assertTrue(file.toFile().setExecutable(true));
        return file;
    }

    private static void assertSameFile(Path expected, File actual) throws Exception {
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(expected.toFile().getCanonicalPath(), actual.getCanonicalPath());
    }

    private static Path createTool(Path directory) throws Exception {
        Path tool = directory.resolve(SystemInfo.isWindows ? "lsp4ij-tool.bat" : "lsp4ij-tool");
        Files.writeString(tool, SystemInfo.isWindows ? "@echo off\r\necho ok" : "#!/bin/sh\necho ok");
        Assertions.assertTrue(tool.toFile().setExecutable(true));
        return tool;
    }

    private static void assertCommand(String command, String... expected) {
        List<String> actual = CommandUtils.createCommands(command);
        Assertions.assertArrayEquals(expected, actual.toArray());
    }
}
