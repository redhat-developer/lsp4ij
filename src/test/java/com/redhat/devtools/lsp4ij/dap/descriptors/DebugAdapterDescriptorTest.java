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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * Tests for {@link DebugAdapterDescriptor}.
 */
public class DebugAdapterDescriptorTest {

    private static class TestDescriptor extends DebugAdapterDescriptor {
        public TestDescriptor() {
            super(new RunConfigurationOptions(), new ExecutionEnvironment(), null);
        }

        @Override
        public ProcessHandler startServer() {
            return null;
        }

        @Override
        public Map<String, Object> getDapParameters() {
            return Collections.emptyMap();
        }

        @Override
        public FileType getFileType() {
            return PlainTextFileType.INSTANCE;
        }

        @Override
        public boolean isDebuggableFile(VirtualFile file, Project project) {
            return true;
        }

        @Override
        public @NotNull GeneralCommandLine createStartServerCommandLine(@Nullable String workingDirectory,
                                                                        @Nullable String command,
                                                                        @NotNull Map<String, String> userEnvironmentVariables,
                                                                        boolean includeSystemEnvironmentVariables) throws ExecutionException {
            return super.createStartServerCommandLine(workingDirectory, command, userEnvironmentVariables, includeSystemEnvironmentVariables);
        }
    }

    @Test
    public void testCreateStartServerCommandLineWithValidWorkingDirectory() throws Exception {
        Path tempDir = Files.createTempDirectory("dap-test");
        try {
            TestDescriptor descriptor = new TestDescriptor();
            GeneralCommandLine cmd = descriptor.createStartServerCommandLine(
                    tempDir.toString(),
                    "node server.js",
                    Collections.emptyMap(),
                    false
            );

            Assert.assertNotNull(cmd.getWorkDirectory());
            Assert.assertEquals(tempDir.toFile().getCanonicalPath(), cmd.getWorkDirectory().getCanonicalPath());
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testCreateStartServerCommandLineWithNonDirectoryPath() throws Exception {
        Path tempFile = Files.createTempFile("dap-test", ".tmp");
        try {
            TestDescriptor descriptor = new TestDescriptor();
            GeneralCommandLine cmd = descriptor.createStartServerCommandLine(
                    tempFile.toString(),
                    "node server.js",
                    Collections.emptyMap(),
                    false
            );

            // A file is not a directory, so no working directory is set
            Assert.assertNull(cmd.getWorkDirectory());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testCreateStartServerCommandLineWithNullOrBlankWorkingDirectory() throws Exception {
        TestDescriptor descriptor = new TestDescriptor();

        GeneralCommandLine cmdNull = descriptor.createStartServerCommandLine(
                null,
                "node server.js",
                Collections.emptyMap(),
                false
        );
        Assert.assertNull(cmdNull.getWorkDirectory());

        GeneralCommandLine cmdBlank = descriptor.createStartServerCommandLine(
                "   ",
                "node server.js",
                Collections.emptyMap(),
                false
        );
        Assert.assertNull(cmdBlank.getWorkDirectory());
    }

    @Test
    public void testTheCommandLineOfACommandAloneInheritsTheIdeEnvironment() throws Exception {
        // createStartServerCommandLine(command) passes no system variables, and its process inherits them, as on main
        GeneralCommandLine cmd = new TestDescriptor().createStartServerCommandLine("node server.js");

        Assert.assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, cmd.getParentEnvironmentType());
        Assert.assertNotNull(cmd.getEffectiveEnvironment().get("PATH"));
    }

    @Test
    public void testCreateStartServerCommandLineReplacesPortMacro() throws Exception {
        TestDescriptor descriptor = new TestDescriptor();
        GeneralCommandLine cmd = descriptor.createStartServerCommandLine(
                null,
                "node server.js --port=${port}",
                Collections.emptyMap(),
                false
        );

        Integer port = cmd.getUserData(DebugAdapterDescriptor.SERVER_PORT);
        Assert.assertNotNull(port);
        Assert.assertTrue("Command should contain the assigned port",
                cmd.getCommandLineString().contains("--port=" + port));
    }

    @Test(expected = ExecutionException.class)
    public void testCreateStartServerCommandLineThrowsOnBlankCommand() throws Exception {
        TestDescriptor descriptor = new TestDescriptor();
        descriptor.createStartServerCommandLine(null, "   ", Collections.emptyMap(), false);
    }
}
