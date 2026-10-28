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
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The properties panel of a DAP line breakpoint, in the Breakpoints dialog: the hit condition.
 */
class DAPBreakpointPropertiesPanel extends XBreakpointCustomPropertiesPanel<XLineBreakpoint<DAPBreakpointProperties>> {

    private final JBTextField hitConditionField = new JBTextField();
    private final JPanel panel;

    DAPBreakpointPropertiesPanel() {
        hitConditionField.getEmptyText().setText(DAPBundle.message("dap.breakpoints.hit.condition.hint"));
        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(DAPBundle.message("dap.breakpoints.hit.condition"), hitConditionField)
                .getPanel();
    }

    @Override
    public @NotNull JComponent getComponent() {
        return panel;
    }

    @Override
    public void loadFrom(@NotNull XLineBreakpoint<DAPBreakpointProperties> breakpoint) {
        hitConditionField.setText(breakpoint.getProperties().getHitCondition());
    }

    @Override
    public void saveTo(@NotNull XLineBreakpoint<DAPBreakpointProperties> breakpoint) {
        var properties = breakpoint.getProperties();
        String hitCondition = hitConditionField.getText().strip();
        if (hitCondition.equals(properties.getHitCondition())) {
            return;
        }
        properties.setHitCondition(hitCondition);
        // IntelliJ doesn't see a change of the properties: tell it, so that the debug sessions send the breakpoint again
        ((XBreakpointBase<?, ?, ?>) breakpoint).fireBreakpointChanged();
    }
}
