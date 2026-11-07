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

import com.intellij.openapi.application.WriteAction;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A function breakpoint keeps its name and hit condition with the project, and its properties panel changes them.
 */
public class DAPFunctionBreakpointPropertiesTest extends HeavyPlatformTestCase {

    public void testThePropertiesAreSavedAndLoaded() {
        var properties = new DAPFunctionBreakpointProperties("Point.move");
        properties.setHitCondition(">= 3");

        var loaded = new DAPFunctionBreakpointProperties();
        loaded.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(properties.getState()), DAPFunctionBreakpointProperties.class));

        assertEquals("Point.move", loaded.getName());
        assertEquals(">= 3", loaded.getHitCondition());
    }

    public void testThePanelChangesTheNameAndTheHitCondition() {
        var type = XDebuggerUtil.getInstance().findBreakpointType(DAPFunctionBreakpointType.class);
        XBreakpoint<DAPFunctionBreakpointProperties> breakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject())
                .getBreakpointManager().addBreakpoint(type, new DAPFunctionBreakpointProperties("twice")));
        assertEquals("twice", type.getDisplayText(breakpoint));
        var changed = new ArrayList<XBreakpoint<?>>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
            @Override
            public void breakpointChanged(@NotNull XBreakpoint<?> changedBreakpoint) {
                changed.add(changedBreakpoint);
            }
        });

        var panel = new DAPFunctionBreakpointPropertiesPanel();
        try {
            panel.loadFrom(breakpoint);
            List<JBTextField> fields = UIUtil.findComponentsOfType(panel.getComponent(), JBTextField.class);
            assertEquals(2, fields.size());
            var name = fields.get(0);
            var hitCondition = fields.get(1);
            assertEquals("twice", name.getText());
            assertEquals("", hitCondition.getText());

            // nothing changed: the debug sessions are not told
            panel.saveTo(breakpoint);
            assertEmpty(changed);

            name.setText(" Point.move ");
            hitCondition.setText("% 2");
            panel.saveTo(breakpoint);
            assertEquals("Point.move", breakpoint.getProperties().getName());
            assertEquals("% 2", breakpoint.getProperties().getHitCondition());
            assertEquals(List.of(breakpoint), changed);

            // a function breakpoint keeps a name
            name.setText(" ");
            hitCondition.setText("");
            panel.saveTo(breakpoint);
            assertEquals("Point.move", breakpoint.getProperties().getName());
            assertEquals("", breakpoint.getProperties().getHitCondition());
        } finally {
            panel.dispose();
        }
    }
}
