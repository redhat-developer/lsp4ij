/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.console.explorer;

import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;

import javax.swing.tree.DefaultTreeModel;

public class LanguageServerProcessTreeNodeTest extends LightPlatformTestCase {

    public void testRefreshPreservesStartTimeAndRecalculatesDisplay() {
        var definition = new MockLanguageServerDefinition("test-server", false);
        var wrapper = new LanguageServerWrapper(getProject(), definition);
        var parent = new LanguageServerTreeNode(definition, getProject());
        var treeModel = new DefaultTreeModel(parent);
        var node = new LanguageServerProcessTreeNode(wrapper, treeModel);
        parent.add(node);

        node.setServerStatus(ServerStatus.starting);
        long initialStartTime = node.getStartTime();
        assertTrue("startTime must be greater than 0 when starting", initialStartTime > 0);

        // An error occurs during starting and refresh() is called directly without reflection
        wrapper.setServerError(new CannotStartProcessException("Starting failed"));
        node.refresh();

        // startTime must NOT be reset or modified
        assertEquals(initialStartTime, node.getStartTime());
        // lifecycle status must NOT be changed
        assertEquals(ServerStatus.starting, node.getServerStatus());
        // display name must be recalculated to include restart attempt info
        assertTrue("Display name must contain restart attempt info", node.getDisplayName().contains("[0/"));
    }

    public void testSecondaryTextAndTooltipConnectionDescription() {
        var definition = new MockLanguageServerDefinition("test-server", false);

        // 1. Null connection description: no secondary text and no connection in tooltip
        var wrapperNull = new LanguageServerWrapper(getProject(), definition);
        var treeModel = new DefaultTreeModel(null);
        var nodeNull = new LanguageServerProcessTreeNode(wrapperNull, treeModel);
        assertNull(nodeNull.getSecondaryText());
        String tooltipNull = nodeNull.getToolTipText();
        assertNotNull(tooltipNull);
        assertFalse(tooltipNull.contains("Connection:"));
        assertFalse(tooltipNull.contains("custom"));

        // 2. Stdio connection description
        var wrapperStdio = new LanguageServerWrapper(getProject(), definition) {
            @Override
            public String getConnectionDescription() {
                return "stdio";
            }
        };
        var nodeStdio = new LanguageServerProcessTreeNode(wrapperStdio, treeModel);
        assertEquals("stdio", nodeStdio.getSecondaryText());
        String tooltipStdio = nodeStdio.getToolTipText();
        assertNotNull(tooltipStdio);
        assertTrue(tooltipStdio.contains("<b>Connection:</b> stdio"));

        // 3. Socket connection description
        var wrapperSocket = new LanguageServerWrapper(getProject(), definition) {
            @Override
            public String getConnectionDescription() {
                return "connect to 127.0.0.1:8080";
            }
        };
        var nodeSocket = new LanguageServerProcessTreeNode(wrapperSocket, treeModel);
        assertEquals("connect to 127.0.0.1:8080", nodeSocket.getSecondaryText());
        String tooltipSocket = nodeSocket.getToolTipText();
        assertNotNull(tooltipSocket);
        assertTrue(tooltipSocket.contains("<b>Connection:</b> connect to 127.0.0.1:8080"));
    }
}
