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
package com.redhat.devtools.lsp4ij.dap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.dap.installation.DeclarativeDebugAdapterServerInstaller;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationStatus;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.PrintOutput;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The installer of a debug adapter which a run configuration starts gets the project of the run configuration, as the
 * installer of a language server gets the project of its client: the commands of its {@code exec} tasks without a
 * {@code workingDir} run in the directory of the project, where the tools that the project selects, as with mise, are
 * found, and not in the directory of the IDE, as {@code /} for an IDE started from the Dock (#2295). A
 * {@code workingDir} of the installer still wins.
 */
public class DAPInstallerProjectTest extends HeavyPlatformTestCase {

    private static final int TIMEOUT_SECONDS = 60;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The directory of the project, which isn't on disk until it is created
        getOrCreateProjectBaseDir();
    }

    public void testTheInstallerOfADebugAdapterRunsItsCommandsInTheDirectoryOfTheProjectOfTheRunConfiguration()
            throws Exception {
        var installer = new TestInstaller(null);

        assertEquals(ServerInstallationStatus.INSTALLED, check(installer));

        assertEquals("The command runs in the directory of the project", realPath(projectDirectory().getPath()),
                installer.directory());
        assertSame("The installer runs for the project of the run configuration", getProject(), installer.project());
    }

    public void testAWorkingDirOfTheInstallerWins() throws Exception {
        Path workingDir = createTempDirectory().toPath();
        var installer = new TestInstaller(workingDir);

        assertEquals(ServerInstallationStatus.INSTALLED, check(installer));

        assertSame(getProject(), installer.project());
        assertEquals(realPath(workingDir.toString()), installer.directory());
    }

    /**
     * Checks the installation with the given installer, as a DAP run configuration of the project starts it, and
     * returns how it ended.
     */
    private @NotNull ServerInstallationStatus check(@NotNull TestInstaller installer) throws Exception {
        var installation = DAPDebugRunner.checkInstallation(installer, getProject());
        PlatformTestUtil.waitWithEventsDispatching("The check of the installation must end within " + TIMEOUT_SECONDS
                + " s", installation::isDone, TIMEOUT_SECONDS);
        return installation.get();
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
     * Returns the installer whose check runs {@link PrintOutput}, which prints the directory it runs in, in the given
     * working directory, or without one.
     */
    private static @NotNull ServerInstallerDescriptor loadInstaller(@Nullable Path workingDir) {
        var command = new JsonArray();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(PathManager.getJarPathForClass(PrintOutput.class));
        command.add(PrintOutput.class.getName());
        command.add("cwd");
        var exec = new JsonObject();
        exec.addProperty("name", "Print the directory");
        exec.add("command", command);
        if (workingDir != null) {
            exec.addProperty("workingDir", workingDir.toString());
        }
        // Where the JVM can say that it picked up JAVA_TOOL_OPTIONS
        exec.addProperty("ignoreStderr", true);
        var check = new JsonObject();
        check.add("exec", exec);
        var installer = new JsonObject();
        installer.addProperty("name", "Test debug adapter");
        installer.add("check", check);
        return ServerInstallerManager.getInstance().loadInstaller(installer);
    }

    /**
     * The installer of a debug adapter, which keeps the contexts it runs with, and what they print.
     */
    private static final class TestInstaller extends DeclarativeDebugAdapterServerInstaller {

        private final @NotNull ServerInstallerDescriptor descriptor;
        private final List<InstallerContext> contexts = new CopyOnWriteArrayList<>();
        private final List<String> printed = new CopyOnWriteArrayList<>();

        TestInstaller(@Nullable Path workingDir) {
            this.descriptor = loadInstaller(workingDir);
        }

        @Override
        protected @Nullable ServerInstallerDescriptor getServerInstallerDescriptor() {
            return descriptor;
        }

        @Override
        protected boolean canExecute(@NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
            // As a user-defined debug adapter, which checks its installation at its first debug session
            return true;
        }

        @Override
        protected @NotNull InstallerContext createInstallerContext(@NotNull InstallerContext.InstallerAction action,
                                                                   @NotNull ProgressIndicator indicator) {
            var context = super.createInstallerContext(action, indicator);
            contexts.add(context);
            // What the context prints goes to its progress indicator too, as to its consoles
            context.setProgressIndicator(new EmptyProgressIndicator() {
                @Override
                public void setText2(@Nullable String text) {
                    if (text != null) {
                        printed.add(text);
                    }
                }
            });
            return context;
        }

        /**
         * Returns the project of the context of the check.
         */
        @Nullable Project project() {
            assertSize(1, contexts);
            return contexts.get(0).getProject();
        }

        /**
         * Returns the directory in which the command of the check ran, as it printed it.
         */
        @NotNull Path directory() throws Exception {
            String output = String.join("\n", printed);
            String directory = output.lines()
                    .filter(line -> line.startsWith("cwd="))
                    .map(line -> line.substring("cwd=".length()))
                    .findFirst()
                    .orElse(null);
            assertNotNull("The command printed the directory it ran in: " + output, directory);
            return realPath(directory);
        }
    }
}
