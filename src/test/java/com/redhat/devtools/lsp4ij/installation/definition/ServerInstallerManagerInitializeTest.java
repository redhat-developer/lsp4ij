/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.ConfigureServerTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.DownloadTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.ExecTask;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.ExecTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.FileExistsTaskFactory;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.InitializeTask;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.InitializeTestServer;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.ShowMessageTaskFactory;
import com.redhat.devtools.lsp4ij.launching.ui.UICommandLineUpdater;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests the check of an installer as the bundled installers have it, an {@code exec} of {@code ${server.command}}
 * whose {@code onSuccess} is {@code initialize}, as LSP4IJ runs it for Install if Needed: the plugin registers the
 * task, the check passes only if the server answers initialize, and an LSP4IJ which doesn't know the task checks with
 * the {@code exec} alone, as before (#1957).
 */
public class ServerInstallerManagerInitializeTest extends BasePlatformTestCase {

    private static final String INSTALLER = """
            {
              "name": "my-server",
              "check": {
                "exec": {
                  "name": "Trying current command",
                  "command": "${server.command}",
                  "timeout": 2000,
                  "onSuccess": {
                    "initialize": {
                      "name": "Check that my-server starts",
                      "timeout": 5000
                    }
                  }
                }
              },
              "run": {
                "exec": {
                  "name": "Install my-server",
                  "command": "npm install my-server"
                }
              }
            }
            """;

    private Path record;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        record = Files.createTempFile("lsp4ij-initialize", ".txt");
        Files.delete(record);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Files.deleteIfExists(record);
        } finally {
            super.tearDown();
        }
    }

    @Override
    protected boolean runInDispatchThread() {
        // As the check of an installation, on a background thread
        return false;
    }

    public void testThePluginRegistersTheInitializeTask() {
        var check = ServerInstallerManager.getInstance().loadInstaller(INSTALLER).getCheck();
        assertInstanceOf(check, ExecTask.class);
        assertInstanceOf(check.getOnSuccess(), InitializeTask.class);
    }

    public void testTheCheckOfALanguageServerWhichCannotBeStartedFails() {
        assertFalse("the server isn't installed, so that Install if Needed installs it",
                check(ServerInstallerManager.getInstance(), "lsp4ij-no-such-language-server --stdio"));
    }

    public void testTheCheckOfALanguageServerWhichAnswersInitializePasses() throws Exception {
        assertTrue("the command runs, and the server answers initialize",
                check(ServerInstallerManager.getInstance(), InitializeTestServer.commandLine("lsp", "answer", record)));
        assertEquals(List.of("initialize", "shutdown", "exit"), received());
        assertGone();
    }

    public void testTheCheckOfALanguageServerWhichRunsButDoesNotAnswerInitializeFails() throws Exception {
        assertFalse("the command still runs at the timeout of the exec, but the server doesn't answer initialize",
                check(ServerInstallerManager.getInstance(), InitializeTestServer.commandLine("lsp", "silent", record)));
        assertEquals(List.of("initialize"), received());
        assertGone();
    }

    public void testAnLSP4IJWithoutTheInitializeTaskChecksWithTheExecAloneAsBefore() throws Exception {
        var registry = new InstallerTaskRegistry();
        registry.registerFactory("fileExists", new FileExistsTaskFactory());
        registry.registerFactory("exec", new ExecTaskFactory());
        registry.registerFactory("download", new DownloadTaskFactory());
        registry.registerFactory("configureServer", new ConfigureServerTaskFactory());
        registry.registerFactory("showMessage", new ShowMessageTaskFactory());

        assertTrue("the command still runs at the timeout of the exec, as before",
                check(registry, InitializeTestServer.commandLine("lsp", "silent", record)));
        assertEquals("initialize isn't sent", List.of(), received());
        assertFalse("a command which doesn't exist fails, as before",
                check(registry, "lsp4ij-no-such-language-server --stdio"));
    }

    /**
     * Runs the check of the installer, loaded with the given registry, for a language server with the given command, as
     * Install if Needed does, and returns its result.
     */
    private boolean check(@NotNull InstallerTaskRegistry registry, @NotNull String command) {
        var server = new UserDefinedLanguageServerDefinition("initialize-test", "Initialize test", null, command, Map.of(),
                true, null, null);
        var installer = registry.loadInstaller(INSTALLER);
        // Without the light project, whose folder isn't on the disk, where the exec would run its command
        var context = new InstallerContext(null, InstallerContext.InstallerAction.CHECK);
        context.setProgressIndicator(new EmptyProgressIndicator());
        context.setCommandLineUpdater(new UICommandLineUpdater(server, null));
        context.setShowNotification(false);
        return ServerInstallerManager.getInstance().install(installer, context);
    }

    /**
     * Returns the methods which the test servers got.
     */
    private @NotNull List<String> received() throws Exception {
        if (!Files.exists(record)) {
            return List.of();
        }
        return Files.readAllLines(record).stream().filter(line -> !line.startsWith("pid ")).toList();
    }

    /**
     * Asserts that the processes of the test servers, that of the exec and that of initialize, are gone.
     */
    private void assertGone() throws Exception {
        List<Long> pids = Files.readAllLines(record).stream()
                .filter(line -> line.startsWith("pid "))
                .map(line -> Long.parseLong(line.substring("pid ".length())))
                .toList();
        assertFalse("the servers must have started", pids.isEmpty());
        // Each process is awaited until it exits, within one bound for all of them
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        for (long pid : pids) {
            var process = ProcessHandle.of(pid);
            if (process.isEmpty()) {
                continue;
            }
            try {
                process.get().onExit().get(Math.max(0, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                fail("the process " + pid + " must be gone");
            }
        }
    }
}
