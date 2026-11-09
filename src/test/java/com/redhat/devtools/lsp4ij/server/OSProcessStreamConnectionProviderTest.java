/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtilsTest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Regression tests for process ownership and terminal stop semantics.
 */
public class OSProcessStreamConnectionProviderTest extends LightPlatformTestCase {

    /**
     * Models shutdown after the wrapper publishes a provider but before its startup task runs.
     */
    public void testStopBeforeStartRejectsProcessCreation() throws Exception {
        var provider = provider();
        try {
            provider.stop();
            assertThrows(CannotStartProcessException.class, provider::start);
            assertNull(provider.getPid());
        } finally {
            reap(provider);
        }
    }

    /**
     * Checks actual child termination and that stopping remains idempotent and terminal.
     */
    public void testStartedProcessIsReapedByStop() throws Exception {
        var provider = provider();
        try {
            provider.start();
            Process process = provider.getProcessHandler().getProcess();
            assertTrue(process.isAlive());
            provider.stop();
            awaitExit(process);
            assertFalse(process.isAlive());
            provider.stop();
            assertThrows(CannotStartProcessException.class, provider::start);
        } finally {
            reap(provider);
        }
    }

    /**
     * Ensures a rejected second start preserves ownership of the first child process.
     */
    public void testRepeatedStartCannotReplaceAnOwnedProcess() throws Exception {
        var provider = provider();
        Process first = null;
        try {
            provider.start();
            first = provider.getProcessHandler().getProcess();
            assertThrows(CannotStartProcessException.class, provider::start);
            assertSame(first, provider.getProcessHandler().getProcess());
        } finally {
            try {
                reap(provider);
            } finally {
                if (first != null && first.isAlive()) {
                    first.destroyForcibly();
                    awaitExit(first);
                }
            }
        }
    }

    /**
     * A command line that a plugin creates with a working directory starts the tool which that directory's environment
     * selects, as a user-defined language server's does.
     */
    public void testStartLooksUpTheCommandInTheEnvironmentOfItsWorkingDirectory() throws Exception {
        Path directory = Files.createTempDirectory("tool-dir");
        Path other = Files.createTempDirectory("other-tool-dir");
        OSProcessStreamConnectionProvider provider = null;
        try {
            Path tool = CommandUtilsTest.createTool(directory, "working-directory");
            // The same command in the PATH given to the command line, as the IDE's PATH can have it
            CommandUtilsTest.createTool(other, "given-path");
            String givenPath = other + File.pathSeparator + Objects.requireNonNullElse(System.getenv("PATH"), "");
            provider = new OSProcessStreamConnectionProvider(CommandUtilsTest.withDirectoryInPath(
                    new GeneralCommandLine(tool.getFileName().toString()).withEnvironment("PATH", givenPath), directory)
                    .withWorkDirectory(directory.toFile()));

            provider.start();

            assertEquals(tool.toFile().getCanonicalPath(), new File(provider.getCommandLine().getExePath()).getCanonicalPath());
            // The tool of the working directory is the one that runs
            assertEquals("working-directory", readLine(provider));
        } finally {
            if (provider != null) {
                reap(provider);
            }
            FileUtil.delete(directory.toFile());
            FileUtil.delete(other.toFile());
        }
    }

    /**
     * Reads the first line that the process of the provider prints; the deadline only prevents a broken test from
     * hanging CI.
     */
    private static String readLine(OSProcessStreamConnectionProvider provider) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(provider.getInputStream(), StandardCharsets.UTF_8));
        return CompletableFuture.supplyAsync(() -> {
            try {
                return reader.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).get(30, TimeUnit.SECONDS);
    }

    /**
     * Uses the test JVM to run a quiet child without an external language server installation.
     */
    private static OSProcessStreamConnectionProvider provider() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = PathManager.getJarPathForClass(WaitingServer.class);
        return new OSProcessStreamConnectionProvider(
                new GeneralCommandLine(java, "-cp", classes, WaitingServer.class.getName()));
    }

    /**
     * Releases the tracked child, including orphans produced when testing the unfixed provider.
     */
    private static void reap(OSProcessStreamConnectionProvider provider) throws Exception {
        provider.stop();
        var handler = provider.getProcessHandler();
        if (handler != null) {
            var process = handler.getProcess();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            awaitExit(process);
        }
    }

    /**
     * Waits for actual termination; the deadline only prevents a broken test from hanging CI.
     */
    private static void awaitExit(Process process) throws Exception {
        process.onExit().get(30, TimeUnit.SECONDS);
    }

    public static class WaitingServer {
        /**
         * Waits for input to keep the child alive while the provider's lifecycle is exercised.
         */
        public static void main(String[] args) throws Exception {
            System.in.read();
        }
    }
}
