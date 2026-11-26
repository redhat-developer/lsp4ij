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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.platform.launcher.LauncherInterceptor;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Supplier;

/**
 * Gives each test JVM of a test task that runs in parallel forks its own IntelliJ system, config and log directories,
 * so that the forks share no indexes, VFS caches or logs.
 * <p>
 * The IntelliJ Platform Gradle Plugin gives every test JVM of a task the same {@code idea.system.path},
 * {@code idea.config.path} and {@code idea.log.path}. When the build runs the tests in more than one fork, it sets
 * {@code lsp4ij.test.forks} to their number and enables JUnit's launcher interceptors. Each test JVM then takes the
 * first free slot k = 1, 2, ...: the one whose lock file {@code <system>-slot<k>.lock}, next to the system directory,
 * no other JVM holds. It holds the lock until it ends. Slot 1 keeps the directories of the task, and slot k > 1 uses
 * {@code <directory>-<k>} instead, whose config starts as a copy of the task's. A fork mostly takes the same slot from
 * one build to the next, so that its caches stay warm. The plugins directory is shared, as the tests only read it.
 * <p>
 * It's a {@link LauncherInterceptor}, and not a {@code LauncherSessionListener}, because JUnit creates the interceptors
 * before anything else of a launcher session, while it creates all session listeners before it calls one: IntelliJ's
 * {@code com.intellij.tests.JUnit5TestSessionListener}, which comes first on the classpath, initializes
 * {@code UsefulTestCase} when JUnit creates it, which opens the test log in {@code idea.log.path}. JUnit finds this
 * interceptor through {@code META-INF/services/org.junit.platform.launcher.LauncherInterceptor}. Without forks, the
 * build doesn't enable the interceptors, and the tests run as they did before it.
 */
public final class TestForkSandbox implements LauncherInterceptor {

    private static final String FORKS_PROPERTY = "lsp4ij.test.forks";

    private static final String SYSTEM_PATH = "idea.system.path";
    private static final String CONFIG_PATH = "idea.config.path";
    private static final String LOG_PATH = "idea.log.path";

    /**
     * The most slots a test JVM tries, far more than the forks of a task and the test JVMs of earlier builds which
     * still run. Past it, the lock files don't work as they should, and the tests had better stop than share a sandbox.
     */
    private static final int MAX_SLOTS = 64;

    public TestForkSandbox() {
        // JUnit may create the interceptor more than once in a JVM, which takes its slot once
        SlotOfThisJvm.lock();
    }

    @Override
    public <T> T intercept(Invocation<T> invocation) {
        return invocation.proceed();
    }

    @Override
    public void close() {
        // The JVM keeps its slot until it ends, when the OS releases the lock
    }

    /**
     * Holds the lock of the slot of this JVM, which it takes when the class is initialized: never released, since
     * closing its channel would release it.
     */
    private static final class SlotOfThisJvm {

        private static final @Nullable FileLock LOCK = take();

        static @Nullable FileLock lock() {
            return LOCK;
        }

        private static @Nullable FileLock take() {
            int forks = Integer.getInteger(FORKS_PROPERTY, 1);
            if (forks <= 1) {
                return null;
            }
            Path system = Path.of(required(SYSTEM_PATH));
            Path config = Path.of(required(CONFIG_PATH));
            String log = System.getProperty(LOG_PATH);
            for (int slot = 1; slot <= MAX_SLOTS; slot++) {
                FileLock lock = tryLock(system.resolveSibling(system.getFileName() + "-slot" + slot + ".lock"));
                if (lock != null) {
                    if (slot > 1) {
                        useDirectoriesOf(slot, system, config, log);
                    }
                    writeToTheStandardError("LSP4IJ test JVM " + ProcessHandle.current().pid() + ", one of up to " + forks +
                            " forks, uses sandbox slot " + slot + ": " + System.getProperty(SYSTEM_PATH) + ", " +
                            System.getProperty(CONFIG_PATH) + ", " + System.getProperty(LOG_PATH));
                    return lock;
                }
            }
            throw new IllegalStateException("The test JVM finds no free sandbox slot: " + system.getFileName() +
                    "-slot1.lock to -slot" + MAX_SLOTS + ".lock in " + system.getParent() + " are all locked");
        }

