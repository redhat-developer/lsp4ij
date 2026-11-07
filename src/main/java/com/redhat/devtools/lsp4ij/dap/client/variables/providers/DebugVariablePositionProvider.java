/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.variables.providers;

import com.intellij.xdebugger.XSourcePosition;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for resolving source positions of debug variables.
 */
public interface DebugVariablePositionProvider {

    /**
     * Loads what this provider needs to configure the context and which may take time, such as the answer of a
     * language server. This method is called without a read action, before
     * {@link #configureContext(DebugVariableContext)}, which is called once the returned future is done.
     *
     * @param context the provider context
     * @return the future which is done once this provider has loaded what it needs.
     */
    default @NotNull CompletableFuture<Void> loadContext(@NotNull DebugVariableContext context) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Configures the context for this provider.
     * This method should be called before resolving positions. It is called in a read action, so it must not wait.
     *
     * @param context the provider context
     */
    void configureContext(@NotNull DebugVariableContext context);

    /**
     * Resolves the source position of the given debug variable and null otherwise.
     *
     * @param variable the debug variable
     * @param context  the provider context
     * @return the source position of the given debug variable and null otherwise.
     */
    @Nullable
    XSourcePosition getSourcePosition(@NotNull Variable variable,
                                      @NotNull DebugVariableContext context);
}
