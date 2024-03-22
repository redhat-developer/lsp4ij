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
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LSP4IJWebsiteUrlConstants;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.settings.ErrorReportingKind;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.actions.DisableLanguageServerErrorAction;
import com.redhat.devtools.lsp4ij.settings.actions.OpenUrlAction;
import com.redhat.devtools.lsp4ij.settings.actions.ReportErrorInLogAction;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

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
     * @param <T>    the result type of the future.
     * @return the future to execute.
     */
    public <T> CompletableFuture<T> execute(@NotNull CompletableFuture<T> future) {
        return execute(future, null, null);
    }

    /**
     * Add the given future to the list of the futures to cancel (when CancellationSupport.cancel() is called)
     *
     * @param future         the future to cancel when CancellationSupport.cancel() is called.
     * @param languageServer the language server which have created the LSP future and null otherwise.
     * @param featureName    the LSP feature name (ex: textDocument/completion) and null otherwise.
     * @param <T>            the result type of the future.
     * @return the future to execute.
     */
    public <T> CompletableFuture<T> execute(@NotNull CompletableFuture<T> future,
                                            @Nullable LanguageServerItem languageServer,
                                            @Nullable String featureName) {
        if (cancelled) {
            if (!future.isDone()) {
                future.cancel(true);
            }
            throw new ProcessCanceledException();
        } else {
            // Add the future to the list of the futures to cancel (when CancellationSupport.cancel() is called)
            this.futuresToCancel.add(future);
            if (languageServer != null) {
                // It is an LSP request (ex : textDocument/completion)
                // Handle the LSP request result to show LSP error (ResponseErrorException) in an IJ notification
                // In this error case, the future will return null as result instead of throwing the ResponseErrorException error
                // to avoid breaking the LSP request result of another language server (when file is associated to several language servers)
                future = future.handle(handleLSPFeatureResult(languageServer, featureName));
            }
        }
        return future;
    }

    @NotNull
    private static <T> BiFunction<T, Throwable, T> handleLSPFeatureResult(@NotNull LanguageServerItem languageServer, @Nullable String featureName) {
        return (result, error) -> {
            if (error instanceof ResponseErrorException responseErrorException) {
                if (isRequestCancelled(responseErrorException)) {
                    // Don't show cancelled error
                    return null;
                }
            }
            if (error instanceof ResponseErrorException) {
                handleLanguageServerError(languageServer, featureName, error);
                // return null as result instead of throwing the ResponseErrorException error
                // to avoid breaking the LSP request result of another language server (when file is associated to several language servers)
                return null;
            }
            if (error != null) {
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                }
                // Rethrow the error
                throw new CompletionException(error);
            }
            // Return the result
            return result;
        };
    }

    private static void handleLanguageServerError(@NotNull LanguageServerItem languageServer,
                                                  @Nullable String featureName,
                                                  @NotNull Throwable error) {
        ErrorReportingKind errorReportingKind = getReportErrorKind(languageServer);
        switch (errorReportingKind) {
            case as_notification -> // Show LSP error (ResponseErrorException) in an IJ notification
                    showNotificationError(languageServer, featureName, error);
            case in_log -> {
                // Show LSP error in the log
                String languageServerName = languageServer.getServerWrapper().getServerDefinition().getDisplayName();
                LOGGER.error("Error while consuming '" + featureName + "' with language server '" + languageServerName + "'", error);
            }
            default -> {
                // Do nothing
            }
        }
    }

    private static void showNotificationError(@NotNull LanguageServerItem languageServer, @Nullable String featureName, Throwable error) {
        String languageServerName = languageServer.getServerWrapper().getServerDefinition().getDisplayName();
        String title = languageServerName + " (" + featureName + ")";
        String content = error.getMessage();
        Notification notification = new Notification(LanguageServerBundle.message("language.server.protocol.groupId"), title, content, NotificationType.ERROR);
        notification.addAction(new DisableLanguageServerErrorAction(notification, languageServer));
        notification.addAction(new ReportErrorInLogAction(notification, languageServer));
        notification.addAction(new OpenUrlAction(LSP4IJWebsiteUrlConstants.FEEDBACK_URL));
        notification.setIcon(AllIcons.General.Error);
        Notifications.Bus.notify(notification);
    }

    /**
     * Returns the error reporting kind for the given language server.
     *
     * @param languageServer the language server.
     * @return the error reporting kind for the given language server.
     */
    @NotNull
    private static ErrorReportingKind getReportErrorKind(@NotNull LanguageServerItem languageServer) {
        String languageServerId = languageServer.getServerWrapper().getServerDefinition().getId();
        ErrorReportingKind errorReportingKind = null;
        Project project = languageServer.getServerWrapper().getProject();
        UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = project != null ? UserDefinedLanguageServerSettings.getInstance(project)
                .getLanguageServerSettings(languageServerId) : null;
        if (settings != null) {
            errorReportingKind = settings.getErrorReportingKind();
        }
        return errorReportingKind != null ? errorReportingKind : ErrorReportingKind.getDefaultValue();
    }

    private static boolean isRequestCancelled(ResponseErrorException responseErrorException) {
        ResponseError responseError = responseErrorException.getResponseError();
        return responseError != null
                && responseError.getCode() == ResponseErrorCode.RequestCancelled.getValue();
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
            if (!futureToCancel.isDone()) {
                futureToCancel.cancel(true);
            }
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
}