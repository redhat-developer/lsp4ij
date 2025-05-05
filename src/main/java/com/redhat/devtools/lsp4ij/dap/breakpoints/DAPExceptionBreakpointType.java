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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DAPExceptionBreakpointType extends XBreakpointType<XBreakpoint<DAPExceptionBreakpointProperties>, DAPExceptionBreakpointProperties> {

    private static final String BREAKPOINT_ID = "dap-exception-breakpoint";

    public DAPExceptionBreakpointType() {
        super(BREAKPOINT_ID, "DAP Exceptions");
    }

    @Override
    public @Nls String getDisplayText(XBreakpoint<DAPExceptionBreakpointProperties> breakpoint) {
        if (breakpoint.getProperties() != null) {
            return  breakpoint.getProperties().getServerName();
        }
        return "DAP Exception";
    }

    @Override
    public @Nullable DAPExceptionBreakpointProperties createProperties() {
        return new DAPExceptionBreakpointProperties();
    }

    @Override
    public @NotNull Icon getEnabledIcon() {
        return AllIcons.Debugger.Db_exception_breakpoint;
    }

    @Override
    public @NotNull Icon getDisabledIcon() {
        return AllIcons.Debugger.Db_disabled_exception_breakpoint;
    }

    @Override
    public @NotNull Icon getMutedEnabledIcon() {
        return AllIcons.Debugger.Db_exception_breakpoint;
    }

    @Override
    public @NotNull Icon getMutedDisabledIcon() {
        return AllIcons.Debugger.Db_exception_breakpoint;
    }

    @Override
    public boolean isAddBreakpointButtonVisible() {
        return true;
    }

}
