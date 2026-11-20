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

import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.ServerInfo;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LanguageServerTreeRendererTest {

    @Test
    public void testFormatServerInfo() {
        assertEquals("zls 0.13.0", LanguageServerTreeRenderer.formatServerInfo(new ServerInfo("zls", "0.13.0")));
        assertEquals("zls", LanguageServerTreeRenderer.formatServerInfo(new ServerInfo("zls")));

        ServerInfo versionOnly = new ServerInfo();
        versionOnly.setVersion("0.13.0");
        assertEquals("0.13.0", LanguageServerTreeRenderer.formatServerInfo(versionOnly));

        assertNull(LanguageServerTreeRenderer.formatServerInfo(null));
        assertNull(LanguageServerTreeRenderer.formatServerInfo(new ServerInfo()));
        assertNull(LanguageServerTreeRenderer.formatServerInfo(new ServerInfo("   ", "   ")));
    }

    @Test
    public void testCustomizeCellRendererSetsTooltipAndResetsBetweenRows() {
        LanguageServerTreeRenderer renderer = new LanguageServerTreeRenderer();
        JTree tree = new JTree();

        ServerInfo serverInfo = new ServerInfo("zls", "0.13.0");
        LanguageServerDefinition zigDef = new MockLanguageServerDefinition("zig", false) {
            @Override
            public String getDisplayName() {
                return "Zig";
            }
        };
        LanguageServerDefinition rustDef = new MockLanguageServerDefinition("rust", false) {
            @Override
            public String getDisplayName() {
                return "Rust";
            }
        };

        LanguageServerTreeNode nodeWithInfo = new LanguageServerTreeNode(zigDef) {
            @Override
            public ServerInfo getServerInfo() {
                return serverInfo;
            }
        };

        LanguageServerTreeNode nodeWithoutInfo = new LanguageServerTreeNode(rustDef) {
            @Override
            public ServerInfo getServerInfo() {
                return null;
            }
        };

        // Render first node with ServerInfo: tooltip must include server info and language server details
        renderer.customizeCellRenderer(tree, nodeWithInfo, false, false, true, 0, false);
        String tooltipWithInfo = renderer.getToolTipText();
        assertNotNull(tooltipWithInfo);
        assertTrue(tooltipWithInfo.contains("zls 0.13.0"));
        assertTrue(tooltipWithInfo.contains("<b>Server:</b> zls 0.13.0"));
        assertTrue(tooltipWithInfo.contains("<b>Language Server:</b> Zig (zig)"));
        assertTrue(tooltipWithInfo.contains("<b>Status:</b>"));

        // Render second node without ServerInfo: tooltip must NOT contain server info
        renderer.customizeCellRenderer(tree, nodeWithoutInfo, false, false, true, 1, false);
        String tooltipWithoutInfo = renderer.getToolTipText();
        assertNotNull(tooltipWithoutInfo);
        assertFalse(tooltipWithoutInfo.contains("zls 0.13.0"));
        assertFalse(tooltipWithoutInfo.contains("<b>Server:</b>"));
        assertTrue(tooltipWithoutInfo.contains("<b>Language Server:</b> Rust (rust)"));
        assertTrue(tooltipWithoutInfo.contains("<b>Status:</b>"));

        // Render non-LSP node: tooltip must be reset to null (no leak)
        renderer.customizeCellRenderer(tree, "other", false, false, true, 2, false);
        assertNull(renderer.getToolTipText(), "Tooltip must not leak to subsequent rows");
    }
}
