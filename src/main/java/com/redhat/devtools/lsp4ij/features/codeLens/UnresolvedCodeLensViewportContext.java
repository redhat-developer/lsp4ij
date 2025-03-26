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
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Manages the viewport for unresolved code lens elements, scheduling their resolution
 * and refreshing the editor once the resolution is complete. This class tracks the
 * currently visible lines and ensures efficient refreshing through debounce logic.
 */
public class UnresolvedCodeLensViewportContext implements Disposable {

    private static final long VIEWPORT_CHANGE_DELAY_MS = 500L; // Debounce delay before processing viewport changes

    private final @NotNull Editor editor;
    private int firstViewportLine;
    private int lastViewportLine;
    private long modificationStamp;
    private volatile Alarm scrollStopAlarm = null;

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
     * @param result            The unresolved code lens data to resolve.
     * @param file              The associated PSI file containing the code.
     * @param firstViewportLine The first visible line in the editor's viewport.
     * @param lastViewportLine  The last visible line in the editor's viewport.
     */
    public void refreshCodeVision(@NotNull CodeLensDataResult result,
                                  @NotNull PsiFile file,
                                  int firstViewportLine,
                                  int lastViewportLine) {
        var scrollStopAlarm = getScrollStopAlarm();
        scrollStopAlarm.cancelAllRequests();
        scrollStopAlarm.addRequest(() -> {
            if (isSameViewportRange(firstViewportLine, lastViewportLine)
                    && result.hasToResolve(firstViewportLine, lastViewportLine)
                    && !hasFileChanged(file)) {
                // The viewport hasn't changed (no scrolling occurred) there are some codelens to resolve and file has not changed
                // , so we proceed to refresh
                EditorFeatureManager.getInstance(editor.getProject()).
                        refreshEditorFeature(file, EditorFeatureType.CODE_VISION, false);
            }
        }, VIEWPORT_CHANGE_DELAY_MS);
    }

    private Alarm getScrollStopAlarm() {
        if (scrollStopAlarm == null) {
            synchronized (this) {
                if (scrollStopAlarm == null) {
                    scrollStopAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
                }
            }
        }
        return scrollStopAlarm;
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
    }

    /**
     * Returns true if the file content has changed and false otherwise.
     *
     * @param file the file.
     * @return true if the file content has changed and false otherwise.
     */
    public boolean hasFileChanged(@NotNull PsiFile file) {
        if (modificationStamp != file.getModificationStamp()) {
            modificationStamp = file.getModificationStamp();
            return true;
        }
        return false;
    }
}
