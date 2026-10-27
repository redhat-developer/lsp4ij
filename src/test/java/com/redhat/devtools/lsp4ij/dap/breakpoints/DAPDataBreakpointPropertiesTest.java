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
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import org.eclipse.lsp4j.debug.DataBreakpointAccessType;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * A data breakpoint keeps its data, access type and hit condition with the project, but not the DAP server of its
 * session, and its properties panel changes the hit condition.
 */
public class DAPDataBreakpointPropertiesTest extends HeavyPlatformTestCase {

    public void testThePropertiesAreSavedAndLoadedWithoutTheDebugAdapter() {
        var properties = new DAPDataBreakpointProperties("7:count", "Counter.count", DataBreakpointAccessType.WRITE, true, "xtc");
        properties.setHitCondition(">= 3");
        properties.setServer((IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
                    throw new AssertionError(m.getName());
                }));

        var element = XmlSerializer.serialize(properties.getState());
        assertFalse(JDOMUtil.write(element), JDOMUtil.write(element).contains("server\""));
        var loaded = new DAPDataBreakpointProperties();
        loaded.loadState(XmlSerializer.deserialize(element, DAPDataBreakpointProperties.class));

        assertEquals("7:count", loaded.getDataId());
        assertEquals("Counter.count", loaded.getDescription());
        assertEquals(DataBreakpointAccessType.WRITE, loaded.getAccessType());
        assertEquals(">= 3", loaded.getHitCondition());
        assertTrue(loaded.isCanPersist());
        assertEquals("xtc", loaded.getServerId());
        assertNull(loaded.getServer());
    }

    public void testThePanelChangesTheHitCondition() {
        var type = XDebuggerUtil.getInstance().findBreakpointType(DAPDataBreakpointType.class);
        XBreakpoint<DAPDataBreakpointProperties> breakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject())
                .getBreakpointManager().addBreakpoint(type, new DAPDataBreakpointProperties("7:count", "Counter.count", null, false, "xtc")));
        assertEquals("Counter.count", type.getDisplayText(breakpoint));
        var changed = new ArrayList<XBreakpoint<?>>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
            @Override
            public void breakpointChanged(@NotNull XBreakpoint<?> changedBreakpoint) {
                changed.add(changedBreakpoint);
            }
        });

        var panel = new DAPDataBreakpointPropertiesPanel();
        try {
            panel.loadFrom(breakpoint);
            List<JBTextField> fields = UIUtil.findComponentsOfType(panel.getComponent(), JBTextField.class);
            assertEquals(1, fields.size());
            var hitCondition = fields.get(0);
            assertEquals("", hitCondition.getText());

            // nothing changed: the debug sessions are not told
            panel.saveTo(breakpoint);
            assertEmpty(changed);

            hitCondition.setText(" % 2 ");
            panel.saveTo(breakpoint);
            assertEquals("% 2", breakpoint.getProperties().getHitCondition());
            assertEquals(List.of(breakpoint), changed);
        } finally {
            panel.dispose();
        }
    }

    public void testADataBreakpointWithoutADescriptionShowsItsDataId() {
        var type = XDebuggerUtil.getInstance().findBreakpointType(DAPDataBreakpointType.class);
        XBreakpoint<DAPDataBreakpointProperties> breakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject())
                .getBreakpointManager().addBreakpoint(type, new DAPDataBreakpointProperties("7:count", "", null, false, "xtc")));
        assertEquals("7:count", type.getDisplayText(breakpoint));
        // it is added from a variable, not with the "+" of the Breakpoints dialog
        assertFalse(type.isAddBreakpointButtonVisible());
    }
}
