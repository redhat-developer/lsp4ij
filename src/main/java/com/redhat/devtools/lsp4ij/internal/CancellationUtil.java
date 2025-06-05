/*******************************************************************************
 * Copyright (c) 2023 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Group AG) - Initial Implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Cancellation utilities.
 * <p>
 * This class is a copy/paste from <a href="https://github.com/eclipse/lsp4e/blob/master/org.eclipse.lsp4e/src/org/eclipse/lsp4e/internal/CancellationUtil.java">https://github.com/eclipse/lsp4e/blob/master/org.eclipse.lsp4e/src/org/eclipse/lsp4e/internal/CancellationUtil.java</a>
 */
public final class CancellationUtil {

    private CancellationUtil() {
        // this class shouldn't be instantiated
    }

    public static boolean isRequestCancelledException(@Nullable Throwable throwable) {
        if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
            throwable = throwable.getCause();
        }
        if (throwable instanceof ResponseErrorException) {
            return isRequestCancelled((ResponseErrorException) throwable);
        }
        return throwable instanceof CancellationException;
    }

    public static boolean isRequestCancelled(@NotNull ResponseErrorException responseErrorException) {
        return isResponseErrorCode(responseErrorException, ResponseErrorCode.RequestCancelled, ResponseErrorCode.ServerCancelled);
    }

    public static boolean isContentModified(@NotNull ResponseErrorException responseErrorException) {
        return isResponseErrorCode(responseErrorException, ResponseErrorCode.ContentModified);
    }

    private static boolean isResponseErrorCode(@NotNull ResponseErrorException responseErrorException,
                                               @NotNull ResponseErrorCode... errorCodes) {
        ResponseError responseError = responseErrorException.getResponseError();
        if (responseError == null || errorCodes == null) {
            return false;
        }
        int code = responseError.getCode();
        for (var errorCode : errorCodes) {
            if (code == errorCode.getValue()) {
                return true;
            }
        }
        return false;
    }
}