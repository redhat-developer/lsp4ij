/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Sends {@code textDocument/willSave} and {@code textDocument/willSaveWaitUntil} before a document is saved, to the
 * language servers that have the document opened and ask for them.
 */
public class LSPDocumentSaveListener implements FileDocumentManagerListener {

    /**
     * How long a save waits, in all, for the edits of {@code textDocument/willSaveWaitUntil}: as long as VS Code waits
     * for its save participants. The documents saved together, as by Save All, share it.
     */
    private static final long WILL_SAVE_WAIT_UNTIL_TIMEOUT_MS = 1500L;

    // The budget of the saves of one UI event, which only a test changes
    private static volatile long willSaveWaitUntilTimeoutMs = WILL_SAVE_WAIT_UNTIL_TIMEOUT_MS;

    // The deadline that the saves of one UI event share, and 0 between events. Saves run on the UI thread.
    private long deadlineNanos;

    // The UI event whose saves share the deadline, held until the reset that ends the deadline runs
    private @Nullable AWTEvent deadlineEvent;

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        var file = FileDocumentManager.getInstance().getFile(document);
        if (file == null) {
            return;
        }
        // The event that IntelliJ dispatches, which is the outer one again once a nested dispatch returns, as between the
        // documents of Save All. EventQueue.getCurrentEvent() would stay on the last nested event.
        var event = IdeEventQueue.getInstance().getTrueCurrentEvent();
        if (deadlineNanos == 0 || event != deadlineEvent) {
            // The first save of an event starts the budget that the saves of the event share. A save in another event
            // starts its own, even one queued before the previous event ended, as a second Ctrl+S pressed while the first
            // save waited is: it runs before the reset below.
            deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(willSaveWaitUntilTimeoutMs);
            deadlineEvent = event;
            // The reset ends the budget after the event, for a save in the same event after a nested dispatch, and lets
            // the event go. It is a plain AWT event that needs no lock: a runnable of Application.invokeLater needs the
            // write-intent lock, and from IntelliJ 2025.3 on, the UI thread holds it back while a background write action
            // runs, such as a document commit.
            SwingUtilities.invokeLater(() -> {
                // A save in a later event may have started a budget of its own, which this one does not end
                if (deadlineEvent == event) {
                    deadlineNanos = 0;
                    deadlineEvent = null;
                }
            });
        }
        for (var project : ProjectManager.getInstance().getOpenProjects()) {
            for (var wrapper : new ArrayList<>(LanguageServiceAccessor.getInstance(project).getStartedServers())) {
                if (!project.equals(wrapper.getProject())) {
                    continue;
                }
                var openedDocument = wrapper.getOpenedDocument(wrapper.toUri(file));
                if (openedDocument != null && openedDocument.getSynchronizer() != null) {
                    openedDocument.getSynchronizer().documentWillSave(deadlineNanos);
                }
            }
        }
    }

    /**
     * Sets how long the saves of one UI event wait, in all, for the edits of {@code textDocument/willSaveWaitUntil},
     * instead of {@value #WILL_SAVE_WAIT_UNTIL_TIMEOUT_MS} ms, until the given disposable is disposed, which restores
     * the budget it replaced. The documents saved together still share the budget.
     *
     * @param timeout          the budget of the saves of one UI event.
     * @param parentDisposable the disposable whose disposal restores the previous budget.
     */
    @TestOnly
    static void setWillSaveWaitUntilTimeout(@NotNull Duration timeout, @NotNull Disposable parentDisposable) {
        long previous = willSaveWaitUntilTimeoutMs;
        // Registered first: Disposer refuses a disposed parent, which then leaves the budget as it is
        Disposer.register(parentDisposable, () -> willSaveWaitUntilTimeoutMs = previous);
        willSaveWaitUntilTimeoutMs = timeout.toMillis();
    }
}
