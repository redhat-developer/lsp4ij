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
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Predicate;

/**
 * Debug Adapter Protocol (DAP) data breakpoint type: a breakpoint which stops the program when the DAP server sees
 * a value change, rather than at a line. It is added with "Break on Value Change" in the Variables view, on the data
 * which the DAP server names for the variable ('dataBreakpointInfo'), and sent with 'setDataBreakpoints' to that DAP
 * server.
 */
public class DAPDataBreakpointType extends XBreakpointType<XBreakpoint<DAPDataBreakpointProperties>, DAPDataBreakpointProperties> {

    private static final String BREAKPOINT_ID = "dap-data-breakpoint";

    public DAPDataBreakpointType() {
        super(BREAKPOINT_ID, DAPBundle.message("dap.breakpoints.data.title"));
    }

    @Override
    public @Nls String getDisplayText(XBreakpoint<DAPDataBreakpointProperties> breakpoint) {
        var properties = breakpoint.getProperties();
        if (properties == null) {
            return "";
        }
        return !properties.getDescription().isBlank() ? properties.getDescription() : properties.getDataId();
    }

    @Override
    public @Nls String getGeneralDescription(XBreakpoint<DAPDataBreakpointProperties> breakpoint) {
        return DAPBundle.message("dap.breakpoints.data.description");
    }

    @Override
    public @NotNull DAPDataBreakpointProperties createProperties() {
        return new DAPDataBreakpointProperties();
    }

    @Override
    public @NotNull XBreakpointCustomPropertiesPanel<XBreakpoint<DAPDataBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
        return new DAPDataBreakpointPropertiesPanel();
    }

    @Override
    public @NotNull XDebuggerEditorsProvider getEditorsProvider(@NotNull XBreakpoint<DAPDataBreakpointProperties> breakpoint,
                                                                @NotNull Project project) {
        // Returns a non-null XDebuggerEditorsProvider to support Condition in the breakpoint type.
        // A data breakpoint has no file, so its condition is plain text.
        return new DAPDebuggerEditorsProvider(null, null);
    }

    /**
     * Ends the debug session of the data breakpoints of the project which the given condition selects: one whose data
     * id can't persist is removed, as IntelliJ removes a temporary breakpoint when its session stops, so it is neither
     * saved with the project nor sent to a later debug session; one whose data id can persist is kept, and no longer
     * bound to the DAP server of the session.
     *
     * @param project the project.
     * @param ended   selects the data breakpoints whose debug session ended.
     */
    public static void endSession(@NotNull Project project,
                                  @NotNull Predicate<DAPDataBreakpointProperties> ended) {
        var breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
        for (var breakpoint : breakpointManager.getBreakpoints(DAPDataBreakpointType.class)) {
            var properties = breakpoint.getProperties();
            if (!ended.test(properties)) {
                continue;
            }
            if (properties.isCanPersist()) {
                properties.setServer(null);
            } else {
                breakpointManager.removeBreakpoint(breakpoint);
            }
        }
    }

    @Override
    public @NotNull Icon getEnabledIcon() {
        return AllIcons.Debugger.Db_field_breakpoint;
    }

    @Override
    public @NotNull Icon getDisabledIcon() {
        return AllIcons.Debugger.Db_disabled_field_breakpoint;
    }

    @Override
    public @NotNull Icon getSuspendNoneIcon() {
        return AllIcons.Debugger.Db_no_suspend_field_breakpoint;
    }

    @Override
    public @NotNull Icon getMutedEnabledIcon() {
        return AllIcons.Debugger.Db_muted_field_breakpoint;
    }

    @Override
    public @NotNull Icon getMutedDisabledIcon() {
        return AllIcons.Debugger.Db_muted_disabled_field_breakpoint;
    }

    @Override
    public @NotNull Icon getInactiveDependentIcon() {
        return AllIcons.Debugger.Db_dep_field_breakpoint;
    }
}
