/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

/**
 * LSP4J Response error wrapper.
 */
public class ResponseErrorExceptionWrapper extends ResponseErrorException {

    public ResponseErrorExceptionWrapper(@NotNull Throwable throwable) {
        super(toResponseError(throwable.getMessage(), throwable));
    }

    public ResponseErrorExceptionWrapper(@NotNull String message) {
        this(message, null);
    }

    public ResponseErrorExceptionWrapper(@NotNull String message,
                                         @Nullable Throwable throwable) {
        super(toResponseError(message, throwable));
    }

    @NotNull
    private static ResponseError toResponseError(@NotNull String message,
                                                 @Nullable Throwable throwable) {
        ResponseError error = new ResponseError();
        error.setMessage(message);
        error.setCode(ResponseErrorCode.InternalError);
        if (throwable != null) {
            ByteArrayOutputStream stackTrace = new ByteArrayOutputStream();
            PrintWriter stackTraceWriter = new PrintWriter(stackTrace);
            throwable.printStackTrace(stackTraceWriter);
            stackTraceWriter.flush();
            error.setData(stackTrace.toString());
        }
        return error;
    }
}
