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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests the lookup of the executable of a {@link WorkingDirectoryCommandLine} as its process starts.
 */
public class WorkingDirectoryCommandLineTest {

    @Test
    public void testTheExecutableIsTheOneOfTheEnvironmentWhichTheCustomizersSetUpOnceForTheStart() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        Path other = Files.createTempDirectory("other-tool-dir");
        try {
            Path tool = CommandUtilsTest.createTool(directory, "working-directory");
            // The same command in the PATH given to the command line, which the platform looks it up in
            CommandUtilsTest.createTool(other, "given-path");
            AtomicInteger customizations = new AtomicInteger();
            GeneralCommandLine commandLine = CommandUtilsTest.customizedCommandLine(List.of("lsp4ij-tool"), directory, customizations)
                    .withEnvironment("PATH", other + File.pathSeparator + Objects.requireNonNullElse(System.getenv("PATH"), ""))
                    .withWorkDirectory(directory.toFile());

            String output = CommandUtilsTest.run(commandLine);

            Assertions.assertEquals("working-directory", output);
            Assertions.assertEquals(1, customizations.get(), "The customizers run once for the start");
            CommandUtilsTest.assertSameFile(tool, new File(commandLine.getExePath()));
        } finally {
            FileUtil.delete(directory.toFile());
            FileUtil.delete(other.toFile());
        }
    }

    @Test
    public void testTheExecutableThatIsLookedUpGetsTheArguments() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        try {
            Path tool = directory.resolve(SystemInfo.isWindows ? "lsp4ij-tool.bat" : "lsp4ij-tool");
            Files.writeString(tool, SystemInfo.isWindows ? "@echo off\r\necho %~1" : "#!/bin/sh\necho \"$1\"");
            Assertions.assertTrue(tool.toFile().setExecutable(true));
            GeneralCommandLine commandLine = CommandUtilsTest.customizedCommandLine(List.of("lsp4ij-tool", "an argument"),
                            directory, new AtomicInteger())
                    .withWorkDirectory(directory.toFile());

            Assertions.assertEquals("an argument", CommandUtilsTest.run(commandLine));
        } finally {
            FileUtil.delete(directory.toFile());
        }
    }
}