        /**
         * Points IntelliJ at the system, config and log directories of the slot, before it reads them.
         */
        private static void useDirectoriesOf(int slot, @NotNull Path system, @NotNull Path config, @Nullable String log) {
            Path slotConfig = ofSlot(config, slot);
            copyNewer(config, slotConfig);
            use(SYSTEM_PATH, ofSlot(system, slot));
            use(CONFIG_PATH, slotConfig);
            if (log != null) {
                use(LOG_PATH, ofSlot(Path.of(log), slot));
            }
            // PathManager keeps a path once it has read it: had it read one before, IntelliJ would use the directory of
            // slot 1, which another fork uses
            assertUsed(SYSTEM_PATH, PathManager::getSystemPath);
            assertUsed(CONFIG_PATH, PathManager::getConfigPath);
            if (log != null) {
                assertUsed(LOG_PATH, PathManager::getLogPath);
            }
        }

        private static @NotNull Path ofSlot(@NotNull Path directory, int slot) {
            return directory.resolveSibling(directory.getFileName() + "-" + slot);
        }

        private static void use(@NotNull String property, @NotNull Path directory) {
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create " + directory + " for " + property, e);
            }
            System.setProperty(property, directory.toString());
        }

        private static void assertUsed(@NotNull String property, @NotNull Supplier<String> pathOfIntelliJ) {
            Path expected = Path.of(System.getProperty(property)).toAbsolutePath().normalize();
            Path used = Path.of(pathOfIntelliJ.get()).toAbsolutePath().normalize();
            if (!used.equals(expected)) {
                throw new IllegalStateException("IntelliJ read " + property + " before the test JVM took its sandbox slot: " +
                        "it uses " + used + ", which another fork may use too, and not " + expected);
            }
        }

        private static @NotNull String required(@NotNull String property) {
            String value = System.getProperty(property);
            if (value == null) {
                throw new IllegalStateException(FORKS_PROPERTY + " is set, but not " + property +
                        ", from which each fork would get a directory of its own");
            }
            return value;
        }

        /**
         * Locks the file, or returns null if another JVM holds its lock.
         */
        private static @Nullable FileLock tryLock(@NotNull Path file) {
            FileChannel channel = null;
            try {
                channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    channel.close();
                }
                return lock;
            } catch (OverlappingFileLockException e) {
                // This JVM holds it
                close(channel);
                return null;
            } catch (IOException e) {
                close(channel);
                throw new UncheckedIOException("Cannot lock " + file, e);
            }
        }

        private static void close(@Nullable FileChannel channel) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // Only a channel without a lock
                }
            }
        }

        /**
         * Copies each file of {@code from} which {@code to} hasn't got, or has got in an older version.
         */
        private static void copyNewer(@NotNull Path from, @NotNull Path to) {
            try {
                Files.walkFileTree(from, new SimpleFileVisitor<>() {
                    @Override
                    public @NotNull FileVisitResult preVisitDirectory(@NotNull Path directory,
                                                                      @NotNull BasicFileAttributes attributes) throws IOException {
                        Files.createDirectories(to.resolve(from.relativize(directory).toString()));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public @NotNull FileVisitResult visitFile(@NotNull Path file,
                                                              @NotNull BasicFileAttributes attributes) throws IOException {
                        Path copy = to.resolve(from.relativize(file).toString());
                        if (Files.notExists(copy) || attributes.lastModifiedTime().compareTo(Files.getLastModifiedTime(copy)) > 0) {
                            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public @NotNull FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException e) throws IOException {
                        // A file which the test JVM of slot 1 deletes while this one copies the config
                        if (e instanceof NoSuchFileException) {
                            return FileVisitResult.CONTINUE;
                        }
                        throw e;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot copy the config " + from + " to " + to, e);
            }
        }

        private static void writeToTheStandardError(@NotNull String text) {
            // Not System.err, which Gradle collects for the test that runs, and no test runs yet
            var err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
            err.println(text);
            err.flush();
        }
    }
}
