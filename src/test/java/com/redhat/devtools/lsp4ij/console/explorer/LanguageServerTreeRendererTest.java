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

import org.eclipse.lsp4j.ServerInfo;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JTree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        LanguageServerTreeNode nodeWithInfo = new LanguageServerTreeNode(null) {
            @Override
            public String getDisplayName() {
                return "Zig";
            }

            @Override
            public Icon getIcon() {
                return null;
            }

            @Override
            public ServerInfo getServerInfo() {
                return serverInfo;
            }
        };

        LanguageServerTreeNode nodeWithoutInfo = new LanguageServerTreeNode(null) {
            @Override
            public String getDisplayName() {
                return "Rust";
            }

            @Override
            public Icon getIcon() {
                return null;
            }

            @Override
            public ServerInfo getServerInfo() {
                return null;
            }
        };

        // Render first node with ServerInfo
        renderer.customizeCellRenderer(tree, nodeWithInfo, false, false, true, 0, false);
        assertEquals("zls 0.13.0", renderer.getToolTipText());

        // Render second node without ServerInfo: tooltip must be reset to null (no leak)
        renderer.customizeCellRenderer(tree, nodeWithoutInfo, false, false, true, 1, false);
        assertNull(renderer.getToolTipText(), "Tooltip must not leak to subsequent rows");
    }
}
