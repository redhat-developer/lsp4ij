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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.eclipse.lsp4j.debug.ExceptionBreakpointsFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DAPExceptionBreakpointProperties extends XBreakpointProperties<DAPExceptionBreakpointProperties> {

    /**
     * The ID of the exception filter defined by {@link ExceptionBreakpointsFilter}.
     */
    private String exceptionFilterId;
    /**
     * The label of the exception filter defined by {@link ExceptionBreakpointsFilter}.
     */
    private String exceptionFilterLabel;

    @Override
    public @Nullable DAPExceptionBreakpointProperties getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull DAPExceptionBreakpointProperties state) {
        exceptionFilterId = state.exceptionFilterId;
        exceptionFilterLabel = state.exceptionFilterLabel;
    }

    /**
     * Returns the ID of the exception filter defined by {@link ExceptionBreakpointsFilter}.
     *
     * @return the ID of the exception filter defined by {@link ExceptionBreakpointsFilter}.
     */
    public String getExceptionFilterId() {
        return exceptionFilterId;
    }

    /**
     * Returns the label of the exception filter defined by {@link ExceptionBreakpointsFilter}.
     *
     * @return the label of the exception filter defined by {@link ExceptionBreakpointsFilter}.
     */
    public String getExceptionFilterLabel() {
        return exceptionFilterLabel;
    }

    public void setByFilter(@NotNull ExceptionBreakpointsFilter filter) {
        exceptionFilterId = filter.getFilter();
        exceptionFilterLabel = filter.getLabel();
    }

    public void setExceptionFilterId(String exceptionFilterId) {
        this.exceptionFilterId = exceptionFilterId;
    }

    public void setExceptionFilterLabel(String exceptionFilterLabel) {
        this.exceptionFilterLabel = exceptionFilterLabel;
    }
}
