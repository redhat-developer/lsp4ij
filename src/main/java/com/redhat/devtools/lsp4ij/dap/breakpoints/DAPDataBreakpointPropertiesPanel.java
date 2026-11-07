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
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The properties panel of a DAP data breakpoint, in the Breakpoints dialog: the hit condition. The data is the one
 * which the DAP server named, and can't be changed.
 */
class DAPDataBreakpointPropertiesPanel extends XBreakpointCustomPropertiesPanel<XBreakpoint<DAPDataBreakpointProperties>> {

    private final JBTextField hitConditionField = new JBTextField();
    private final JPanel panel;

    DAPDataBreakpointPropertiesPanel() {
        hitConditionField.getEmptyText().setText(DAPBundle.message("dap.breakpoints.data.hit.condition.hint"));
        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(DAPBundle.message("dap.breakpoints.data.hit.condition"), hitConditionField)
                .getPanel();
    }

    @Override
    public @NotNull JComponent getComponent() {
        return panel;
    }

    @Override
    public void loadFrom(@NotNull XBreakpoint<DAPDataBreakpointProperties> breakpoint) {
        hitConditionField.setText(breakpoint.getProperties().getHitCondition());
    }

    @Override
    public void saveTo(@NotNull XBreakpoint<DAPDataBreakpointProperties> breakpoint) {
        var properties = breakpoint.getProperties();
        String hitCondition = hitConditionField.getText().strip();
        if (hitCondition.equals(properties.getHitCondition())) {
            return;
        }
        properties.setHitCondition(hitCondition);
        // IntelliJ doesn't see a change of the properties: tell it, so that the debug sessions send the breakpoint again
        BreakpointInternalSupport.fireBreakpointChanged(breakpoint);
    }
}
