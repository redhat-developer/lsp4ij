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

/**
 * Interface for resolving source positions of debug variables.
 */
public interface DebugVariablePositionProvider {

    /**
     * Configures the context for this provider.
     * This method should be called before resolving positions.
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
