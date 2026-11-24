/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Key;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import com.redhat.devtools.lsp4ij.server.definition.launching.WorkingDirectoryCommandLine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * <pre>
 * " exec": {
 *     "name": "Check typescript-language-server",*
 *     "command": {
 *       "windows": "where typescript-language-server",
 *       "default": "which typescript-language-server"
 *     },
 *     "onFail": { ...
 *     }
 * }
 * </pre>
 */
public class ExecTask extends InstallerTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecTask.class);

    /**
     * The locks of the working directories that exec tasks declare, by absolute path: the installers of two language
     * servers which install into the same directory, as the CSS and ESLint language servers, which share their npm
     * package, run there one at a time, else two {@code npm install} would break each other's install.
     */
    private static final Map<Path, ReentrantLock> WORKING_DIR_LOCKS = new ConcurrentHashMap<>();

    /**
     * The directory of the lock files of the working directories, which keep the exec tasks of the other IDEs with
     * LSP4IJ, as IntelliJ IDEA and WebStorm, out of a working directory, since they share the directories into which
     * the installers install, as {@code $USER_HOME$/.lsp4ij/lsp}.
     */
    private static final Path LOCK_FILES_DIR = Path.of(SystemProperties.getUserHome(), ".lsp4ij", "locks");

    private final @NotNull List<String> command;
    private final @Nullable Integer timeout;
    private final @Nullable String workingDir;
    private final boolean ignoreStderr;

    public ExecTask(@Nullable String id,
                    @Nullable String name,
                    @Nullable InstallerTask onFail,
                    @Nullable InstallerTask onSuccess,
                    @NotNull List<String> command,
                    @Nullable String workingDir,
                    @Nullable Integer timeout,
                    boolean ignoreStderr,
                    @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        super(id, name, onFail, onSuccess, serverInstallerDeclaration);
        this.command = command;
        this.workingDir = workingDir;
        this.timeout = timeout;
        this.ignoreStderr = ignoreStderr;

    }

    private static List<String> resolveServerCommand(@NotNull List<String> command,
                                                     @NotNull InstallerContext context) {
        if (command.size() == 1 && command.get(0).equals("${server.command}")) {
            // Specific case: resolve "${server.command}"
            // create an array string of the resolved command
            String resolved = context.resolveValues(command.get(0));
            return CommandUtils.createCommands(resolved);
        }
        return command;
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        try (@Nullable WorkingDirLock workingDirLock = lockWorkingDir(context)) {
            if (workingDirLock != null && workingDirLock.waited() && context.isCheckAgainAfterWaiting() && checkAgain(context)) {
                // As when the installers of the CSS and ESLint language servers start together: the one which waited
                // doesn't install their npm package again (#1960)
                context.print("The installation which ran in the working directory meanwhile installed it: the command doesn't run");
                return true;
            }
            return runCommand(context);
        }
    }

    /**
     * Runs the check of the installer again, and returns true if it passes and false otherwise, or without a check.
     */
    private boolean checkAgain(@NotNull InstallerContext context) {
        InstallerTask check = getServerInstallerDeclaration().getCheck();
        return check != null && check.execute(context);
    }

    /**
     * Waits until no other exec task runs in the working directory that this task declares, in this IDE or in another
     * one, and returns the lock of the directory, which the caller must close; or null without a declared working
     * directory. The wait ends with a {@link ProcessCanceledException} if the installation is cancelled.
     */
    private @Nullable WorkingDirLock lockWorkingDir(@NotNull InstallerContext context) {
        if (this.workingDir == null) {
            return null;
        }
        Path workingDir;
        try {
            workingDir = getWorkingDir(this.workingDir, context);
        } catch (InvalidPathException e) {
            // The command reports it
            return null;
        }
        if (workingDir == null) {
            return null;
        }
        Path dir = workingDir.toAbsolutePath().normalize();
        ReentrantLock lock = WORKING_DIR_LOCKS.computeIfAbsent(dir, key -> new ReentrantLock());
        boolean waited = !lock.tryLock();
        if (waited) {
            await(() -> lock.tryLock(100, TimeUnit.MILLISECONDS));
        }
        if (lock.getHoldCount() > 1) {
            // This thread runs in the directory already, as the check that the run of an installer runs again, and
            // holds its lock file
            return new WorkingDirLock(lock, null, false);
        }
        @Nullable FileChannel lockFile = openLockFile(dir);
        try {
            if (lockFile != null && !tryLockFile(lockFile)) {
                waited = true;
                await(() -> {
                    Thread.sleep(100);
                    return tryLockFile(lockFile);
                });
            }
        } catch (RuntimeException e) {
            // Cancelled
            new WorkingDirLock(lock, lockFile, false).close();
            throw e;
        }
        return new WorkingDirLock(lock, lockFile, waited);
    }

    /**
     * Tries the given attempt to lock until it succeeds, or ends with a {@link ProcessCanceledException} if the
     * installation is cancelled.
     */
    private static void await(@NotNull LockAttempt attempt) {
        try {
            while (!attempt.tryLock()) {
                ProgressManager.checkCanceled();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException(e);
        }
    }

    /**
     * Returns the lock file of the given working directory, in {@link #LOCK_FILES_DIR}.
     */
    @VisibleForTesting
    static @NotNull Path getLockFile(@NotNull Path workingDir) {
        Path dir = workingDir.toAbsolutePath().normalize();
        String name = dir.getFileName() != null ? dir.getFileName().toString() : "root";
        UUID id = UUID.nameUUIDFromBytes(dir.toString().getBytes(StandardCharsets.UTF_8));
        return LOCK_FILES_DIR.resolve(name + "-" + id + ".lock");
    }

    /**
     * Opens the lock file of the given working directory, or returns null if it can't, as in a read-only home: the
     * exec tasks of the other IDEs are then not kept out of the directory.
     */
    private static @Nullable FileChannel openLockFile(@NotNull Path dir) {
        Path lockFile = getLockFile(dir);
        try {
            Files.createDirectories(lockFile.getParent());
            return FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            LOGGER.warn("Cannot open the lock file '{}' of the working directory '{}'", lockFile, dir, e);
            return null;
        }
    }

    /**
     * Tries to lock the given lock file, and returns true if it is locked, or if it can't be, as on a file system
     * without file locks, and false if another IDE holds it.
     */
    private static boolean tryLockFile(@NotNull FileChannel lockFile) {
        try {
            return lockFile.tryLock() != null;
        } catch (OverlappingFileLockException e) {
            // This IDE holds it, for another working directory whose lock file has the same name
            return false;
        } catch (IOException e) {
            LOGGER.warn("Cannot lock the lock file of a working directory", e);
            return true;
        }
    }

    private boolean runCommand(@NotNull InstallerContext context) {
        CapturingProcessHandler handler = null;
        try {
            @Nullable var project = context.getProject();
            List<String> command = resolveServerCommand(this.command, context);
            List<String> resolvedCommand = command
                    .stream()
                    .map(args -> CommandUtils.resolveCommandLine(args, project))
                    .toList();
            context.print("> " + String.join(" ", resolvedCommand));

            // With a working directory, the tools which its environment selects, as the language server gets them
            GeneralCommandLine cmdLine = new WorkingDirectoryCommandLine(resolvedCommand)
                    .withEnvironment(EnvironmentUtil.getEnvironmentMap());
            Path workingDir = getWorkingDir(this.workingDir, context);
            if (workingDir != null) {
                if (!Files.exists(workingDir)) {
                    Files.createDirectories(workingDir);
                }
                cmdLine = cmdLine
                        .withWorkDirectory(workingDir.toFile());
            }
            cmdLine.setCharset(java.nio.charset.StandardCharsets.UTF_8);

            handler = new CapturingProcessHandler(cmdLine);
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    // The output of the command, which can be long, as that of npm install, is logged at DEBUG only,
                    // its standard error too, where commands also write their warnings
                    context.print(event.getText(), outputType == ProcessOutputType.STDERR
                            ? ConsoleViewContentType.ERROR_OUTPUT
                            : ConsoleViewContentType.NORMAL_OUTPUT, Level.FINE);
                }
            });

            ProcessOutput result = timeout != null ? handler.runProcess(timeout) : handler.runProcess();
            if (result.isCancelled()) {
                throw new ProcessCanceledException();
            }
            if (result.isTimeout()) {
                return true;
            }
            return result.getExitCode() == 0 && (ignoreStderr || result.getStderr().isEmpty());
        } catch (Exception e) {
            context.printError("", e);
            return false;
        } finally {
            if (handler != null) {
                handler.destroyProcess();
            }
        }
    }

    private Path getWorkingDir(@Nullable String workingDir,
                               @NotNull InstallerContext context) {
        @Nullable var project = context.getProject();
        if (workingDir != null) {
            String resolved = CommandUtils.resolveCommandLine(context.resolveValues(workingDir), project);
            if (resolved != null) {
                return Paths.get(resolved);
            }
        }
        if (project != null) {
            var projectDir = ProjectUtil.guessProjectDir(project);
            if (projectDir != null) {
                return projectDir.toNioPath();
            }
        }
        return null;
    }

    /**
     * An attempt to lock, which can wait.
     */
    @FunctionalInterface
    private interface LockAttempt {
        boolean tryLock() throws InterruptedException;
    }

    /**
     * The lock of a working directory that an exec task holds: the lock of the directory in this IDE, and the lock file
     * of the directory, whose file lock keeps out the exec tasks of the other IDEs, if this task holds it.
     *
     * @param lock     the lock of the directory in this IDE.
     * @param lockFile the lock file of the directory, if this task holds it.
     * @param waited   true if the exec task waited for another one to leave the directory, and false otherwise.
     */
    private record WorkingDirLock(@NotNull ReentrantLock lock,
                                  @Nullable FileChannel lockFile,
                                  boolean waited) implements AutoCloseable {

        @Override
        public void close() {
            try {
                if (lockFile != null) {
                    // Releases its file lock
                    lockFile.close();
                }
            } catch (IOException e) {
                LOGGER.warn("Cannot close the lock file of a working directory", e);
            } finally {
                lock.unlock();
            }
        }
    }
}
