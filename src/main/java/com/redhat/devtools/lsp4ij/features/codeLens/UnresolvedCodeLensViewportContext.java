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
package com.redhat.devtools.lsp4ij.features.codeLens;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.features.codeLens.LSPCodeLensProvider.collectApplicableCodeLens;

/**
 * Manages the viewport for unresolved code lens elements, scheduling their resolution
 * and refreshing the editor once the resolution is complete. This class tracks the
 * currently visible lines and ensures efficient refreshing through debounce logic.
 */
public class UnresolvedCodeLensViewportContext implements Disposable  {

    private static final long VIEWPORT_CHANGE_DELAY_MS = 500L; // Debounce delay before processing viewport changes

    private final @NotNull Editor editor;
    private int firstViewportLine;
    private int lastViewportLine;
    private @Nullable CompletableFuture<Void> pendingRefreshTask; // Tracks the currently scheduled refresh task

    /**
     * Initializes the context for managing unresolved code lens elements in a given editor.
     *
     * @param editor The associated editor instance.
     */
    public UnresolvedCodeLensViewportContext(@NotNull Editor editor) {
        this.editor = editor;
    }

    /**
     * Updates the range of visible lines in the viewport based on the given visible area.
     *
     * @param visibleArea The visible rectangle in the editor used to calculate the viewport line range.
     */
    public void updateViewportLines(@NotNull Rectangle visibleArea) {
        int firstVisualLine = editor.yToVisualLine(visibleArea.y);
        int lastVisualLine = editor.yToVisualLine(visibleArea.y + visibleArea.height);
        firstViewportLine = editor.visualToLogicalPosition(new VisualPosition(firstVisualLine, 0)).line;
        lastViewportLine = editor.visualToLogicalPosition(new VisualPosition(lastVisualLine, 0)).line;
    }

    /**
     * Gets the first visible line in the editor's viewport.
     *
     * @return The first visible line number.
     */
    public int getFirstViewportLine() {
        return firstViewportLine;
    }

    /**
     * Gets the last visible line in the editor's viewport.
     *
     * @return The last visible line number.
     */
    public int getLastViewportLine() {
        return lastViewportLine;
    }

    /**
     * Resolves unresolved code lens elements in the current viewport and refreshes the editor accordingly.
     * If a previous refresh task is pending, it will be cancelled before scheduling a new one.
     *
     * @param data             The unresolved code lens data to resolve.
     * @param file             The associated PSI file containing the code.
     * @param firstViewportLine The first visible line in the editor's viewport.
     * @param lastViewportLine  The last visible line in the editor's viewport.
     * @param modificationStamp The timestamp used for version tracking of the code file.
     */
    public void resolveAndRefreshUnresolvedCodeLensInViewport(@NotNull List<CodeLensData> data,
                                                              @NotNull PsiFile file,
                                                              int firstViewportLine,
                                                              int lastViewportLine,
                                                              long modificationStamp) {
        if (pendingRefreshTask != null) {
            pendingRefreshTask.cancel(true); // Cancel any pending refresh task
        }

        pendingRefreshTask = delayedExecution(VIEWPORT_CHANGE_DELAY_MS)
                .thenRun(() -> {
                    if (isSameViewportRange(firstViewportLine, lastViewportLine)) {
                        resolveAndRefreshVisibleCodeLens(data, file, firstViewportLine, lastViewportLine, modificationStamp);
                    }
                });
    }

    /**
     * Delays the execution for a specified time before proceeding with the next operation,
     * periodically checking for cancellation requests.
     *
     * @param timeout The delay time in milliseconds before the operation is executed.
     * @return A CompletableFuture that completes after the specified delay.
     */
    private static CompletableFuture<Void> delayedExecution(long timeout) {
        return CompletableFutures.computeAsync(cancelChecker -> {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < timeout) {
                cancelChecker.checkCanceled();
                try {
                    Thread.sleep(5); // Sleep in small intervals to allow cancellation checks
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException(); // Ensure cancellation exception is thrown when interrupted
                }
            }
            return null;
        });
    }

    /**
     * Resolves the code lens elements visible in the editor's current viewport and triggers a refresh.
     *
     * @param data             The unresolved code lens data.
     * @param file             The associated PSI file.
     * @param firstViewportLine The first visible line in the viewport.
     * @param lastViewportLine  The last visible line in the viewport.
     * @param modificationStamp The modification timestamp for version control.
     */
    private void resolveAndRefreshVisibleCodeLens(@NotNull List<CodeLensData> data,
                                                  @NotNull PsiFile file,
                                                  int firstViewportLine,
                                                  int lastViewportLine,
                                                  long modificationStamp) {
        // Collect the unresolved code lens elements that are visible within the current viewport range
        @Nullable CompletableFuture<Void> allResolve =
                collectApplicableCodeLens(data, null, editor);
        if (allResolve != null) {
            // After resolving the code lens, refresh only the elements within the current viewport range
            allResolve
                    .thenRun(() -> {
                        if (isSameViewportRange(firstViewportLine, lastViewportLine)) {
                            // The viewport hasn't changed (no scrolling occurred), so we proceed to refresh
                            EditorFeatureManager.getInstance(editor.getProject())
                                    .refreshEditorFeatureWhenAllDone(new HashSet<>(Arrays.asList(allResolve)),
                                            modificationStamp, file, EditorFeatureType.CODE_VISION);
                        }
                    });
        }
    }

    /**
     * Checks if the current viewport range matches the given range.
     *
     * @param firstViewportLine The first visible line of the viewport to check.
     * @param lastViewportLine  The last visible line of the viewport to check.
     * @return True if the viewport range is unchanged, false otherwise.
     */
    private boolean isSameViewportRange(int firstViewportLine, int lastViewportLine) {
        return firstViewportLine == getFirstViewportLine() && lastViewportLine == getLastViewportLine();
    }

    @Override
    public void dispose() {
        if (pendingRefreshTask != null) {
            if (!pendingRefreshTask.isDone()) {
                pendingRefreshTask.cancel(true);
            }
            pendingRefreshTask = null;
        }
    }
}
