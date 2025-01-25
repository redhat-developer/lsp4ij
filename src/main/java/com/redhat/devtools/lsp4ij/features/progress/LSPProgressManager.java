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
            getOrCreateProgressInfo(token);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void createTask(LSPProgressInfo progressInfo) {
        String token = progressInfo.getToken();
        if (isProgressAlive(progressInfo)) {
            // The progress has been done, cancelled, or the manager has been disposed.
            // --> don't create background task.
            progressMap.remove(token);
            return;
        }
        String title = languageServerWrapper.getClientFeatures().getProgressFeature()
                .getProgressTaskTitle(progressInfo.getTitle());
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
                                // Cancel the LSP progress on language server side.
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
                            switch (kind) {
                                case begin -> // 'begin' has been notified
                                        onProgressBegin((WorkDoneProgressBegin) progressNotification, indicator);
                                case report -> // 'report' has been notified
                                        onProgressReport((WorkDoneProgressReport) progressNotification, indicator);
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

    private void onProgressBegin(@NotNull WorkDoneProgressBegin begin,
                                 @NotNull ProgressIndicator progressIndicator) {
        languageServerWrapper.getClientFeatures().getProgressFeature()
                .onProgressBegin(begin, progressIndicator);
    }

    private void onProgressReport(@NotNull WorkDoneProgressReport report,
                                  @NotNull ProgressIndicator progressIndicator) {
        languageServerWrapper.getClientFeatures().getProgressFeature()
                .onProgressReport(report, progressIndicator);
    }

    private void onProgressEnd(@NotNull WorkDoneProgressEnd end) {
        languageServerWrapper.getClientFeatures().getProgressFeature()
                .onProgressEnd(end);
    }

    /**
     * Notify progress.
     *
     * @param params the {@link ProgressParams} used for the progress notification
     */
    public void notifyProgress(final @NotNull ProgressParams params) {
        if (params.getValue() == null || params.getToken() == null || isDisposed()) {
            return;
        }
        var value = params.getValue();
        if (value.isRight()) {
            // TODO: Partial Result Progress
            // https://microsoft.github.io/language-server-protocol/specifications/specification-current/#partialResults
            return;
        }

        if (!value.isLeft()) {
            return;
        }

        // Work Done Progress
        // https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workDoneProgress
        WorkDoneProgressNotification progressNotification = value.getLeft();
        WorkDoneProgressKind kind = progressNotification.getKind();
        if (kind == null) {
            return;
        }
        String token = getToken(params.getToken());
        LSPProgressInfo progress = progressMap.get(token);
        if (progress == null) {
            // The server is not spec-compliant and reports progress using server-initiated progress but didn't
            // call window/workDoneProgress/create beforehand. In that case, we check the 'kind' field of the
            // progress data. If the 'kind' field is 'begin', we set up a progress reporter anyway.
            if (kind != WorkDoneProgressKind.begin) {
                return;
            }
            progress = getOrCreateProgressInfo(token);
        }

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
            case end -> {
                WorkDoneProgressEnd end = (WorkDoneProgressEnd) progressNotification;
                onProgressEnd(end);
                progress.setDone(true);
            }
        }
    }

    @NotNull
    private synchronized LSPProgressInfo getOrCreateProgressInfo(String token) {
        LSPProgressInfo progress = progressMap.get(token);
        if (progress != null) {
            return progress;
        }
        progress = new LSPProgressInfo(token);
        progressMap.put(token, progress);
        return progress;
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
