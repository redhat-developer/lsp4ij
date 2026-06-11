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
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.mcp.MCPBundle;
import com.redhat.devtools.lsp4ij.mcp.MCPServerManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Main view for MCP Server management.
 *
 * Layout:
 * - Left: Tree with MCP server status and tools list
 * - Right: Tabbed panel with Server config, Tool details, and Traces
 */
public class MCPView extends SimpleToolWindowPanel implements Disposable {

    private final Project project;
    private final MCPServerManager serverManager;

    private MCPTreePanel treePanel;
    private MCPDetailPanel detailPanel;
    private OnePixelSplitter splitter;

    public MCPView(@NotNull Project project) {
        super(true, true);
        this.project = project;
        this.serverManager = MCPServerManager.getInstance();

        initUI();
        Disposer.register(this, treePanel);
        Disposer.register(this, detailPanel);
    }

    private void initUI() {
        // Create tree panel (left side)
        treePanel = new MCPTreePanel(project, serverManager);

        // Create detail panel (right side)
        detailPanel = new MCPDetailPanel(project, serverManager);

        // Listen to tree selection to update detail panel
        treePanel.addSelectionListener(node -> {
            if (node != null) {
                detailPanel.showDetailsFor(node);
            }
        });

        // Create splitter
        splitter = new OnePixelSplitter(false, 0.3f);
        splitter.setFirstComponent(treePanel.getComponent());
        splitter.setSecondComponent(detailPanel.getComponent());

        setContent(splitter);
    }

    @Override
    public void dispose() {
        // Cleanup handled by Disposer registration
    }
}
