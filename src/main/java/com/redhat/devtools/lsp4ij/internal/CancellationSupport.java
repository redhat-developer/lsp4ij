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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * LSP cancellation support hosts the list of LSP requests to cancel when a
 * process is canceled (ex: when completion is re-triggered, when hover is give
 * up, etc)
 *
 * @see <a href=
 * "https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#cancelRequest">https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#cancelRequest</a>
 */
public class CancellationSupport implements CancelChecker {

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
     * @return the future to execute.
     * @param <T> the result type of the future.
     */
    public <T> CompletableFuture<T> execute(@NotNull CompletableFuture<T> future) {
        return execute(future, null, null);
    }

    /**
     * Add the given future to the list of the futures to cancel (when CancellationSupport.cancel() is called)
     *
     * @param future the future to cancel when CancellationSupport.cancel() is called.
     * @param languageServer the language server which have created the LSP future and null otherwise.
     * @param featureName the LSP feature name (ex: textDocument/completion) and null otherwise.
     * @return the future to execute.
     * @param <T> the result type of the future.
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
    private static <T> BiFunction<T, Throwable, T> handleLSPFeatureResult(@Nullable LanguageServerItem languageServer, @Nullable String featureName) {
        return (result, error) -> {
            if (error instanceof ResponseErrorException responseErrorException) {
                if (isRequestCancelled(responseErrorException)) {
                    // Don't show cancelled error
                    return null;
                }
                // Show LSP error (ResponseErrorException) in an IJ notification
                MessageParams messageParams = new MessageParams(MessageType.Error, error.getMessage());
                ServerMessageHandler.showMessage(languageServer.getServerWrapper().serverDefinition.getDisplayName() + " (" + featureName + ")", messageParams);
                // return null as result instead of throwing the ResponseErrorException error
                // to avoid breaking the LSP request result of another language server (when file is associated to several language servers)
                return null;
            } else if (error != null) {
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