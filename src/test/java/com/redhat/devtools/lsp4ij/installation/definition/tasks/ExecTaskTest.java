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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
        try (var workingDir = new WorkingDir()) {
            // Each command holds the same file until the test releases it, and fails if the other one holds it
            var start = new CountDownLatch(1);
            List<Installation> installations = IntStream.range(0, 2)
                    .mapToObj(i -> workingDir.startInstallation(() -> {
                        start.await();
                        return workingDir.holdFileTask().run(new InstallerContext(null, InstallerContext.InstallerAction.RUN));
                    }))
                    .toList();
            start.countDown();
            // While the command of one of them holds the file, the other one waits
            awaitOneWaits(installations);
            workingDir.release();
            List<Boolean> results = new ArrayList<>();
            for (var installation : installations) {
                results.add(installation.result().get(60, TimeUnit.SECONDS));
            }
            assertEquals(List.of(true, true), results, "the two commands must not run in the directory at the same time");
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
        try (var workingDir = new WorkingDir()) {
            Process otherIDE = workingDir.startOtherIDE();
            var output = new BufferedReader(new InputStreamReader(otherIDE.getInputStream(), StandardCharsets.UTF_8));
            assertEquals("locked", assertTimeoutPreemptively(Duration.ofSeconds(30), output::readLine,
                    "the other IDE must start"), "the other IDE must hold the lock file of the directory");
            // The command fails if it runs while the other IDE holds the file
            var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
            var installation = workingDir.startInstallation(() -> workingDir.holdFileTask().run(context));
            awaitOneWaits(List.of(installation));
            // The other IDE leaves the directory
            workingDir.release();
            assertEquals(0, otherIDE.onExit().get(60, TimeUnit.SECONDS).exitValue(), "the other IDE must leave the directory");
            assertTrue(installation.result().get(60, TimeUnit.SECONDS), "the command must wait until the other IDE leaves the directory");
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
        try (var workingDir = new WorkingDir()) {
            // The command of the other installation holds the file until the test releases it
            var other = workingDir.startInstallation(() -> workingDir.holdFileTask()
                    .run(new InstallerContext(null, InstallerContext.InstallerAction.RUN)));
            for (long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30); !Files.exists(workingDir.held); ) {
                assertTrue(System.nanoTime() < end, "the other installation must start");
                Thread.sleep(10);
            }
            // The other installation installs the server, which the check of the installer finds
            Path installed = Files.createFile(workingDir.directory.resolve("installed"));
            var descriptor = descriptor();
            descriptor.setCheck(new FileExistsTask(null, "check", null, null, installed.toString(), descriptor));
            var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
            context.setCheckAgainAfterWaiting(checkAgainAfterWaiting);
            var installation = workingDir.startInstallation(() -> createTask(java("NoSuchProgram"), workingDir.directory,
                    descriptor).run(context));
            awaitOneWaits(List.of(installation));
            // The other installation ends
            workingDir.release();
            assertTrue(other.result().get(60, TimeUnit.SECONDS), "the other installation must succeed");
            return installation.result().get(60, TimeUnit.SECONDS);
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
     * A temp folder in which a test runs exec tasks, with the installations and the other IDEs which it starts there.
     * Closing it releases their commands, which hold their file until the test releases them, waits until they all
     * ended, within one bound, and only then deletes the folder: a command which runs there must find the file which
     * releases it, else it runs on for a minute, into the next tests, and on Windows a folder in which a program still
     * runs can't be deleted. A failure of the test stays its failure, with those of the cleanup suppressed in it.
     */
    private static final class WorkingDir implements AutoCloseable {

        // Bounds the cleanup, from the release of the commands until they all ended
        private static final long CLEANUP_NANOS = TimeUnit.SECONDS.toNanos(30);
        // Bounds the wait for the end of another IDE which the cleanup kills, since it didn't end once released
        private static final long KILL_SECONDS = 10;

        private final @NotNull Path directory;
        // The file which the commands and the other IDEs hold until the test releases them
        private final @NotNull Path held;
        // The file whose creation releases them
        private final @NotNull Path release;
        private final List<Installation> installations = new ArrayList<>();
        private final List<Process> otherIDEs = new ArrayList<>();

        WorkingDir() throws IOException {
            directory = Files.createTempDirectory("lsp4ij-exec");
            held = directory.resolve("installing");
            release = directory.resolve("release");
        }

        /**
         * Starts the given installation on a new thread, which the cleanup waits for.
         */
        @NotNull Installation startInstallation(@NotNull Callable<Boolean> installation) {
            var started = Installation.start(installation);
            installations.add(started);
            return started;
        }

        /**
         * Starts another IDE, {@link HoldLock}, which locks the lock file of this directory and holds {@link #held}
         * until the test releases it, and which the cleanup waits for.
         */
        @NotNull Process startOtherIDE() throws Exception {
            Process otherIDE = new ProcessBuilder(java(HoldLock.class.getName(), ExecTask.getLockFile(directory).toString(),
                    held.toString(), release.toString()))
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            otherIDEs.add(otherIDE);
            return otherIDE;
        }

        /**
         * Returns an exec task which runs {@link HoldFile} in this directory: its command holds {@link #held} until the
         * test releases it, and fails if another command or IDE holds it.
         */
        @NotNull ExecTask holdFileTask() throws Exception {
            return createTask(java(HoldFile.class.getName(), held.toString(), release.toString()), directory, descriptor());
        }

        /**
         * Releases the commands and the other IDEs which hold {@link #held} until the test releases them: the
         * installations which run them end.
         */
        void release() throws IOException {
            if (!Files.exists(release)) {
                Files.createFile(release);
            }
        }

        @Override
        public void close() throws Exception {
            List<Exception> failures = new ArrayList<>();
            long end = System.nanoTime() + CLEANUP_NANOS;
            try {
                release();
            } catch (IOException e) {
                failures.add(e);
            }
            for (var installation : installations) {
                try {
                    installation.result().get(remainingNanos(end), TimeUnit.NANOSECONDS);
                } catch (ExecutionException e) {
                    // It ended, which is all the cleanup needs: the test asserts its result
                } catch (TimeoutException e) {
                    installation.thread().interrupt();
                    failures.add(new IllegalStateException("The installation must end once released, but it runs at "
                            + Arrays.toString(installation.thread().getStackTrace()), e));
                }
            }
            for (var otherIDE : otherIDEs) {
                if (!otherIDE.waitFor(remainingNanos(end), TimeUnit.NANOSECONDS)) {
                    otherIDE.destroyForcibly();
                    failures.add(new IllegalStateException("The other IDE must end once released, but it runs on"
                            + (otherIDE.waitFor(KILL_SECONDS, TimeUnit.SECONDS) ? ": it was killed" : ", also once killed")));
                }
            }
            try {
                NioFiles.deleteRecursively(directory);
            } catch (IOException e) {
                failures.add(e);
            }
            if (!failures.isEmpty()) {
                Exception failure = failures.get(0);
                failures.stream().skip(1).forEach(failure::addSuppressed);
                throw failure;
            }
        }

        private static long remainingNanos(long end) {
            return Math.max(0, end - System.nanoTime());
        }
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
