/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

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

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        var file = FileDocumentManager.getInstance().getFile(document);
        if (file == null) {
            return;
        }
        if (deadlineNanos == 0) {
            // The first save of this event starts the budget, which ends with the event
            deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(willSaveWaitUntilTimeoutMs);
            ApplicationManager.getApplication().invokeLater(() -> deadlineNanos = 0, ModalityState.any());
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
