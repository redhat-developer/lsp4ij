/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.evaluation;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.DAPValue;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletionException;

/**
 * Debug Adapter Protocol (DAP) debugger evaluator.
 */
public class DAPDebuggerEvaluator extends XDebuggerEvaluator {

    private final @NotNull DAPDebugProcess debugProcess;

    public DAPDebuggerEvaluator(@NotNull DAPDebugProcess debugProcess) {
        this.debugProcess = debugProcess;
    }

    @Override
    public void evaluate(@NotNull String expression,
                         @NotNull XEvaluationCallback callback,
                         @Nullable XSourcePosition expressionPosition) {
        DAPStackFrame stackFrame = debugProcess.getCurrentDapStackFrame();
        if (stackFrame == null) {
            return;
        }
        DAPClient client = stackFrame.getClient();
        int frameId = stackFrame.getFrameId();
        client.evaluate(expression, frameId)
                .thenAccept(evaluateResponse -> {
                    Variable variable = new Variable();
                    variable.setName(expression); // DAPValue which extends XNamedValue requires a non-null name
                    variable.setValue(evaluateResponse.getResult());
                    variable.setType(evaluateResponse.getType());
                    variable.setVariablesReference(evaluateResponse.getVariablesReference());
                    variable.setNamedVariables(evaluateResponse.getNamedVariables());
                    variable.setIndexedVariables(evaluateResponse.getIndexedVariables());
                    callback.evaluated(new DAPValue(client, variable));
                }).exceptionally(error -> {
                    if (error instanceof CompletionException && error.getCause() != null) {
                        error = error.getCause();
                    }
                    String errorMessage = getErrorMessage(error);
                    callback.errorOccurred(errorMessage);
                    return null;
                });
    }

    private static String getErrorMessage(@NotNull Throwable error) {
        if (error instanceof ResponseErrorException responseErrorException) {
            if (responseErrorException.getResponseError() != null) {
                String message = responseErrorException.getResponseError().getMessage();
                if (StringUtils.isNotBlank(message)) {
                    return message;
                }
            }
        }
        return error.getMessage();
    }

}
