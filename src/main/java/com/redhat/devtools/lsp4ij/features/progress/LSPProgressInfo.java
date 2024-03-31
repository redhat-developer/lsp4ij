/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.progress;

import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * LSP progress information.
 */
class LSPProgressInfo {

    private final String token;
    private boolean cancellable;
    private boolean done;

    private final LinkedBlockingDeque<WorkDoneProgressNotification> progressNotifications;

    public LSPProgressInfo(String token) {
        this.token = token;
        this.progressNotifications = new LinkedBlockingDeque<>();
    }

    private boolean cancelled;

    private String title;

    public String getToken() {
        return token;
    }

    /**
     * Add 'begin', 'report', 'end' progress notification.
     *
     * @param progressNotification the progress notification.
     */
    public void addProgressNotification(@NotNull WorkDoneProgressNotification progressNotification) {
        progressNotifications.add(progressNotification);
    }

    /**
     * Get the next progress notification stored in the queue and null otherwise.
     *
     * @return the next progress notification stored in the queue and null otherwise.
     * @throws InterruptedException
     */
    @Nullable
    public WorkDoneProgressNotification getNextProgressNotification() throws InterruptedException {
        return progressNotifications.pollFirst(200, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the title or the token otherwise.
     *
     * @return the title or the token otherwise.
     */
    @NotNull
    public String getTitle() {
        return title != null ? title : token;
    }

    /**
     * Set the title.
     *
     * @param title the title.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns true if the progress is cancellable and false otherwise.
     *
     * @return true if the progress is cancellable and false otherwise.
     */
    public boolean isCancellable() {
        return cancellable;
    }

    /**
     * Set the cancellable of the progress.
     *
     * @param cancellable true if the progress is cancellable and false otherwise.
     */
    public void setCancellable(boolean cancellable) {
        this.cancellable = cancellable;
    }

    /**
     * Returns true if the progress is done and false otherwise.
     * @return
     */
    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
