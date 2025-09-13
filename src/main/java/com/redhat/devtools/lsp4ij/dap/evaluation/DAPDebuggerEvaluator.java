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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValueCallback;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValue;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionException;

/**
 * Debug Adapter Protocol (DAP) debugger evaluator.
 */
public class DAPDebuggerEvaluator extends XDebuggerEvaluator {

    private static final String ORIGIN_INTERFACE_NAME = "com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackWithOrigin";
    private static volatile boolean reflectionUnsupported = false;
    private static volatile Method getOriginMethod;
    private static volatile Class<?> originInterface;

    private final @NotNull DAPStackFrame stackFrame;

    public DAPDebuggerEvaluator(@NotNull DAPStackFrame stackFrame) {
        this.stackFrame = stackFrame;
    }

    private static @NotNull String getEvaluationContext(@NotNull XEvaluationCallback callback) {
        // As XEvaluationCallbackWithOrigin is not available on old IntelliJ version  (<=2023.3), we need to use Java reflection.
        if (reflectionUnsupported) {
            return EvaluateArgumentsContext.WATCH;
        }

        try {
            // Initialisation lazy
            if (originInterface == null || getOriginMethod == null) {
                synchronized (DAPDebuggerEvaluator.class) {
                    if (originInterface == null || getOriginMethod == null) {
                        originInterface = Class.forName(ORIGIN_INTERFACE_NAME);
                        getOriginMethod = originInterface.getMethod("getOrigin");
                    }
                }
            }

            if (originInterface.isInstance(callback)) {
                Object origin = getOriginMethod.invoke(callback);
                if (origin != null) {
                    String originName = origin.toString();
                    return switch (originName) {
                        case "INLINE", "CONSOLE",
                             "DIALOG" -> //  XEvaluationOrigin.INLINE, XEvaluationOrigin.CONSOLE, XEvaluationOrigin.DIALOG
                                EvaluateArgumentsContext.REPL;
                        case "EDITOR" -> // XEvaluationOrigin.EDITOR
                                EvaluateArgumentsContext.HOVER;
                        default -> EvaluateArgumentsContext.WATCH;
                    };
                }
            }
        } catch (ClassNotFoundException e) {
            reflectionUnsupported = true;
        } catch (Exception e) {
            // Do nothing
        }

        return EvaluateArgumentsContext.WATCH;
    }

    public static void errorOccurred(@NotNull Throwable error,
                                     @NotNull XValueCallback callback) {
        if (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        String errorMessage = getErrorMessage(error);
        callback.errorOccurred(errorMessage);
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

    @Override
    public void evaluate(@NotNull String expression,
                         @NotNull XEvaluationCallback callback,
                         @Nullable XSourcePosition expressionPosition) {
        DAPClient client = stackFrame.getClient();
        String context = getEvaluationContext(callback);
        int frameId = stackFrame.getFrameId();
        client.evaluate(expression, frameId, context)
                .thenAccept(evaluateResponse -> {
                    Variable variable = new Variable();
                    variable.setName(expression); // DAPValue which extends XNamedValue requires a non-null name
                    variable.setValue(evaluateResponse.getResult());
                    variable.setType(evaluateResponse.getType());
                    variable.setVariablesReference(evaluateResponse.getVariablesReference());
                    variable.setNamedVariables(evaluateResponse.getNamedVariables());
                    variable.setIndexedVariables(evaluateResponse.getIndexedVariables());
                    callback.evaluated(new DAPValue(stackFrame, variable, null));
                }).exceptionally(error -> {
                    errorOccurred(error, callback);
                    return null;
                });
    }

    @Override
    public @NotNull Promise<ExpressionInfo> getExpressionInfoAtOffsetAsync(
            @NotNull Project project,
            @NotNull Document document,
            int offset,
            boolean sideEffectsAllowed
    ) {
        var client = stackFrame.getClient();
        if (!client.isSupportsEvaluateForHovers()) {
            // DAP server doesn't support evaluate for hovers
            return null;
        }
        return ReadAction.nonBlocking(() -> {

                    var file = LSPIJUtils.getFile(document);
                    if (file == null || !client.getServerDescriptor().isDebuggableFile(file, project)) {
                        // The current document which is hovered is not managed by the DAP server from the current stack frame
                        return null;
                    }

                    TextRange textRange = getTextRange(document, offset);
                    if (textRange == null) {
                        return null;
                    }
                    // Extract the expression text
                    String expression = getExpression(document, offset, textRange);

                    // Build ExpressionInfo with the text range and expression string
                    return new ExpressionInfo(textRange, expression);
                })
                .withDocumentsCommitted(project)
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    protected @Nullable TextRange getTextRange(@NotNull Document document,
                                               int offset) {
        // Get the entire document text
        String text = document.getText();

        // Safety check: ensure offset is within bounds
        if (offset < 0 || offset >= text.length()) {
            return null;
        }

        // Find the start of the identifier at the given offset
        int start = offset;
        while (start > 0) {
            char ch = text.charAt(start - 1);
            if (Character.isJavaIdentifierPart(ch) || ch == '.') {
                start--;
            } else {
                break;
            }
        }

        // Find the end of the identifier at the given offset
        int end = offset;
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            end++;
        }

        // If there is no identifier at the offset, do not return an expression
        if (start == end) {
            return null;
        }
        return new TextRange(start, end);
    }

    protected @NotNull String getExpression(@NotNull Document document, int offset, TextRange textRange) {
        return document.getText(textRange);
    }
}
