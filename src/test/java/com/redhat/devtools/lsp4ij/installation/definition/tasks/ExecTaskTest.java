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
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that the installers of two language servers which install into the same directory, as the CSS and ESLint
 * language servers, which share their npm package, run their commands there one at a time, also in two IDEs, and that
 * the one which waited doesn't install again once the other one installed the server (#1960). Also tests that the
 * tests keep the lock files of their working directories, which are temp folders, in a temp folder, and not in
 * {@code ~/.lsp4ij/locks} (#2140).
 */
public class ExecTaskTest {

    // Bounds each wait of the tests for an installation, or its command, to reach a state
    private static final long WAIT_NANOS = TimeUnit.SECONDS.toNanos(30);
    // How often the tests look at that state, while they wait for the installation to end
    private static final long POLL_MILLIS = 10;

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    @Test
    public void testTwoInstallersWhichRunACommandInTheSameDirectoryRunThereOneAtATime() throws Exception {
        try (var workingDir = new WorkingDir()) {
            // Each command holds the same file until the test releases it, and fails if the other one holds it
            var first = workingDir.startInstallation(() -> workingDir.holdFileTask()
                    .run(new InstallerContext(null, InstallerContext.InstallerAction.RUN)));
            awaitHolds(first, workingDir.held);
            // Only then the second one starts: it must wait for the directory while the command of the first one holds
            // the file, else its command would run then, and fail
            var second = workingDir.startInstallation(() -> workingDir.holdFileTask()
                    .run(new InstallerContext(null, InstallerContext.InstallerAction.RUN)));
            awaitWaits(second, "the command of the first installation",
                    () -> !first.result().isDone() && Files.exists(workingDir.held));
            workingDir.release();
            List<Boolean> results = List.of(first.result().get(60, TimeUnit.SECONDS), second.result().get(60, TimeUnit.SECONDS));
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
            awaitWaits(installation, "the other IDE", () -> otherIDE.isAlive() && Files.exists(workingDir.held));
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
            awaitHolds(other, workingDir.held);
            // The other installation installs the server, which the check of the installer finds
            Path installed = Files.createFile(workingDir.directory.resolve("installed"));
            var descriptor = descriptor();
            descriptor.setCheck(new FileExistsTask(null, "check", null, null, installed.toString(), descriptor));
            var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
            context.setCheckAgainAfterWaiting(checkAgainAfterWaiting);
            var installation = workingDir.startInstallation(() -> createTask(java("NoSuchProgram"), workingDir.directory,
                    descriptor).run(context));
            awaitWaits(installation, "the command of the other installation",
                    () -> !other.result().isDone() && Files.exists(workingDir.held));
            // The other installation ends
            workingDir.release();
            assertTrue(other.result().get(60, TimeUnit.SECONDS), "the other installation must succeed");
            return installation.result().get(60, TimeUnit.SECONDS);
        }
    }

    /**
     * Waits until the command of the given installation holds the given file, which it holds until the test releases
     * it, and fails with the result or the failure of the installation if it ends first, since its command then failed.
     */
    private static void awaitHolds(@NotNull Installation installation, @NotNull Path held) throws Exception {
        for (long end = System.nanoTime() + WAIT_NANOS; !Files.exists(held); ) {
            Optional<Boolean> result = installation.awaitResult(POLL_MILLIS);
            if (result.isPresent()) {
                fail("the command of the installation must hold " + held + " until the test releases it, but the "
                        + "installation ended with " + result.get());
            }
            assertTrue(System.nanoTime() < end, "the command of the installation must hold " + held);
        }
    }

    /**
     * Waits until the given installation waits for the working directory of its exec task, while the given holder, the
     * command of another installation or another IDE, holds the directory and its file until the test releases them.
     * Fails if the installation ends first, since it then ran its command without waiting, or if the holder doesn't
     * hold its file when the installation waits, since the installation then had nothing to wait for.
     */
    private static void awaitWaits(@NotNull Installation installation,
                                   @NotNull String holder,
                                   @NotNull BooleanSupplier holderHoldsItsFile) throws Exception {
        for (long end = System.nanoTime() + WAIT_NANOS; ; ) {
            // Looked at before the holder, which holds its file from before the installation started until the test
            // releases it: if the holder still holds it, it held it when the installation waited
            boolean waits = installation.waitsForTheWorkingDir();
            assertTrue(holderHoldsItsFile.getAsBoolean(), holder + " must hold its file until the test releases it");
            if (waits) {
                return;
            }
            Optional<Boolean> result = installation.awaitResult(POLL_MILLIS);
            if (result.isPresent()) {
                fail("the installation must wait for " + holder + ", but it ended without waiting, with " + result.get());
            }
            assertTrue(System.nanoTime() < end, "the installation must wait for " + holder);
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
         * Waits for the result of the installation for at most the given time, and returns it if the installation
         * ended, or nothing if it still runs.
         */
        @NotNull Optional<Boolean> awaitResult(long millis) throws InterruptedException, ExecutionException {
            try {
                return Optional.of(result.get(millis, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                return Optional.empty();
            }
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
