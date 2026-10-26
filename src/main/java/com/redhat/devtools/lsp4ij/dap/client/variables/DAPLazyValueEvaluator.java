/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.variables;

import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import org.eclipse.lsp4j.debug.ValueFormat;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * The "Show value" link of a lazy DAP variable, whose value the debug adapter computes only when asked, as getting
 * it may run code (presentationHint.lazy). The variable's variablesReference gives the variable with its value.
 */
public class DAPLazyValueEvaluator extends XFullValueEvaluator {

    private final @NotNull IDebugProtocolServer server;
    private final int variablesReference;
    private final @Nullable ValueFormat format;
    private final @NotNull Consumer<Variable> resolved;
    // The request for the value: a click while it runs waits for it instead of asking again, as asking may run code
    private @Nullable CompletableFuture<@Nullable Variable> request;

    /**
     * @param server             the debug adapter.
     * @param variablesReference the lazy variable's variablesReference.
     * @param format             the format the value is asked for in, or null for the debug adapter's own.
     * @param resolved           what shows the variable with its value, in place of the lazy one.
     */
    public DAPLazyValueEvaluator(@NotNull IDebugProtocolServer server,
                                 int variablesReference,
                                 @Nullable ValueFormat format,
                                 @NotNull Consumer<Variable> resolved) {
        super(DAPBundle.message("dap.variables.lazy.show"));
        this.server = server;
        this.variablesReference = variablesReference;
        this.format = format;
        this.resolved = resolved;
        // The value replaces the link in the tree instead of showing in a popup
        setShowValuePopup(false);
    }

    /**
     * Returns true if the given DAP variable is lazy, and false otherwise.
     *
     * @param variable the DAP variable.
     * @return true if the given DAP variable is lazy, and false otherwise.
     */
    public static boolean isLazy(@NotNull Variable variable) {
        var hint = variable.getPresentationHint();
        return hint != null && Boolean.TRUE.equals(hint.getLazy()) && variable.getVariablesReference() > 0;
    }

    @Override
    public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
        getRequest()
                .whenComplete((variable, error) -> {
                    if (error != null) {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                        callback.errorOccurred(cause.getMessage() != null ? cause.getMessage() : cause.toString());
                        return;
                    }
                    if (variable == null) {
                        callback.errorOccurred(DAPBundle.message("dap.variables.lazy.none"));
                        return;
                    }
                    // Shown once: the variable now has its value
                    setIsEnabled(false);
                    resolved.accept(variable);
                    callback.evaluated(variable.getValue() != null ? variable.getValue() : "");
                });
    }

    /**
     * Returns the request for the value, the one which runs or gave the value, or a new one. A request which fails or
     * gives no variable is asked again at the next click.
     */
    private synchronized @NotNull CompletableFuture<@Nullable Variable> getRequest() {
        if (request == null) {
            CompletableFuture<@Nullable Variable> newRequest = resolve(server, variablesReference, format);
            request = newRequest;
            newRequest.whenComplete((variable, error) -> {
                if (error != null || variable == null) {
                    forget(newRequest);
                }
            });
            return newRequest;
        }
        return request;
    }

    private synchronized void forget(@NotNull CompletableFuture<@Nullable Variable> failed) {
        if (request == failed) {
            request = null;
        }
    }

    /**
     * Returns the variable with the value of a lazy variable, the first variable of its variablesReference, and
     * null if there is none.
     *
     * @param server             the debug adapter.
     * @param variablesReference the lazy variable's variablesReference.
     * @param format             the format the value is asked for in, or null for the debug adapter's own.
     * @return the variable with the value of a lazy variable, and null if there is none.
     */
    static CompletableFuture<@Nullable Variable> resolve(@NotNull IDebugProtocolServer server,
                                                         int variablesReference,
                                                         @Nullable ValueFormat format) {
        VariablesArguments args = new VariablesArguments();
        args.setVariablesReference(variablesReference);
        args.setFormat(format);
        return server.variables(args)
                .thenApply(response -> response != null && response.getVariables() != null && response.getVariables().length > 0 ?
                        response.getVariables()[0] : null);
    }
}
