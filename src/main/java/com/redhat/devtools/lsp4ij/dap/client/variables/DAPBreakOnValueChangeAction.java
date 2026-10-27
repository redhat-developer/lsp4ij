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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPDataBreakpointHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * IntelliJ action of the Variables and Watches views, "Break on Value Change", which adds a data breakpoint on the
 * selected value: it asks the DAP server which data it would watch ('dataBreakpointInfo'), with the variables
 * reference of the value's container and the value's name, and adds a data breakpoint on that data, or reports why
 * the DAP server can't watch it. It is shown only when the DAP server supports data breakpoints.
 */
public class DAPBreakOnValueChangeAction extends AnAction implements DumbAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(DAPBreakOnValueChangeAction.class);

    private static final String BACKEND_ONLY_ACTION_BASE_NAME = "com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeBackendOnlyActionBase";

    /**
     * 'XDebuggerTreeBackendOnlyActionBase.getSelectedValue(DataContext)' of IntelliJ 2025.3 and later, or null on
     * older versions.
     */
    private static final @Nullable Method GET_SELECTED_BACKEND_VALUE = findGetSelectedBackendValue();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(getValue(e.getDataContext()) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        DAPValue value = getValue(e.getDataContext());
        if (value == null) {
            return;
        }
        var client = value.getClient();
        var server = client.getDebugProtocolServer();
        if (server == null) {
            return;
        }
        var arguments = DAPDataBreakpointHandler.createDataBreakpointInfoArguments(value.getParentVariablesReference(),
                value.getVariable().getName(),
                value.getStackFrame().getFrameId());
        client.getDataBreakpointHandler()
                .addDataBreakpoint(server, arguments)
                .exceptionally(error -> {
                    LOGGER.warn("Cannot add a data breakpoint on '{}'", arguments.getName(), error);
                    return null;
                });
    }

    /**
     * Returns the DAP value selected in the Variables or Watches view if its DAP server supports data breakpoints, and
     * null otherwise.
     *
     * @param dataContext the data context of the view.
     * @return the selected DAP value if its DAP server supports data breakpoints, and null otherwise.
     */
    static @Nullable DAPValue getValue(@NotNull DataContext dataContext) {
        return getSelectedValue(dataContext) instanceof DAPValue value
                && value.getClient().isSupportsDataBreakpoints() ? value : null;
    }

    /**
     * Returns the value selected in the Variables or Watches view as the debug process created it.
     *
     * With the split debugger of IntelliJ 2026.1 and later, the views' tree holds frontend proxies of the values,
     * not the DAP values. 'XDebuggerTreeBackendOnlyActionBase.getSelectedValue' (2025.3 and later) gives the debug
     * process' value of the selected node, and is called through Java reflection since LSP4IJ supports older versions
     * of IntelliJ.
     *
     * @param dataContext the data context of the view.
     * @return the selected value, or null if no value is selected.
     */
    private static @Nullable XValue getSelectedValue(@NotNull DataContext dataContext) {
        if (GET_SELECTED_BACKEND_VALUE != null) {
            try {
                if (GET_SELECTED_BACKEND_VALUE.invoke(null, dataContext) instanceof XValue value) {
                    return value;
                }
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Cannot get the selected value of the debugger tree", e);
            }
        }
        return XDebuggerTreeActionBase.getSelectedValue(dataContext);
    }

    private static @Nullable Method findGetSelectedBackendValue() {
        try {
            return Class.forName(BACKEND_ONLY_ACTION_BASE_NAME).getMethod("getSelectedValue", DataContext.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        }
    }
}
