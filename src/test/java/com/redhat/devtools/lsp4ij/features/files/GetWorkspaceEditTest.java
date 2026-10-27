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
package com.redhat.devtools.lsp4ij.features.files;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.mock.MockWorkspaceService;
import org.eclipse.lsp4j.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link AbstractLSPFileListener#getWorkspaceEdit(CompletableFuture, long)}.
 * <p>
 * Verifies timeout, cancellation, error handling, and deadline behavior
 * of the method that waits for {@code workspace/will*} responses.
 */
public class GetWorkspaceEditTest extends BasePlatformTestCase {

    // --- Success cases ---

    public void testReturnsEditWhenFutureCompletesImmediately() {
        WorkspaceEdit expected = new WorkspaceEdit();
        CompletableFuture<WorkspaceEdit> future = CompletableFuture.completedFuture(expected);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        WorkspaceEdit result = AbstractLSPFileListener.getWorkspaceEdit(future, deadline);

        assertSame("Should return the completed WorkspaceEdit", expected, result);
    }

    public void testReturnsEditWhenFutureCompletesBeforeDeadline() {
        WorkspaceEdit expected = new WorkspaceEdit();
        CompletableFuture<WorkspaceEdit> future = new CompletableFuture<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)
                .execute(() -> future.complete(expected));

        WorkspaceEdit result = AbstractLSPFileListener.getWorkspaceEdit(future, deadline);

        assertSame("Should return the completed WorkspaceEdit", expected, result);
    }

    public void testReturnsNullWhenServerReturnsNull() {
        CompletableFuture<WorkspaceEdit> future = CompletableFuture.completedFuture(null);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        WorkspaceEdit result = AbstractLSPFileListener.getWorkspaceEdit(future, deadline);

        assertNull("Should return null when server returns null", result);
    }

    // --- Timeout cases ---

    public void testReturnsNullAndCancelsFutureOnTimeout() {
        CompletableFuture<WorkspaceEdit> future = new CompletableFuture<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);

        WorkspaceEdit result = AbstractLSPFileListener.getWorkspaceEdit(future, deadline);

        assertNull("Should return null on timeout", result);
        assertTrue("Future should be cancelled after timeout", future.isCancelled());
    }

    public void testReturnsNullImmediatelyWhenDeadlineAlreadyExpired() {
        CompletableFuture<WorkspaceEdit> future = new CompletableFuture<>();
        long deadline = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);

        WorkspaceEdit result = AbstractLSPFileListener.getWorkspaceEdit(future, deadline);

        assertNull("Should return null for expired deadline", result);
        assertTrue("Future should be cancelled for expired deadline", future.isCancelled());
    }

    // --- Error cases ---

    public void testReturnsNullOnServerError() {
        CompletableFuture<WorkspaceEdit> future =
                CompletableFuture.failedFuture(new RuntimeException("Server error"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        WorkspaceEdit result = AbstractLSPFileListener.getWorkspaceEdit(future, deadline);

        assertNull("Should return null on server error", result);
    }

    public void testReturnsNullOnCancellation() {
        CompletableFuture<WorkspaceEdit> future = new CompletableFuture<>();
        future.cancel(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        WorkspaceEdit result = AbstractLSPFileListener.getWorkspaceEdit(future, deadline);

        assertNull("Should return null on cancellation", result);
    }

    // --- Shared deadline behavior ---

    public void testSharedDeadlineIsRespectedAcrossMultipleCalls() {
        // First call: generous deadline, already-completed future → succeeds immediately
        WorkspaceEdit edit1 = new WorkspaceEdit();
        CompletableFuture<WorkspaceEdit> future1 = CompletableFuture.completedFuture(edit1);
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        WorkspaceEdit result1 = AbstractLSPFileListener.getWorkspaceEdit(future1, deadlineNanos);
        assertNotNull("First call should succeed (future already complete)", result1);

        // Second call: same deadline but set to past → returns null via early exit
        long expiredDeadline = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
        CompletableFuture<WorkspaceEdit> future2 = new CompletableFuture<>();
        WorkspaceEdit result2 = AbstractLSPFileListener.getWorkspaceEdit(future2, expiredDeadline);

        assertNull("Second call should fail with expired deadline", result2);
        assertTrue("Future should be cancelled after shared deadline expires",
                future2.isCancelled());
    }

    // --- Caller-level test: collectWillEdits pattern with MockWorkspaceService ---

    public void testSharedDeadlineAcrossMultipleWillRequestTypes() {
        // Exercises the collectWillEdits pattern with MockWorkspaceService:
        // first request succeeds, then deadline expires, second request fails.
        MockWorkspaceService mockService = new MockWorkspaceService(CompletableFuture::completedFuture);
        WorkspaceEdit createEdit = new WorkspaceEdit();
        WorkspaceEdit deleteEdit = new WorkspaceEdit();
        mockService.setWillCreate(createEdit);
        mockService.setWillCreateDelayMs(0);
        mockService.setWillDelete(deleteEdit);
        mockService.setWillDeleteDelayMs(60_000); // effectively never

        // 1st request: generous deadline, immediate completion → succeeds
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        var createParams = new CreateFilesParams(List.of(new FileCreate("file:///a.txt")));
        WorkspaceEdit result1 = AbstractLSPFileListener.getWorkspaceEdit(
                mockService.willCreateFiles(createParams), deadlineNanos);
        assertNotNull("willCreateFiles should succeed (immediate completion)", result1);

        // 2nd request: expired deadline → fails immediately
        long expiredDeadline = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
        var deleteParams = new DeleteFilesParams(List.of(new FileDelete("file:///b.txt")));
        CompletableFuture<WorkspaceEdit> deleteFuture = mockService.willDeleteFiles(deleteParams);
        WorkspaceEdit result2 = AbstractLSPFileListener.getWorkspaceEdit(deleteFuture, expiredDeadline);

        assertNull("willDeleteFiles should fail with expired deadline", result2);
        assertTrue("willDeleteFiles future should be cancelled", deleteFuture.isCancelled());
    }

    // --- Progress indicator cancellation ---

    public void testRethrowsProcessCancelledExceptionWhenIndicatorCancelled() {
        CompletableFuture<WorkspaceEdit> future = new CompletableFuture<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        EmptyProgressIndicator indicator = new EmptyProgressIndicator();
        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)
                .execute(indicator::cancel);

        try {
            ProgressManager.getInstance().runProcess(
                    () -> AbstractLSPFileListener.getWorkspaceEdit(future, deadline),
                    indicator);
            fail("Should have thrown ProcessCanceledException");
        } catch (ProcessCanceledException e) {
            assertTrue("Future should be cancelled when indicator is cancelled",
                    future.isCancelled());
        }
    }
}
