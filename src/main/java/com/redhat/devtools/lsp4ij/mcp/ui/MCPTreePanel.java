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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.mcp.MCPBundle;
import com.redhat.devtools.lsp4ij.mcp.MCPServerManager;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBean;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tree panel showing MCP server and its tools.
 */
public class MCPTreePanel implements Disposable {

    private static final ExtensionPointName<MCPToolBean> EP_NAME =
            ExtensionPointName.create("com.redhat.devtools.lsp4ij.mcpTool");

    private final Project project;
    private final MCPServerManager serverManager;
    private final JBScrollPane scrollPane;
    private final Tree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;

    private final List<Consumer<MCPTreeNode>> selectionListeners = new ArrayList<>();

    public MCPTreePanel(@NotNull Project project, @NotNull MCPServerManager serverManager) {
        this.project = project;
        this.serverManager = serverManager;

        // Create tree
        rootNode = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new MCPTreeCellRenderer());

        // Setup selection listener
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof MCPTreeNode mcpNode) {
                notifySelectionListeners(mcpNode);
            }
        });

        scrollPane = new JBScrollPane(tree);
        scrollPane.setBorder(JBUI.Borders.empty());

        // Listen to server status changes to update tree
        serverManager.addStatusListener((status, message) -> {
            ApplicationManager.getApplication().invokeLater(this::refreshTree);
        });

        // Initial tree build
        ApplicationManager.getApplication().invokeLater(this::refreshTree);
    }

    public JComponent getComponent() {
        return scrollPane;
    }

    public void addSelectionListener(Consumer<MCPTreeNode> listener) {
        selectionListeners.add(listener);
    }

    private void notifySelectionListeners(MCPTreeNode node) {
        for (Consumer<MCPTreeNode> listener : selectionListeners) {
            listener.accept(node);
        }
    }

    private void refreshTree() {
        rootNode.removeAllChildren();

        // Add server node
        MCPTreeNode serverNode = new MCPTreeNode(
                MCPTreeNode.NodeType.SERVER,
                MCPBundle.message("mcp.tree.server.node"),
                null
        );
        DefaultMutableTreeNode serverTreeNode = new DefaultMutableTreeNode(serverNode);
        rootNode.add(serverTreeNode);

        // Add tools node
        List<MCPToolBean> tools = getTools();
        MCPTreeNode toolsNode = new MCPTreeNode(
                MCPTreeNode.NodeType.TOOLS_ROOT,
                MCPBundle.message("mcp.tree.tools.node", tools.size()),
                null
        );
        DefaultMutableTreeNode toolsTreeNode = new DefaultMutableTreeNode(toolsNode);
        serverTreeNode.add(toolsTreeNode);

        // Add individual tools
        for (MCPToolBean toolBean : tools) {
            MCPTreeNode toolNode = new MCPTreeNode(
                    MCPTreeNode.NodeType.TOOL,
                    toolBean.getName(),
                    toolBean
            );
            DefaultMutableTreeNode toolTreeNode = new DefaultMutableTreeNode(toolNode);
            toolsTreeNode.add(toolTreeNode);
        }

        treeModel.reload();

        // Expand server node by default
        tree.expandPath(new TreePath(serverTreeNode.getPath()));
    }

    private List<MCPToolBean> getTools() {
        return EP_NAME.getExtensionList();
    }

    @Override
    public void dispose() {
        selectionListeners.clear();
    }

    /**
     * Tree node data holder.
     */
    public static class MCPTreeNode {
        public enum NodeType {
            SERVER,
            TOOLS_ROOT,
            TOOL
        }

        private final NodeType type;
        private final String name;
        private final MCPToolBean toolBean;

        public MCPTreeNode(NodeType type, String name, MCPToolBean toolBean) {
            this.type = type;
            this.name = name;
            this.toolBean = toolBean;
        }

        public NodeType getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public MCPToolBean getToolBean() {
            return toolBean;
        }
    }

    /**
     * Custom tree cell renderer.
     */
    private class MCPTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node)) {
                return;
            }

            if (!(node.getUserObject() instanceof MCPTreeNode mcpNode)) {
                return;
            }

            switch (mcpNode.getType()) {
                case SERVER:
                    renderServerNode(mcpNode);
                    break;
                case TOOLS_ROOT:
                    renderToolsRootNode(mcpNode);
                    break;
                case TOOL:
                    renderToolNode(mcpNode);
                    break;
            }
        }

        private void renderServerNode(MCPTreeNode node) {
            append(node.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            append(" ");

            MCPServerManager.ServerStatus status = serverManager.getStatus();
            String statusText = switch (status) {
                case RUNNING -> MCPBundle.message("mcp.server.status.running");
                case STARTING -> MCPBundle.message("mcp.server.status.starting");
                case ERROR -> MCPBundle.message("mcp.server.status.error");
                default -> MCPBundle.message("mcp.server.status.stopped");
            };

            SimpleTextAttributes statusAttr = switch (status) {
                case RUNNING -> SimpleTextAttributes.SYNTHETIC_ATTRIBUTES;
                case ERROR -> SimpleTextAttributes.ERROR_ATTRIBUTES;
                default -> SimpleTextAttributes.GRAYED_ATTRIBUTES;
            };

            append("[" + statusText + "]", statusAttr);
        }

        private void renderToolsRootNode(MCPTreeNode node) {
            append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }

        private void renderToolNode(MCPTreeNode node) {
            append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }
}
