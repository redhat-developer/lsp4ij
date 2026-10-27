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
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.testFramework.LightPlatformTestCase;
import org.eclipse.lsp4j.debug.Capabilities;

import java.util.Arrays;

/** Values are asked for in hexadecimal when the user chose it and the DAP server formats values. */
public class DAPValueFormatTest extends LightPlatformTestCase {

    public void testHexadecimalOnlyWhenChosenAndSupported() {
        var supported = new Capabilities();
        supported.setSupportsValueFormattingOptions(true);
        var format = new DAPValueFormat();

        // the DAP server's own format until the user chooses hexadecimal
        assertNull(format.toValueFormat(supported));

        format.setHexadecimal(true);
        assertTrue(format.toValueFormat(supported).getHex());
        // a DAP server which doesn't format values is not asked to
        assertNull(format.toValueFormat(new Capabilities()));

        format.setHexadecimal(false);
        assertNull(format.toValueFormat(supported));
    }

    public void testTheActionIsInTheVariablesAndWatchesMenu() {
        var actionManager = ActionManager.getInstance();
        var action = actionManager.getAction("DAP.HexadecimalValues");
        assertInstanceOf(action, DAPHexadecimalValuesAction.class);

        var group = (DefaultActionGroup) actionManager.getAction("XDebugger.ValueGroup");
        var children = Arrays.stream(group.getChildActionsOrStubs()).map(actionManager::getId).toList();
        assertTrue(children.toString(), children.contains("DAP.HexadecimalValues"));
    }
}
