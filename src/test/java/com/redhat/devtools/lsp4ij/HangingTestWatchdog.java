/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import org.jetbrains.annotations.NotNull;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Ends the test JVM when a test runs for longer than {@link #LIMIT}, once it has written which test that is and the
 * stacks of all threads. A test which hangs then fails the build within minutes and says where it hangs, instead of
 * holding the CI runner for the 6 hours after which GitHub cancels a job.
 * <p>
 * The slowest test passes in about 20 seconds, and the longest wait of a test which fails is 2 minutes, so the limit
 * is far from any test which ends. JUnit registers the watchdog for every test run, through
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}. It does nothing while a debugger is
 * attached, since a test may then stop at a breakpoint for as long as the developer likes.
 */
public final class HangingTestWatchdog implements TestExecutionListener {

    static final Duration LIMIT = Duration.ofMinutes(5);

    private final Duration limit;
    private final Consumer<String> report;
    private final Runnable end;
    private final boolean debugging;
    private final Map<TestIdentifier, ScheduledFuture<?>> running = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "Hanging test watchdog");
        thread.setDaemon(true);
        return thread;
    });

    public HangingTestWatchdog() {
        this(LIMIT, HangingTestWatchdog::writeToTheStandardError, () -> Runtime.getRuntime().halt(1), isDebugging());
    }

    HangingTestWatchdog(@NotNull Duration limit, @NotNull Consumer<String> report, @NotNull Runnable end,
                        boolean debugging) {
        this.limit = limit;
        this.report = report;
        this.end = end;
        this.debugging = debugging;
    }

    @Override
    public void executionStarted(TestIdentifier test) {
        if (test.isTest() && !debugging) {
            running.put(test, timer.schedule(() -> hangs(test), limit.toMillis(), TimeUnit.MILLISECONDS));
        }
    }

    @Override
    public void executionFinished(TestIdentifier test, TestExecutionResult result) {
        var watch = running.remove(test);
        if (watch != null) {
            watch.cancel(false);
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        timer.shutdownNow();
    }

    private void hangs(@NotNull TestIdentifier test) {
        report.accept("The test " + test.getUniqueId() + " has run for longer than " + limit.toSeconds() +
                " seconds: it hangs, so the tests end here. The stacks of all threads:\n\n" + threadDump());
        end.run();
    }

    static @NotNull String threadDump() {
        var dump = new StringBuilder();
        for (ThreadInfo thread : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            dump.append('"').append(thread.getThreadName()).append("\" ").append(thread.getThreadState());
            if (thread.getLockName() != null) {
                dump.append(" on ").append(thread.getLockName());
            }
            if (thread.getLockOwnerName() != null) {
                dump.append(" held by \"").append(thread.getLockOwnerName()).append('"');
            }
            dump.append('\n');
            // ThreadInfo.toString() would show 8 frames only
            for (StackTraceElement frame : thread.getStackTrace()) {
                dump.append("\tat ").append(frame).append('\n');
            }
            dump.append('\n');
        }
        return dump.toString();
    }

    private static void writeToTheStandardError(@NotNull String text) {
        // Not System.err, which Gradle replaces to collect the output of each test, and which it would never show once
        // the JVM ends
        var err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        err.println(text);
        err.flush();
    }

    private static boolean isDebugging() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(argument -> argument.startsWith("-agentlib:jdwp") || argument.startsWith("-Xrunjdwp"));
    }
}
