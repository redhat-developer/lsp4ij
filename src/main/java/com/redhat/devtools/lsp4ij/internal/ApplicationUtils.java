/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * Application utilities.
 */
public class ApplicationUtils {

    /**
     * Invoke the given runnable later if needed.
     *
     * @param runnable the runnable.
     */
    public static void invokeLaterIfNeeded(@NotNull Runnable runnable) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            runnable.run();
        } else {
            ApplicationManager.getApplication().invokeLater(runnable);
        }
    }

    /**
     * Executes a read action in a cancellable way to avoid UI freezes.
     * <p>
     * This method uses {@link ReadAction#nonBlocking(Callable)} which creates a cancellable
     * read action, preventing UI freezes when the operation takes too long or when the user
     * performs actions that require write access.
     * </p>
     * <p>
     * See <a href="https://blog.jetbrains.com/platform/2026/03/ui-freezes-and-the-dangers-of-non-cancellable-read-actions-in-background-threads/">
     * UI Freezes and the Dangers of Non-Cancellable Read Actions in Background Threads</a>
     * </p>
     * <p>
     * On the EDT, a non-blocking read action runs at once if the EDT has read access, and fails without it. Since
     * IntelliJ 2026.1, the EDT doesn't always hold the write-intent lock, which gives it read access, so a caller that
     * reads directly only when it has read access gets here on the EDT too. Without read access, this takes the
     * write-intent lock on the EDT first, as JetBrains advises such code to do.
     * </p>
     *
     * @param action           the read action to execute
     * @param parentDisposable the parent disposable to expire the read action when disposed
     * @param <T>              the type of the result
     * @return the result of the read action
     */
    public static <T> T runCancellableReadAction(@NotNull Callable<T> action, @NotNull Disposable parentDisposable) {
        var application = ApplicationManager.getApplication();
        if (application.isDispatchThread() && !application.isReadAccessAllowed()) {
            return WriteIntentReadAction.compute((Computable<T>) () -> runNonBlocking(action, parentDisposable));
        }
        return runNonBlocking(action, parentDisposable);
    }

    /**
     * Executes a read action in a cancellable way to avoid UI freezes.
     * <p>
     * This method uses {@link ReadAction#nonBlocking(Runnable)} which creates a cancellable
     * read action, preventing UI freezes when the operation takes too long or when the user
     * performs actions that require write access.
     * </p>
     * <p>
     * See <a href="https://blog.jetbrains.com/platform/2026/03/ui-freezes-and-the-dangers-of-non-cancellable-read-actions-in-background-threads/">
     * UI Freezes and the Dangers of Non-Cancellable Read Actions in Background Threads</a>
     * </p>
     * <p>
     * On the EDT without read access, this takes the write-intent lock first, as
     * {@link #runCancellableReadAction(Callable, Disposable)} does.
     * </p>
     *
     * @param action           the read action to execute
     * @param parentDisposable the parent disposable to expire the read action when disposed
     */
    public static void runCancellableReadAction(@NotNull Runnable action, @NotNull Disposable parentDisposable) {
        runCancellableReadAction(() -> {
            action.run();
            return null;
        }, parentDisposable);
    }

    private static <T> T runNonBlocking(@NotNull Callable<T> action, @NotNull Disposable parentDisposable) {
        return ReadAction
                .nonBlocking(action)
                .expireWith(parentDisposable)
                .executeSynchronously();
    }
}
