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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * LSP progress support.
 * <p>
 * See <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#progress">Progress Support</a>
 */
public class LSPProgressManager implements Disposable {

    private final Map<String /* token */ , LSPProgressInfo> progressMap;
    private LanguageServer languageServer;
    private LanguageServerWrapper languageServerWrapper;
    private boolean disposed;

    public LSPProgressManager() {
        // Map which contains current progresses stored by their token.
        this.progressMap = new ConcurrentHashMap<>();
    }

    public void connect(final LanguageServer languageServer, LanguageServerWrapper languageServerWrapper) {
        this.languageServer = languageServer;
        this.languageServerWrapper = languageServerWrapper;
    }

    /**
     * Creates the progress.
     *
     * @param params the {@link WorkDoneProgressCreateParams} to be used to create the progress
     * @return the completable future
     */
    @NotNull
    public CompletableFuture<Void> createProgress(final @NotNull WorkDoneProgressCreateParams params) {
        if (!isDisposed()) {
            String token = getToken(params.getToken());
            LSPProgressInfo progress = progressMap.get(token);
            if (progress != null) {
                // An LSP progress already exists with this token, cancel it.
                progress.cancel();
            }
            progressMap.put(token, new LSPProgressInfo(token));
        }
        return CompletableFuture.completedFuture(null);
    }

    private void createTask(LSPProgressInfo progressInfo) {
        String token = progressInfo.getToken();
        if (isProgressAlive(progressInfo)) {
            // The progress has been done, cancelled, or the manager has been disposed,
            // /don't create background task.
            progressMap.remove(token);
            return;
        }
        String name = this.languageServerWrapper.getServerDefinition().getDisplayName();
        String title = name + ": " + progressInfo.getTitle();
        boolean cancellable = progressInfo.isCancellable();
        ProgressManager.getInstance().run(new Task.Backgroundable(languageServerWrapper.getProject(), title, cancellable) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    while (!isProgressAlive(progressInfo)) {

                        if (indicator.isCanceled()) {
                            // The user has clicked on cancel icon of the progress bar.
                            progressMap.remove(token);
                            if (languageServer != null) {
                                // Cancel the LSP progress on language server side..
                                final var workDoneProgressCancelParams = new WorkDoneProgressCancelParams();
                                workDoneProgressCancelParams.setToken(token);
                                languageServer.cancelProgress(workDoneProgressCancelParams);
                                throw new ProcessCanceledException();
                            }
                        }

                        // Try to get the next reported progress params.
                        WorkDoneProgressNotification progressNotification;
                        try {
                            progressNotification = progressInfo.getNextProgressNotification();
                        } catch (InterruptedException e) {
                            progressMap.remove(token);
                            Thread.currentThread().interrupt();
                            throw new ProcessCanceledException(e);
                        }
                        if (progressNotification != null) {
                            WorkDoneProgressKind kind = progressNotification.getKind();
                            switch(kind) {
                                case begin -> // 'begin' has been notified
                                        begin((WorkDoneProgressBegin) progressNotification, indicator);
                                case report -> // 'report' has been notified
                                        report((WorkDoneProgressReport) progressNotification, indicator);
                            }
                        }
                    }
                } finally {
                    progressMap.remove(token);
                }
            }
        });
    }

    private boolean isProgressAlive(LSPProgressInfo progressInfo) {
        return progressInfo.isDone() || progressInfo.isCancelled() || isDisposed();
    }

    private void begin(@NotNull WorkDoneProgressBegin begin,
                       @NotNull ProgressIndicator progressIndicator) {
        Integer percentage = begin.getPercentage();
        progressIndicator.setIndeterminate(percentage == null);
        updateProgressIndicator(begin.getMessage(), percentage, progressIndicator);
    }

    private void report(@NotNull WorkDoneProgressReport report,
                        @NotNull ProgressIndicator progressIndicator) {
        updateProgressIndicator(report.getMessage(), report.getPercentage(), progressIndicator);
    }

    private void updateProgressIndicator(@Nullable String message,
                                         @Nullable Integer percentage,
                                         @NotNull ProgressIndicator progressIndicator) {
        if (message != null && !message.isBlank()) {
            progressIndicator.setText(message);
        }
        if (percentage != null) {
            progressIndicator.setFraction(percentage.doubleValue() / 100);
        }
    }

    /**
     * Notify progress.
     *
     * @param params the {@link ProgressParams} used for the progress notification
     */
    public void notifyProgress(final @NotNull ProgressParams params) {
        if (isDisposed()) {
            return;
        }
        String token = getToken(params.getToken());
        LSPProgressInfo progress = progressMap.get(token);
        if (progress == null) {
            // may happen if the server does not wait on the return value of the future of createProgress
            return;
        }
        var value = params.getValue();
        if (value == null || !value.isLeft()) {
            return;
        }
        WorkDoneProgressNotification progressNotification = value.getLeft();
        if (progressNotification != null) {
            // Add the progress notification
            progress.addProgressNotification(progressNotification);
            switch (progressNotification.getKind()) {
                case begin -> {
                    // 'begin' progress
                    WorkDoneProgressBegin begin = (WorkDoneProgressBegin) progressNotification;
                    progress.setTitle(begin.getTitle());
                    progress.setCancellable(begin.getCancellable() != null && begin.getCancellable());
                    // The IJ task is created on 'begin' and not on 'create' to initialize
                    // the Task with the 'begin' title.
                    createTask(progress);
                }
                case end -> progress.setDone(true);
            }
        }
    }

    private static String getToken(Either<String, Integer> token) {
        return token.map(Function.identity(), Object::toString);
    }

    /**
     * Dispose the progress manager.
     */
    @Override
    public void dispose() {
        this.disposed = true;
        progressMap.values().forEach(LSPProgressInfo::cancel);
        progressMap.clear();
    }

    public boolean isDisposed() {
        return disposed;
    }
}
