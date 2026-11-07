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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.NotNull;

/**
 * Debug Adapter Protocol (DAP) function breakpoint properties: the name of the function, as the DAP server knows it,
 * and the hit condition, which IntelliJ breakpoints don't have.
 */
public class DAPFunctionBreakpointProperties extends XBreakpointProperties<DAPFunctionBreakpointProperties> {

    private @NotNull String name = "";
    private @NotNull String hitCondition = "";

    public DAPFunctionBreakpointProperties() {
    }

    public DAPFunctionBreakpointProperties(@NotNull String name) {
        this.name = name;
    }

    /**
     * Returns the name of the function.
     *
     * @return the name of the function.
     */
    public @NotNull String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    /**
     * Returns the hit condition, which the DAP server interprets, and an empty string if there is none.
     *
     * @return the hit condition, and an empty string if there is none.
     */
    public @NotNull String getHitCondition() {
        return hitCondition;
    }

    public void setHitCondition(@NotNull String hitCondition) {
        this.hitCondition = hitCondition;
    }

    @Override
    public @NotNull DAPFunctionBreakpointProperties getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull DAPFunctionBreakpointProperties state) {
        name = state.name;
        hitCondition = state.hitCondition;
    }
}
