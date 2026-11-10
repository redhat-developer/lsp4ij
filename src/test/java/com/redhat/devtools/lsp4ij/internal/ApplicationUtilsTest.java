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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A cancellable read action on the EDT is no non-blocking read action, which the platform doesn't run synchronously
 * there. A non-blocking read action runs at once on a thread that has read access, as the EDT has in these tests, but
 * fails with "Access from Event Dispatch Thread (EDT) is not allowed" on the EDT without it, as the settings page of a
 * language server did in IntelliJ 2026.3. Off the EDT, it is still a non-blocking read action, which expires with its
 * disposable.
 */
public class ApplicationUtilsTest extends BasePlatformTestCase {

    private static final String NON_BLOCKING_READ_ACTION = "com.intellij.openapi.application.impl.NonBlockingReadActionImpl";

    public void testAReadActionWithAResultOnTheEdtIsNoNonBlockingReadAction() {
        assertTrue(ApplicationManager.getApplication().isDispatchThread());

        assertFalse(ApplicationUtils.runCancellableReadAction(() -> isNonBlockingReadAction(), getTestRootDisposable()));
    }

    public void testAReadActionWithoutAResultOnTheEdtIsNoNonBlockingReadAction() {
        assertTrue(ApplicationManager.getApplication().isDispatchThread());
        var nonBlocking = new AtomicBoolean(true);

        ApplicationUtils.runCancellableReadAction(() -> nonBlocking.set(isNonBlockingReadAction()), getTestRootDisposable());

        assertFalse(nonBlocking.get());
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
     * Returns whether the caller runs in a non-blocking read action, and checks that it runs in a read action.
     */
    private static boolean isNonBlockingReadAction() {
        assertTrue("The action must run in a read action", ApplicationManager.getApplication().isReadAccessAllowed());
        return StackWalker.getInstance()
                .walk(frames -> frames.anyMatch(frame -> frame.getClassName().startsWith(NON_BLOCKING_READ_ACTION)));
    }
}
