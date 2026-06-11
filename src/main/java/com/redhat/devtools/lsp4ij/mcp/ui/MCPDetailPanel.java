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
package com.redhat.devtools.lsp4ij.mcp.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.mcp.MCPBundle;
import com.redhat.devtools.lsp4ij.mcp.MCPServerManager;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBean;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Detail panel showing tabs: Server, Tools, Traces.
 */
public class MCPDetailPanel implements Disposable {

    private final Project project;
    private final MCPServerManager serverManager;
    private final JBTabbedPane tabbedPane;

    private MCPServerConfigPanel serverConfigPanel;
    private MCPToolDetailPanel toolDetailPanel;
    private MCPTracesPanel tracesPanel;

    public MCPDetailPanel(@NotNull Project project, @NotNull MCPServerManager serverManager) {
        this.project = project;
        this.serverManager = serverManager;

        tabbedPane = new JBTabbedPane();
        tabbedPane.setBorder(JBUI.Borders.empty());

        initTabs();
    }

    private void initTabs() {
        // Server tab
        serverConfigPanel = new MCPServerConfigPanel(serverManager);
        tabbedPane.addTab(
                MCPBundle.message("mcp.tab.server"),
                serverConfigPanel.getComponent()
        );

        // Tool detail tab
        toolDetailPanel = new MCPToolDetailPanel();
        tabbedPane.addTab(
                MCPBundle.message("mcp.tab.tools"),
                toolDetailPanel.getComponent()
        );

        // Traces tab
        tracesPanel = new MCPTracesPanel(project, serverManager);
        Disposer.register(this, tracesPanel);
        tabbedPane.addTab(
                MCPBundle.message("mcp.tab.traces"),
                tracesPanel.getComponent()
        );
    }

    public JComponent getComponent() {
        return tabbedPane;
    }

    public void showDetailsFor(MCPTreePanel.MCPTreeNode node) {
        switch (node.getType()) {
            case SERVER:
                tabbedPane.setSelectedIndex(0); // Server tab
                break;
            case TOOL:
                toolDetailPanel.showTool(node.getToolBean());
                tabbedPane.setSelectedIndex(1); // Tools tab
                break;
            case TOOLS_ROOT:
                tabbedPane.setSelectedIndex(1); // Tools tab
                toolDetailPanel.clear();
                break;
        }
    }

    @Override
    public void dispose() {
        if (tracesPanel != null) {
            tracesPanel.dispose();
        }
    }
}
