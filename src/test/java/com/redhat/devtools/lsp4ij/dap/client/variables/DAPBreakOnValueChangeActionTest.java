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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPDataBreakpointHandler;
import org.eclipse.lsp4j.debug.Capabilities;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * "Break on Value Change" is in the context menu of the Variables and Watches views, which other debuggers share: it
 * is shown for a value of a DAP server which supports data breakpoints only.
 */
public class DAPBreakOnValueChangeActionTest extends LightPlatformTestCase {

    public void testTheActionIsInTheVariablesAndWatchesMenu() {
        var actionManager = ActionManager.getInstance();
        var action = actionManager.getAction("DAP.BreakOnValueChange");
        assertInstanceOf(action, DAPBreakOnValueChangeAction.class);
        assertEquals("Break on Value Change", action.getTemplatePresentation().getText());

        var group = (DefaultActionGroup) actionManager.getAction("XDebugger.ValueGroup");
        var children = Arrays.stream(group.getChildActionsOrStubs()).map(actionManager::getId).toList();
        assertTrue(children.toString(), children.contains("DAP.BreakOnValueChange"));
    }

    public void testTheActionIsShownOnlyWhenTheDebugAdapterSupportsDataBreakpoints() {
        // DAPValue#getClient#isSupportsDataBreakpoints reads the capabilities of its DAP server
        assertFalse(DAPDataBreakpointHandler.isSupportsDataBreakpoints(new Capabilities()));
        var capabilities = new Capabilities();
        capabilities.setSupportsDataBreakpoints(true);
        assertTrue(DAPDataBreakpointHandler.isSupportsDataBreakpoints(capabilities));
    }

    public void testTheActionIsHiddenWithoutADAPValue() {
        // nothing selected
        assertHidden(SimpleDataContext.getSimpleContext(XDebuggerTree.SELECTED_NODES, List.of()));

        // a value of another debugger, which shares the menu
        var tree = new XDebuggerTree(getProject(), new DAPDebuggerEditorsProvider(null, null), null,
                XDebuggerActions.VARIABLES_TREE_POPUP_GROUP, null);
        Disposer.register(getTestRootDisposable(), tree);
        var node = new XValueNodeImpl(tree, null, "count", new XValue() {
            @Override
            public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
                node.setPresentation(null, "int", "2", false);
            }
        });
        assertHidden(SimpleDataContext.getSimpleContext(XDebuggerTree.SELECTED_NODES, List.of(node)));
    }

    private static void assertHidden(@NotNull DataContext dataContext) {
        assertNull(DAPBreakOnValueChangeAction.getValue(dataContext));
        var action = ActionManager.getInstance().getAction("DAP.BreakOnValueChange");
        var event = TestActionEvent.createTestEvent(action, dataContext);
        action.update(event);
        assertFalse(event.getPresentation().isEnabledAndVisible());
    }
}
