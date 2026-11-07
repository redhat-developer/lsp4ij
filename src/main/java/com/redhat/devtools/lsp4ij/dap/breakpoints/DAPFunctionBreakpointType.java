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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Debug Adapter Protocol (DAP) function breakpoint type: a breakpoint on a function, by its name, which the DAP server
 * resolves, rather than on a line. It is added with the "+" of the Breakpoints dialog, and sent with
 * 'setFunctionBreakpoints' to the DAP servers which support function breakpoints.
 */
public class DAPFunctionBreakpointType extends XBreakpointType<XBreakpoint<DAPFunctionBreakpointProperties>, DAPFunctionBreakpointProperties> {

    private static final String BREAKPOINT_ID = "dap-function-breakpoint";

    public DAPFunctionBreakpointType() {
        super(BREAKPOINT_ID, DAPBundle.message("dap.breakpoints.function.title"));
    }

    @Override
    public @Nls String getDisplayText(XBreakpoint<DAPFunctionBreakpointProperties> breakpoint) {
        var properties = breakpoint.getProperties();
        return properties != null ? properties.getName() : "";
    }

    @Override
    public @Nls String getGeneralDescription(XBreakpoint<DAPFunctionBreakpointProperties> breakpoint) {
        return DAPBundle.message("dap.breakpoints.function.description");
    }

    @Override
    public @NotNull DAPFunctionBreakpointProperties createProperties() {
        return new DAPFunctionBreakpointProperties();
    }

    @Override
    public boolean isAddBreakpointButtonVisible() {
        return true;
    }

    @Override
    public @Nullable XBreakpoint<DAPFunctionBreakpointProperties> addBreakpoint(Project project, JComponent parentComponent) {
        String name = Messages.showInputDialog(project,
                DAPBundle.message("dap.breakpoints.function.add.message"),
                DAPBundle.message("dap.breakpoints.function.add.title"),
                null,
                null,
                new NonEmptyInputValidator());
        if (name == null) {
            return null;
        }
        var properties = new DAPFunctionBreakpointProperties(name.strip());
        return WriteAction.compute(() -> XDebuggerManager.getInstance(project).getBreakpointManager()
                .addBreakpoint(this, properties));
    }

    @Override
    public @NotNull XBreakpointCustomPropertiesPanel<XBreakpoint<DAPFunctionBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
        return new DAPFunctionBreakpointPropertiesPanel();
    }

    @Override
    public @NotNull XDebuggerEditorsProvider getEditorsProvider(@NotNull XBreakpoint<DAPFunctionBreakpointProperties> breakpoint,
                                                                @NotNull Project project) {
        // Returns a non-null XDebuggerEditorsProvider to support Condition in the breakpoint type.
        // A function breakpoint has no file, so its condition is plain text.
        return new DAPDebuggerEditorsProvider(null, null);
    }

    @Override
    public @NotNull Icon getEnabledIcon() {
        return AllIcons.Debugger.Db_method_breakpoint;
    }

    @Override
    public @NotNull Icon getDisabledIcon() {
        return AllIcons.Debugger.Db_disabled_method_breakpoint;
    }

    @Override
    public @NotNull Icon getSuspendNoneIcon() {
        return AllIcons.Debugger.Db_no_suspend_method_breakpoint;
    }

    @Override
    public @NotNull Icon getMutedEnabledIcon() {
        return AllIcons.Debugger.Db_muted_method_breakpoint;
    }

    @Override
    public @NotNull Icon getMutedDisabledIcon() {
        return AllIcons.Debugger.Db_muted_disabled_method_breakpoint;
    }

    @Override
    public @NotNull Icon getInactiveDependentIcon() {
        return AllIcons.Debugger.Db_dep_method_breakpoint;
    }
}
