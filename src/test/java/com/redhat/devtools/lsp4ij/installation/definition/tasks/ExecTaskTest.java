/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.util.SystemProperties;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskRegistry;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the installers of two language servers which install into the same directory, as the CSS and ESLint
 * language servers, which share their npm package, run their commands there one at a time, also in two IDEs, and that
 * the one which waited doesn't install again once the other one installed the server (#1960). Also tests that the
 * tests keep the lock files of their working directories, which are temp folders, in a temp folder, and not in
 * {@code ~/.lsp4ij/locks} (#2140).
 */
public class ExecTaskTest {

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    @Test
    public void testTwoInstallersWhichRunACommandInTheSameDirectoryRunThereOneAtATime() throws Exception {
        Path directory = Files.createTempDirectory("lsp4ij-exec");
        // Each command holds the same file until the test releases it, and fails if the other one holds it
        String held = directory.resolve("installing").toString();
        Path release = directory.resolve("release");
        var start = new CountDownLatch(1);
        List<Installation> installations = IntStream.range(0, 2)
                .mapToObj(i -> Installation.start(() -> {
                    start.await();
                    var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
                    return createTask(java(HoldFile.class.getName(), held, release.toString()), directory, descriptor())
                            .run(context);
                }))
                .toList();
        try {
            start.countDown();
            // While the command of one of them holds the file, the other one waits
            awaitOneWaits(installations);
            release(release);
            List<Boolean> results = new ArrayList<>();
            for (var installation : installations) {
                results.add(installation.result().get(60, TimeUnit.SECONDS));
            }
            assertEquals(List.of(true, true), results, "the two commands must not run in the directory at the same time");
        } finally {
            release(release);
            NioFiles.deleteRecursively(directory);
        }
    }

    @Test
    public void testAnInstallationWhichWaitedForAnotherOneDoesNotRunItsCommandIfTheServerIsInstalledThen() throws Exception {
        assertTrue(installAfterAnotherInstallation(true), "the command must not run, since the check passes");
    }

    @Test
    public void testAForcedInstallationWhichWaitedForAnotherOneRunsItsCommand() throws Exception {
        assertFalse(installAfterAnotherInstallation(false), "the command must run, as for the Reinstall action");
    }

    @Test
    public void testAnInstallerWaitsForTheInstallerOfAnotherIDEWhichRunsACommandInTheSameDirectory() throws Exception {
        Path directory = Files.createTempDirectory("lsp4ij-exec");
        String held = directory.resolve("installing").toString();
        Path release = directory.resolve("release");
        Process otherIDE = new ProcessBuilder(java(HoldLock.class.getName(), ExecTask.getLockFile(directory).toString(), held,
                release.toString()))
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            var output = new BufferedReader(new InputStreamReader(otherIDE.getInputStream(), StandardCharsets.UTF_8));
            assertEquals("locked", assertTimeoutPreemptively(Duration.ofSeconds(30), output::readLine,
                    "the other IDE must start"), "the other IDE must hold the lock file of the directory");
            // The command fails if it runs while the other IDE holds the file
            var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
            var installation = Installation.start(() -> createTask(java(HoldFile.class.getName(), held, release.toString()),
                    directory, descriptor()).run(context));
            awaitOneWaits(List.of(installation));
            // The other IDE leaves the directory
            release(release);
            assertEquals(0, otherIDE.onExit().get(60, TimeUnit.SECONDS).exitValue(), "the other IDE must leave the directory");
            assertTrue(installation.result().get(60, TimeUnit.SECONDS), "the command must wait until the other IDE leaves the directory");
        } finally {
            release(release);
            otherIDE.destroy();
            NioFiles.deleteRecursively(directory);
        }
    }

    @Test
    public void testTheTestsKeepTheLockFilesOfTheirWorkingDirectoriesInATempFolderAndNotInTheHomeOfTheUser() throws Exception {
        Path directory = Files.createTempDirectory("lsp4ij-exec");
        try {
            // Whatever its command does, the exec task locks its working directory first, with the lock file
            var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
            createTask(java("NoSuchProgram"), directory, descriptor()).run(context);
            Path lockFile = ExecTask.getLockFile(directory);
            assertTrue(Files.exists(lockFile), "the exec task must lock its working directory with " + lockFile);
            Path lockFilesOfTheUser = Path.of(SystemProperties.getUserHome(), ".lsp4ij", "locks");
            assertFalse(lockFile.startsWith(lockFilesOfTheUser),
                    "the lock files of the tests, whose working directories are temp folders, must not pile up in " +
                            lockFilesOfTheUser + " (#2140): " + lockFile);
            Path tempFolder = Path.of(System.getProperty("java.io.tmpdir"));
            assertTrue(lockFile.startsWith(tempFolder),
                    "the lock files of the tests must be in a temp folder, in " + tempFolder + ": " + lockFile);
        } finally {
            NioFiles.deleteRecursively(directory);
        }
    }

    /**
     * Runs an installation into a directory while another installation runs there and installs the server, and returns
     * the result of the one that waited, whose command fails if it runs: true if it ran the check of the installer
     * again, and false if it ran its command.
     */
    private static boolean installAfterAnotherInstallation(boolean checkAgainAfterWaiting) throws Exception {
        Path directory = Files.createTempDirectory("lsp4ij-exec");
        // The command of the other installation holds the file until the test releases it
        String held = directory.resolve("installing").toString();
        Path release = directory.resolve("release");
        var other = Installation.start(() -> createTask(java(HoldFile.class.getName(), held, release.toString()), directory,
                descriptor()).run(new InstallerContext(null, InstallerContext.InstallerAction.RUN)));
        try {
            for (long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30); !Files.exists(Path.of(held)); ) {
                assertTrue(System.nanoTime() < end, "the other installation must start");
                Thread.sleep(10);
            }
            // The other installation installs the server, which the check of the installer finds
            Path installed = Files.createFile(directory.resolve("installed"));
            var descriptor = descriptor();
            descriptor.setCheck(new FileExistsTask(null, "check", null, null, installed.toString(), descriptor));
            var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
            context.setCheckAgainAfterWaiting(checkAgainAfterWaiting);
            var installation = Installation.start(() -> createTask(java("NoSuchProgram"), directory, descriptor).run(context));
            awaitOneWaits(List.of(installation));
            // The other installation ends
            release(release);
            assertTrue(other.result().get(60, TimeUnit.SECONDS), "the other installation must succeed");
            return installation.result().get(60, TimeUnit.SECONDS);
        } finally {
            release(release);
            NioFiles.deleteRecursively(directory);
        }
    }

    /**
     * Waits until one of the given installations waits for the working directory of its exec task, which another
     * installation holds, and fails if one of them ends first: since the command of the other installation holds the
     * directory until the test releases it, an installation which ends meanwhile ran its command without waiting.
     */
    private static void awaitOneWaits(@NotNull List<Installation> installations) throws InterruptedException {
        for (long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
             installations.stream().noneMatch(Installation::waitsForTheWorkingDir); ) {
            assertTrue(installations.stream().noneMatch(installation -> installation.result().isDone()),
                    "the installation must wait for the one which holds the working directory, but it ended without waiting");
            assertTrue(System.nanoTime() < end, "the installation must wait for the one which holds the working directory");
            Thread.sleep(10);
        }
    }

    /**
     * Releases the commands which hold their file until the given file exists, as {@link HoldFile} and {@link HoldLock}:
     * the installations which run them end.
     */
    private static void release(@NotNull Path release) throws IOException {
        if (!Files.exists(release)) {
            Files.createFile(release);
        }
    }

    private static @NotNull ServerInstallerDescriptor descriptor() {
        return new ServerInstallerDescriptor("test", false, new InstallerTaskRegistry());
    }

    /**
     * Returns an exec task which runs the given command in the given working directory.
     */
    private static @NotNull ExecTask createTask(@NotNull List<String> command,
                                                @NotNull Path workingDir,
                                                @NotNull ServerInstallerDescriptor descriptor) {
        return new ExecTask(null, "install", null, null, command, workingDir.toString(), null, true, descriptor);
    }

    /**
     * Returns the command which runs the given main class of the test classes with the given arguments.
     */
    private static @NotNull List<String> java(@NotNull String mainClass, @NotNull String... args) throws Exception {
        String java = ProcessHandle.current().info().command().orElseThrow();
        // The directory of the compiled test classes, above the folders of the package of HoldFile
        Path classes = Path.of(HoldFile.class.getResource("HoldFile.class").toURI());
        for (int i = HoldFile.class.getName().split("\\.").length; i > 0; i--) {
            classes = classes.getParent();
        }
        return Stream.concat(Stream.of(java, "-cp", classes.toString(), mainClass), Stream.of(args)).toList();
    }

    /**
     * An installation which runs on its own thread, and its result.
     *
     * @param thread the thread of the installation.
     * @param result the result of the installation.
     */
    private record Installation(@NotNull Thread thread, @NotNull Future<Boolean> result) {

        /**
         * Starts the given installation on a new thread.
         */
        static @NotNull Installation start(@NotNull Callable<Boolean> installation) {
            var result = new FutureTask<>(installation);
            var thread = new Thread(result, "ExecTaskTest installation");
            thread.setDaemon(true);
            thread.start();
            return new Installation(thread, result);
        }

        /**
         * Returns true if the installation waits for the working directory of its exec task, which another one holds,
         * in this IDE or in another one: an exec task waits for it in {@code ExecTask.await}, and only then.
         */
        boolean waitsForTheWorkingDir() {
            return Arrays.stream(thread.getStackTrace())
                    .anyMatch(frame -> frame.getClassName().equals(ExecTask.class.getName())
                            && frame.getMethodName().equals("await"));
        }
    }
}
