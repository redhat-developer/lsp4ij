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

import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The properties panel of a DAP function breakpoint, in the Breakpoints dialog: the name of the function and the hit
 * condition.
 */
class DAPFunctionBreakpointPropertiesPanel extends XBreakpointCustomPropertiesPanel<XBreakpoint<DAPFunctionBreakpointProperties>> {

    private final JBTextField nameField = new JBTextField();
    private final JBTextField hitConditionField = new JBTextField();
    private final JPanel panel;

    DAPFunctionBreakpointPropertiesPanel() {
        hitConditionField.getEmptyText().setText(DAPBundle.message("dap.breakpoints.function.hit.condition.hint"));
        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(DAPBundle.message("dap.breakpoints.function.name"), nameField)
                .addLabeledComponent(DAPBundle.message("dap.breakpoints.function.hit.condition"), hitConditionField)
                .getPanel();
    }

    @Override
    public @NotNull JComponent getComponent() {
        return panel;
    }

    @Override
    public void loadFrom(@NotNull XBreakpoint<DAPFunctionBreakpointProperties> breakpoint) {
        var properties = breakpoint.getProperties();
        nameField.setText(properties.getName());
        hitConditionField.setText(properties.getHitCondition());
    }

    @Override
    public void saveTo(@NotNull XBreakpoint<DAPFunctionBreakpointProperties> breakpoint) {
        var properties = breakpoint.getProperties();
        String name = nameField.getText().strip();
        if (name.isEmpty()) {
            // A function breakpoint names its function: an empty name keeps the one it has
            name = properties.getName();
        }
        String hitCondition = hitConditionField.getText().strip();
        if (name.equals(properties.getName()) && hitCondition.equals(properties.getHitCondition())) {
            return;
        }
        properties.setName(name);
        properties.setHitCondition(hitCondition);
        // IntelliJ doesn't see a change of the properties: tell it, so that the debug sessions send the breakpoint again
        ((XBreakpointBase<?, ?, ?>) breakpoint).fireBreakpointChanged();
    }
}
