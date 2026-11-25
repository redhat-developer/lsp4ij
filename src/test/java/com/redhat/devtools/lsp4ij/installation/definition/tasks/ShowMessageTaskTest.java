/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskRegistry;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@code showMessage} task with actions waits for the user to choose one of them, and ends once the notification is
 * closed without a choice, or the installation is cancelled, instead of keeping its thread forever.
 */
public class ShowMessageTaskTest extends BasePlatformTestCase {

    private static final int TIMEOUT = 10;

    private final List<Notification> notifications = new CopyOnWriteArrayList<>();
    private final AtomicBoolean actionRan = new AtomicBoolean();
    private ExecutorService executor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // A daemon, which can't keep the JVM of the tests from ending
        executor = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "ShowMessageTaskTest");
            thread.setDaemon(true);
            return thread;
        });
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
                .subscribe(Notifications.TOPIC, new Notifications() {
                    @Override
                    public void notify(@NotNull Notification notification) {
                        notifications.add(notification);
                    }
                });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            executor.shutdownNow();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testANotificationClosedWithoutAChoiceEndsTheTask() throws Exception {
        Future<Boolean> result = executor.submit(() -> createTask().run(new InstallerContext(null, InstallerContext.InstallerAction.RUN)));
        var notification = waitForTheNotification();

        notification.expire();

        PlatformTestUtil.waitWithEventsDispatching("The task must end once its notification is closed", result::isDone, TIMEOUT);
        assertFalse("The task fails without a choice", result.get());
        assertFalse(actionRan.get());
    }

    public void testAnActionChosenRightBeforeTheNotificationIsClosedRuns() throws Exception {
        Future<Boolean> result = executor.submit(() -> createTask().run(new InstallerContext(null, InstallerContext.InstallerAction.RUN)));
        var notification = waitForTheNotification();

        Notification.fire(notification, notification.getActions().get(0), DataContext.EMPTY_CONTEXT);
        notification.expire();

        PlatformTestUtil.waitWithEventsDispatching("The task must run the chosen action", result::isDone, TIMEOUT);
        assertTrue("The task gives the result of the chosen action", result.get());
        assertTrue(actionRan.get());
    }

    public void testACancelledInstallationEndsTheTask() throws Exception {
        var indicator = new EmptyProgressIndicator();
        var result = new CompletableFuture<Boolean>();
        executor.execute(() -> {
            try {
                ProgressManager.getInstance().runProcess(() -> {
                    result.complete(createTask().run(new InstallerContext(null, InstallerContext.InstallerAction.RUN)));
                }, indicator);
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        waitForTheNotification();

        indicator.cancel();

        PlatformTestUtil.waitWithEventsDispatching("The task must end once the installation is cancelled", result::isDone, TIMEOUT);
        // A ProcessCanceledException is a CancellationException, which CompletableFuture.get() would throw
        assertInstanceOf(result.handle((value, error) -> error).join(), ProcessCanceledException.class);
        assertFalse(actionRan.get());
    }

    public void testAnInterruptedTaskEnds() throws Exception {
        var ended = new CompletableFuture<Throwable>();
        var taskThread = new CompletableFuture<Thread>();
        Future<?> running = executor.submit(() -> {
            taskThread.complete(Thread.currentThread());
            try {
                createTask().run(new InstallerContext(null, InstallerContext.InstallerAction.RUN));
                ended.complete(null);
            } catch (Throwable e) {
                ended.complete(e);
            }
        });
        waitForTheNotification();
        // The test's listener gets the notification while Notifications.Bus.notify() still delivers it to the others,
        // where an interrupt would end a service that a listener of the platform still creates, and not the task
        // (#2259)
        Thread thread = taskThread.get(TIMEOUT, TimeUnit.SECONDS);
        PlatformTestUtil.waitWithEventsDispatching("The task must wait for the user's choice",
                () -> waitsForTheChoice(thread), TIMEOUT);

        running.cancel(true);

        PlatformTestUtil.waitWithEventsDispatching("The task must end once its thread is interrupted", ended::isDone, TIMEOUT);
        assertInstanceOf(ended.get(), ProcessCanceledException.class);
        assertFalse(actionRan.get());
    }

    /**
     * Returns true if the given thread waits in {@link ShowMessageTask#run} for the user's choice: in
     * {@code Object.wait}, as the stack of that thread shows, since its state alone doesn't say what it waits for.
     */
    private static boolean waitsForTheChoice(@NotNull Thread thread) {
        if (thread.getState() != Thread.State.TIMED_WAITING) {
            return false;
        }
        StackTraceElement[] stack = thread.getStackTrace();
        return stack.length > 0
                && stack[0].getClassName().equals(Object.class.getName())
                && stack[0].getMethodName().startsWith("wait")
                && Arrays.stream(stack).anyMatch(frame -> frame.getClassName().equals(ShowMessageTask.class.getName())
                && frame.getMethodName().equals("run"));
    }

    private @NotNull ShowMessageTask createTask() {
        var descriptor = new ServerInstallerDescriptor("test", false, new InstallerTaskRegistry());
        InstallerTask action = new InstallerTask(null, "Install", null, null, descriptor) {
            @Override
            public boolean run(@NotNull InstallerContext context) {
                actionRan.set(true);
                return true;
            }
        };
        return new ShowMessageTask(null, "Server not found", "Install it?", null, null, List.of(action), descriptor);
    }

    private @NotNull Notification waitForTheNotification() {
        PlatformTestUtil.waitWithEventsDispatching("The task must show its notification", () -> !notifications.isEmpty(), TIMEOUT);
        return notifications.get(0);
    }
}
