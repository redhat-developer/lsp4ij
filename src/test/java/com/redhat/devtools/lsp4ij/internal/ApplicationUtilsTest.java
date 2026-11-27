/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A non-blocking read action runs at once on a thread that has read access, as the EDT has in these tests, but fails
 * with "Access from Event Dispatch Thread (EDT) is not allowed" on the EDT without it, as the settings page of a
 * language server did in IntelliJ 2026.3. On the EDT without read access, a cancellable read action takes the
 * write-intent lock first. On the EDT and off it, it is a non-blocking read action, which expires with its disposable.
 */
public class ApplicationUtilsTest extends BasePlatformTestCase {

    private static final String NON_BLOCKING_READ_ACTION = "com.intellij.openapi.application.impl.NonBlockingReadActionImpl";

    public void testAReadActionOnTheEdtExpiresWithItsDisposable() {
        assertTrue(ApplicationManager.getApplication().isDispatchThread());
        var ran = new AtomicBoolean();

        assertEquals("Both overloads must be cancelled", List.of(true, true), cancellationsOfBothOverloads(ran));
        assertFalse("A read action whose disposable is disposed must not run", ran.get());
    }

    public void testAReadActionOnTheEdtWithoutReadAccessExpiresWithItsDisposable() throws Exception {
        var ran = new AtomicBoolean();
        var cancellations = new ArrayList<Boolean>();

        runWithoutTheWriteIntentLock(() -> cancellations.addAll(cancellationsOfBothOverloads(ran)));

        assertEquals("Both overloads must be cancelled", List.of(true, true), cancellations);
        assertFalse("A read action whose disposable is disposed must not run", ran.get());
    }

    /**
     * On the EDT without read access, as the settings page of a language server was in IntelliJ 2026.3, both overloads
     * take the write-intent lock, which gives them read access, rather than a read lock.
     */
    public void testAReadActionOnTheEdtWithoutReadAccessTakesTheWriteIntentLock() throws Exception {
        var application = ApplicationManager.getApplication();
        var access = new ArrayList<Boolean>();

        runWithoutTheWriteIntentLock(() -> {
            access.add(application.isReadAccessAllowed());
            access.add(ApplicationUtils.runCancellableReadAction(() -> hasWriteIntentLock(), getTestRootDisposable()));
            access.add(application.isReadAccessAllowed());
            var writeIntent = new AtomicBoolean();
            ApplicationUtils.runCancellableReadAction(() -> writeIntent.set(hasWriteIntentLock()), getTestRootDisposable());
            access.add(writeIntent.get());
        });

        assertEquals("The EDT has no read access before each overload, and each takes the write-intent lock",
                List.of(false, true, false, true), access);
    }

    public void testAReadActionOffTheEdtIsANonBlockingReadAction() throws Exception {
        var nonBlocking = ApplicationManager.getApplication().executeOnPooledThread(
                () -> ApplicationUtils.runCancellableReadAction(() -> isNonBlockingReadAction(), getTestRootDisposable())
        ).get(10, TimeUnit.SECONDS);

        assertTrue(nonBlocking);
    }

    public void testAReadActionOffTheEdtExpiresWithItsDisposable() throws Exception {
        var disposable = Disposer.newDisposable();
        Disposer.dispose(disposable);

        var cancellation = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ApplicationUtils.runCancellableReadAction(() -> true, disposable);
                return null;
            } catch (ProcessCanceledException e) {
                return e;
            }
        }).get(10, TimeUnit.SECONDS);

        assertNotNull("A read action whose disposable is disposed must not run", cancellation);
    }

    /**
     * Runs both overloads with a disposed disposable, and returns whether each was cancelled.
     */
    private static List<Boolean> cancellationsOfBothOverloads(AtomicBoolean ran) {
        var disposable = Disposer.newDisposable();
        Disposer.dispose(disposable);
        return List.of(
                isCancelled(() -> ApplicationUtils.runCancellableReadAction(() -> ran.getAndSet(true), disposable)),
                isCancelled(() -> ApplicationUtils.runCancellableReadAction(() -> ran.set(true), disposable)));
    }

    private static boolean isCancelled(Runnable action) {
        try {
            action.run();
            return false;
        } catch (ProcessCanceledException e) {
            return true;
        }
    }

    /**
     * Runs the tests of the EDT without read access on the test's thread, from which
     * {@link #runWithoutTheWriteIntentLock} runs their action on the EDT, and the others on the EDT, which holds the
     * write-intent lock in these tests.
     */
    @Override
    protected boolean runInDispatchThread() {
        return !getName().contains("WithoutReadAccess");
    }

    /**
     * Runs the action on the EDT without the write-intent lock, and so without read access, as an event that IntelliJ
     * dispatches without the lock. Called off the EDT: the EDT can't give the lock away while it runs a test, as
     * {@code ApplicationEx.runUnlockingIntendedWrite} doesn't once the lock is stored in the thread's context, as by
     * default in IntelliJ 2025.1.
     */
    private static void runWithoutTheWriteIntentLock(@NotNull Runnable action) {
        assertFalse("The test must run off the EDT", ApplicationManager.getApplication().isDispatchThread());
        EdtTestUtil.runInEdtAndWait(action::run, false);
    }

    /**
     * Returns whether the caller holds the write-intent lock, and checks that it has read access.
     */
    private static boolean hasWriteIntentLock() {
        var application = ApplicationManager.getApplication();
        assertTrue("The action must have read access", application.isReadAccessAllowed());
        return application.isWriteIntentLockAcquired();
    }

    /**
     * Returns whether the caller runs in a non-blocking read action, and checks that it runs in a read action.
     */
    private static boolean isNonBlockingReadAction() {
        assertTrue("The action must run in a read action", ApplicationManager.getApplication().isReadAccessAllowed());
        return StackWalker.getInstance()
                .walk(frames -> frames.anyMatch(frame -> frame.getClassName().startsWith(NON_BLOCKING_READ_ACTION)));
    }
}
