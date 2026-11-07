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
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    public void testAUserVariableReplacesTheSystemVariableOfTheSameNameInAnotherCaseOnWindowsOnly() {
        String systemName = EnvironmentUtil.getEnvironmentMap().keySet().stream()
                .filter(name -> name.equalsIgnoreCase("PATH"))
                .findFirst()
                .orElseThrow();
        String userName = systemName.equals("PATH") ? "Path" : "PATH";

        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", null, Map.of(userName, "user-path"), true);

        long pathNames = cmd.getEnvironment().keySet().stream().filter(name -> name.equalsIgnoreCase("PATH")).count();
        if (SystemInfo.isWindows) {
            // Windows ignores the case of a name: there is one variable, which the user's replaces
            Assert.assertEquals(1, pathNames);
            Assert.assertEquals("user-path", cmd.getEnvironment().get(systemName));
        } else {
            // Elsewhere they are two variables
            Assert.assertEquals(2, pathNames);
            Assert.assertEquals("user-path", cmd.getEnvironment().get(userName));
            Assert.assertEquals(EnvironmentUtil.getEnvironmentMap().get(systemName), cmd.getEnvironment().get(systemName));
        }
    }

    @Test
    public void testCreateCommandLineWithWorkingDirectory() {
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", Collections.emptyMap(), false);
        Assert.assertNotNull(cmd.getWorkDirectory());
        Assert.assertEquals(new File("C:/test/dir").getAbsolutePath(), cmd.getWorkDirectory().getAbsolutePath());
    }

    @Test
    public void testCreateCommandLineWithNullOrBlankWorkingDirectory() {
        GeneralCommandLine cmdNull = CommandUtils.createCommandLine("node server.js", null, Collections.emptyMap(), false);
        Assert.assertNull(cmdNull.getWorkDirectory());

        GeneralCommandLine cmdBlank = CommandUtils.createCommandLine("node server.js", "   ", Collections.emptyMap(), false);
        Assert.assertNull(cmdBlank.getWorkDirectory());
    }

    @Test
    public void testIncludeParentEnvironmentTruePreservesParentVars() {
        Map<String, String> userEnv = Map.of("USER_VAR", "user_val");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", userEnv, true);
        Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, cmd.getParentEnvironmentType());
        Assert.assertEquals("user_val", cmd.getEnvironment().get("USER_VAR"));
        Assert.assertFalse(cmd.getEnvironment().isEmpty());
    }

    @Test
    public void testUserEnvironmentVariablesOverrideSystemEnvironmentVariables() {
        String pathKey = SystemInfo.isWindows ? "path" : "PATH";
        Map<String, String> userEnv = Map.of(pathKey, "custom-path-override");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", null, userEnv, true);
        Assert.assertEquals("custom-path-override", cmd.getEnvironment().get(pathKey));
        if (SystemInfo.isWindows) {
            Assert.assertEquals("custom-path-override", cmd.getEnvironment().get("PATH"));
            Assert.assertEquals("custom-path-override", cmd.getEnvironment().get("Path"));
        }
    }

    @Test
    public void testIncludeParentEnvironmentFalseExcludesParentVars() {
        Map<String, String> userEnv = Map.of("USER_VAR", "user_val");
        GeneralCommandLine cmd = CommandUtils.createCommandLine("node server.js", "C:/test/dir", userEnv, false);
        Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.NONE, cmd.getParentEnvironmentType());
        Assert.assertEquals(1, cmd.getEnvironment().size());
        Assert.assertEquals("user_val", cmd.getEnvironment().get("USER_VAR"));
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

            Assert.assertEquals(tool.toFile().getCanonicalPath(), new File(commandLine.getExePath()).getCanonicalPath());
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    @Test
    public void testAnExecutableGivenWithItsPathIsNotLookedUp() {
        String executable = new File("bin", "tool").getPath();
        GeneralCommandLine commandLine = new GeneralCommandLine(executable);

        CommandUtils.resolveExecutable(commandLine);

        Assert.assertEquals(executable, commandLine.getExePath());
    }

    @Test
    public void testAnExecutableThatIsNotInThePathIsLeftAsItIs() {
        GeneralCommandLine commandLine = new GeneralCommandLine("lsp4ij-no-such-tool")
                .withEnvironment("PATH", System.getProperty("java.io.tmpdir"));

        CommandUtils.resolveExecutable(commandLine);

        Assert.assertEquals("lsp4ij-no-such-tool", commandLine.getExePath());
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
            Assert.assertEquals(tool.getFileName().toString(), withoutWorkingDirectory.getExePath());
            Assert.assertEquals(tool.toFile().getCanonicalPath(), new File(withWorkingDirectory.getExePath()).getCanonicalPath());
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }

    private static Path createTool(Path directory) throws Exception {
        Path tool = directory.resolve(SystemInfo.isWindows ? "lsp4ij-tool.bat" : "lsp4ij-tool");
        Files.writeString(tool, SystemInfo.isWindows ? "@echo off\r\necho ok" : "#!/bin/sh\necho ok");
        Assert.assertTrue(tool.toFile().setExecutable(true));
        return tool;
    }

    private static void assertCommand(String command, String... expected) {
        List<String> actual = CommandUtils.createCommands(command);
        Assert.assertArrayEquals(expected, actual.toArray());
    }
}
