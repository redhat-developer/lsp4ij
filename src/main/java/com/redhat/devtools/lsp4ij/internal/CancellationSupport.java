/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ExceptionUtil;
import com.redhat.devtools.lsp4ij.LSP4IJWebsiteUrlConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.ErrorReportingKind;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.actions.DisableLanguageServerErrorAction;
import com.redhat.devtools.lsp4ij.settings.actions.OpenUrlAction;
import com.redhat.devtools.lsp4ij.settings.actions.ReportErrorInLogAction;
import com.redhat.devtools.lsp4ij.settings.actions.ShowErrorLogAction;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiFunction;

import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled;
import static com.redhat.devtools.lsp4ij.internal.CancellationUtil.isContentModified;
import static com.redhat.devtools.lsp4ij.internal.CancellationUtil.isRequestCancelled;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * LSP cancellation support hosts the list of LSP requests to cancel when a
 * process is canceled (ex: when completion is re-triggered, when hover is give
 * up, etc.)
 *
 * @see <a href=
 * "https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#cancelRequest">https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#cancelRequest</a>
 */
public class CancellationSupport implements CancelChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(CancellationSupport.class);

    private static final int MAX_REJECTED_EXECUTIONS_BEFORE_CANCELLATION = 16;

    private final List<CompletableFuture<?>> futuresToCancel;

    private boolean cancelled;

    public CancellationSupport() {
        this.futuresToCancel = new CopyOnWriteArrayList<>();
        this.cancelled = false;
    }

    /**
     * Add the given future to the list of the futures to cancel (when CancellationSupport.cancel() is called)
     *
     * @param future the future to cancel when CancellationSupport.cancel() is called
     * @param <T>    the response type of the future.
     * @return the future to execute.
     */
    public <T> CompletableFuture<T> execute(@NotNull CompletableFuture<T> future) {
        return execute(future, (LanguageServerItem) null, null);
    }

    /**
     * Add the given future to the list of the futures to cancel (when CancellationSupport.cancel() is called)
     *
     * @param future         the future to cancel when CancellationSupport.cancel() is called.
     * @param languageServer the language server which have created the LSP future and null otherwise.
     * @param featureName    the LSP feature name (ex: textDocument/completion) and null otherwise.
     * @param <T>            the response type of the future.
     * @return the future to execute.
     */
    public <T> CompletableFuture<T> execute(@NotNull CompletableFuture<T> future,
                                            @Nullable LanguageServerItem languageServer,
                                            @Nullable String featureName) {
        return execute(future, languageServer, featureName, true);
    }

    /**
     * Add the given future to the list of the futures to cancel (when CancellationSupport.cancel() is called)
     *
     * @param future                    the future to cancel when CancellationSupport.cancel() is called.
     * @param languageServer            the language server which have created the LSP future and null otherwise.
     * @param featureName               the LSP feature name (ex: textDocument/completion) and null otherwise.
     * @param handleLanguageServerError true if the error coming from language server are caught and displayed as notification, log, ignore and false otherwise.
     * @param <T>                       the response type of the future.
     * @return the future to execute.
     */
    public <T> CompletableFuture<T> execute(@NotNull CompletableFuture<T> future,
                                            @Nullable LanguageServerItem languageServer,
                                            @Nullable String featureName,
                                            boolean handleLanguageServerError) {
        if (cancelled) {
            CancellationSupport.cancel(future);
            throw new ProcessCanceledException();
        } else {
            // Add the future to the list of the futures to cancel (when CancellationSupport.cancel() is called)
            this.futuresToCancel.add(future);
            if (languageServer != null) {
                // It is an LSP request (ex : textDocument/completion)
                // Handle the LSP request response to show LSP error (ResponseErrorException) in an IJ notification
                // In this error case, the future will return null as response instead of throwing the ResponseErrorException error
                // to avoid breaking the LSP request response of another language server (when file is associated to several language servers)
                future = future.handle(handleLSPFeatureResult(languageServer, featureName, handleLanguageServerError));
            }
        }
        return future;
    }

    @NotNull
    private static <T> BiFunction<T, Throwable, T> handleLSPFeatureResult(@NotNull LanguageServerItem languageServer,
                                                                          @Nullable String featureName,
                                                                          boolean handleLanguageServerError) {
        return (result, error) -> {
            if (error instanceof ResponseErrorException responseError) {
                if (isRequestCancelled(responseError)) {
                    // Don't show cancelled error
                    return null;
                }
                if (isContentModified(responseError)) {
                    // Ignore the content modified error
                    return null;
                }
                if (handleLanguageServerError) {
                    handleLanguageServerError(languageServer, featureName, responseError);
                    // return null as response instead of throwing the ResponseErrorException error
                    // to avoid breaking the LSP request response of another language server (when file is associated to several language servers)
                    return null;
                }
            }
            if (error != null) {
                if (error instanceof CancellationException) {
                    // This case occurs when an LSP request is cancelled and call https://github.com/eclipse-lsp4j/lsp4j/blob/783b6e788c1e374b6de63f69804c7d0f96be4da2/org.eclipse.lsp4j.jsonrpc/src/main/java/org/eclipse/lsp4j/jsonrpc/RemoteEndpoint.java#L161
                    // if CancellationException is rethrown, the call of super.cancel(...) throws again CancellationException
                    // and logs strange error like
                    // - https://github.com/redhat-developer/lsp4ij/issues/1204
                    // - Caused by: java.util.concurrent.CancellationException
                    //	at java.base/java.util.concurrent.CompletableFuture.cancel(CompletableFuture.java:2478)
                    // To fix those issues, we ignore CancellationException in this case.
                    return null;
                }
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                }
                // Rethrow the error
                throw new CompletionException(error);
            }
            // Return the response
            return result;
        };
    }

    private static void handleLanguageServerError(@NotNull LanguageServerItem languageServer,
                                                  @Nullable String featureName,
                                                  @NotNull ResponseErrorException error) {
        ErrorReportingKind errorReportingKind = getReportErrorKind(languageServer);
        switch (errorReportingKind) {
            case as_notification -> // Show LSP error (ResponseErrorException) in an IJ notification
                    showNotificationError(languageServer.getServerDefinition(), featureName, error, languageServer.getProject());
            case in_log -> {
                // Show LSP error in the log
                String languageServerName = languageServer.getServerDefinition().getDisplayName();
                LOGGER.error("Error while consuming '" + featureName + "' with language server '" + languageServerName + "'", error);
            }
            default -> {
                // Do nothing
            }
        }
    }

    @ApiStatus.Internal
    public static Notification showNotificationError(@NotNull LanguageServerDefinition languageServerDefinition,
                                                     @Nullable String subtitle,
                                                     @NotNull Exception error,
                                                     @NotNull Project project) {
        String languageServerName = languageServerDefinition.getDisplayName();
        String content = error.getMessage();
        Notification notification = new Notification(ServerMessageHandler.LSP_WINDOW_SHOW_MESSAGE_GROUP_ID,
                languageServerName,
                content,
                NotificationType.ERROR);
        notification.setSubtitle(subtitle);
        notification.addAction(new ShowErrorLogAction(languageServerDefinition, project));
        notification.addAction(new DisableLanguageServerErrorAction(notification, languageServerDefinition, project));
        notification.addAction(new ReportErrorInLogAction(notification, languageServerDefinition, project));
        notification.addAction(new OpenUrlAction(LSP4IJWebsiteUrlConstants.FEEDBACK_URL));
        notification.setIcon(AllIcons.General.Error);
        Notifications.Bus.notify(notification, project);
        return notification;
    }

    /**
     * Returns the error reporting kind for the given language server.
     *
     * @param languageServer the language server.
     * @return the error reporting kind for the given language server.
     */
    @NotNull
    private static ErrorReportingKind getReportErrorKind(@NotNull LanguageServerItem languageServer) {
        String languageServerId = languageServer.getServerDefinition().getId();
        ErrorReportingKind errorReportingKind = null;
        Project project = languageServer.getProject();
        ProjectLanguageServerSettings.LanguageServerDefinitionSettings settings = ProjectLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerId);
        if (settings != null) {
            errorReportingKind = settings.getErrorReportingKind();
        }
        return errorReportingKind != null ? errorReportingKind : ErrorReportingKind.getDefaultValue();
    }

    /**
     * Cancel all LSP requests.
     */
    public void cancel() {
        if (cancelled) {
            return;
        }
        this.cancelled = true;
        for (CompletableFuture<?> futureToCancel : futuresToCancel) {
            CancellationSupport.cancel(futureToCancel);
        }
        futuresToCancel.clear();
    }

    @Override
    public void checkCanceled() {
        // When LSP requests are called (ex : 'textDocument/completion') the LSP
        // response
        // items are used to compose some UI item (ex : LSP CompletionItem are translate
        // to IJ LookupElement fo).
        // If the cancel occurs after the call of those LSP requests, the component
        // which uses the LSP responses
        // can call checkCanceled to stop the UI creation.
        if (cancelled) {
            throw new CancellationException();
        }
    }

    public static void forwardCancellation(CompletableFuture<?> from, CompletableFuture<?>... to) {
        from.exceptionally(t -> {
            if (t instanceof CancellationException) {
                if (to != null) {
                    for (var f : to) {
                        cancel(f);
                    }
                }
            }
            return null;
        });
    }

    /**
     * Cancel the given future with any error.
     *
     * @param future the future to cancel.
     */
    public static void cancel(@Nullable Future<?> future) {
        if (future != null && !future.isDone()) {
            try {
                future.cancel(true);
            } catch (Throwable e) {
                // Ignore any error while cancelling the future.
                LOGGER.warn("Unexpected error while cancelling future", e);
            }
        }
    }

    public static <T> T awaitWithCheckCanceled(@NotNull Future<T> future) {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        return (T)awaitWithCheckCanceled(future, indicator);
    }

    public static <T> T awaitWithCheckCanceled(@NotNull Future<T> future, @Nullable ProgressIndicator indicator) {
        int rejectedExecutions = 0;
        while (true) {
            checkCancelledEvenWithPCEDisabled(indicator);
            try {
                return future.get(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, MILLISECONDS);
            }
            catch (TimeoutException ignore) {
            }
            //TODO RC: in a non-cancellable section we could still (re-)throw a (P)CE if the _awaited_ code gets cancelled
            //         (nowadays it is mistakenly considered an error) -- [Daniil et all, private conversation]
            catch (CancellationException e) {
                // In LSP4IJ context, the future can be cancelled, we need to catch this exception
                // to avoid logging CancellationException as an error
                // See https://github.com/JetBrains/intellij-community/blob/b764e70363e0c967a536256d0e057ea1f9053ff7/platform/ide-core-impl/src/com/intellij/openapi/progress/util/ProgressIndicatorUtils.java#L373
                throw new ProcessCanceledException(e);
            }
            catch (RejectedExecutionException ree) {
                //EA-225412: FJP throws REE (which propagates through futures) e.g. when FJP reaches max
                // threads while compensating for too many managedBlockers -- or when it is shutdown.

                //This branch creates a risk of infinite loop -- i.e. if the current thread itself is somehow
                // responsible for FJP resource exhaustion, hence can't release anything, each consequent
                // future.get() will throw the same REE again and again. So let's limit retries:
                rejectedExecutions++;
                if (rejectedExecutions > MAX_REJECTED_EXECUTIONS_BEFORE_CANCELLATION) {
                    //RC: It would be clearer to rethrow ree itself -- but I doubt many callers are ready for it,
                    //    while all callers are ready for PCE, hence...
                    throw new ProcessCanceledException(ree);
                }
            }
            catch (InterruptedException e) {
                throw new ProcessCanceledException(e);
            }
            catch (Throwable e) {
                Throwable cause = e.getCause();
                if (cause instanceof ProcessCanceledException) {
                    throw (ProcessCanceledException)cause;
                }
                if (cause instanceof CancellationException) {
                    throw new ProcessCanceledException(cause);
                }
                ExceptionUtil.rethrow(e);
            }
        }

    }
}