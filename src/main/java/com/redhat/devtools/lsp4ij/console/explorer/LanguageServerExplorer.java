/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.console.LSPConsoleToolWindowPanel;
import com.redhat.devtools.lsp4ij.console.explorer.actions.*;
import com.redhat.devtools.lsp4ij.internal.IntelliJPlatformUtils;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Language server explorer which shows language servers and their process.
 *
 * @author Angelo ZERR
 */
public class LanguageServerExplorer extends SimpleToolWindowPanel implements Disposable {

    private final LSPConsoleToolWindowPanel panel;

    private final Tree tree;
    private final LanguageServerExplorerLifecycleListener listener;
    private final LanguageServerDefinitionListener definitionListener = new LanguageServerDefinitionListener() {

        @Override
        public void handleAdded(@NotNull LanguageServerDefinitionListener.LanguageServerAddedEvent event) {
            // Some server definitions has been added, add them from the explorer
            DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
            for (var serverDefinition : event.serverDefinitions) {
                root.add(new LanguageServerTreeNode(serverDefinition));
            }
            treeModel.reload(root);
            // Select the new language server node
            selectAndExpand((DefaultMutableTreeNode) root.getChildAt(root.getChildCount() - 1));
        }

        @Override
        public void handleRemoved(@NotNull LanguageServerDefinitionListener.LanguageServerRemovedEvent event) {
            // Some server definitions has been removed, remove them from the explorer
            DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
            for (var serverDefinition : event.serverDefinitions) {
                LanguageServerTreeNode node = findNodeForServer(serverDefinition);
                if (node != null) {
                    // Remove the language server definition from the tree
                    root.remove(node);
                    // Remove the detail, console panel (on the right) of the language server
                    removeConsolePanel(node);

                }
            }
            treeModel.reload(root);
            if (root.getChildCount() > 0) {
                // Select first language server node
                selectAndExpand((DefaultMutableTreeNode) root.getChildAt(0));
            }
        }

        @Override
        public void handleChanged(@NotNull LanguageServerChangedEvent event) {
            if (event.nameChanged) {
                // A server definition name has changed, rename the proper tree node label of the explorer
                DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
                LanguageServerTreeNode node = findNodeForServer(event.serverDefinition);
                if (node != null) {
                    treeModel.nodeChanged(node);
                }
            }
        }
    };


    public @Nullable LanguageServerTreeNode findNodeForServer(@NotNull LanguageServerDefinition serverDefinition) {
        DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<TreeNode> children = root.children();
        while (children.hasMoreElements()) {
            TreeNode child = children.nextElement();
            if (child instanceof LanguageServerTreeNode serverTreeNode) {
                if (serverDefinition.equals(serverTreeNode.getServerDefinition())) {
                    return serverTreeNode;
                }
            }
        }
        return null;
    }

    private boolean disposed;

    private final TreeSelectionListener treeSelectionListener = event -> {
        if (isDisposed()) {
            return;
        }
        TreePath selectionPath = event.getPath();
        Object selectedItem = selectionPath != null ? selectionPath.getLastPathComponent() : null;
        if (selectedItem instanceof LanguageServerTreeNode node) {
            onLanguageServerSelected(node);
        } else if (selectedItem instanceof LanguageServerProcessTreeNode node) {
            onLanguageServerProcessSelected(node);
        }
    };

    public LanguageServerExplorer(LSPConsoleToolWindowPanel panel) {
        super(true, false);
        this.panel = panel;
        tree = buildTree();
        this.setContent(tree);
        listener = new LanguageServerExplorerLifecycleListener(this);
        LanguageServerLifecycleManager.getInstance(panel.getProject())
                .addLanguageServerLifecycleListener(listener);
        LanguageServersRegistry.getInstance().addLanguageServerDefinitionListener(definitionListener);
    }

    private void onLanguageServerSelected(LanguageServerTreeNode treeNode) {
        if (isDisposed()) {
            return;
        }
        panel.selectDetail(treeNode);
    }

    private void onLanguageServerProcessSelected(LanguageServerProcessTreeNode processTreeNode) {
        if (isDisposed()) {
            return;
        }
        panel.selectConsole(processTreeNode);
    }

    /**
     * Builds the Language server tree
     *
     * @return Tree object of all language servers
     */
    private Tree buildTree() {

        DefaultMutableTreeNode top = new DefaultMutableTreeNode("Language servers");

        Tree tree = new Tree(top);
        tree.setRootVisible(false);

        // Fill tree will all language server definitions, ordered alphabetically
        loadLanguageServerDefinitions(top);

        tree.setCellRenderer(new LanguageServerTreeRenderer());

        tree.addTreeSelectionListener(treeSelectionListener);

        tree.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(Component comp, int x, int y) {
                if (isDisposed()) {
                    return;
                }
                final TreePath[] paths = tree.getSelectionPaths();
                DefaultActionGroup group =  new DefaultActionGroup();
                if (paths == null || paths.length ==0) {
                    group.add(new OpenLanguageServerDialogAction(getProject()));
                } else if (paths.length == 1) {
                    handleSingleSelection(comp, x, y, group);
                } else {
                    handleMultipleSelections(comp, x, y, paths, group);
                }
                if (group.getChildrenCount() > 0) {
                    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group);
                    menu.getComponent().show(comp, x, y);
                }
            }
        });



        tree.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true);

        ((DefaultTreeModel) tree.getModel()).reload(top);
        return tree;
    }

    /**
     * Handle case when only one object is selected.
     */
    private void handleSingleSelection(Component comp, int x, int y, DefaultActionGroup group) {
        final TreePath path = tree.getSelectionPath();
        if (path != null) {
            Object node = path.getLastPathComponent();
            if (node instanceof LanguageServerTreeNode serverTreeNode) {
                // Compute popup menu actions for Language Server node
                LanguageServerDefinition languageServerDefinition = serverTreeNode.getServerDefinition();
                if (languageServerDefinition instanceof UserDefinedLanguageServerDefinition) {
                    group.add(new DeleteServerAction(languageServerDefinition));
                    group.add(new EditServerAction(languageServerDefinition));
                    group.add(new ExportServerAction(Collections.singletonList(languageServerDefinition)));
                    group.addSeparator();
                    group.add(new OpenLanguageServerDialogAction(getProject()));
                }
            } else if (node instanceof LanguageServerProcessTreeNode processTreeNode) {
                // Compute popup menu actions for Language Server process node
                switch (processTreeNode.getServerStatus()) {
                    case starting:
                    case started:
                        // Stop and disable the language server action
                        if (processTreeNode.getLanguageServer().getClientFeatures().canStopServerByUser()) {
                            AnAction stopServerAction = ActionManager.getInstance().getAction(StopServerAction.ACTION_ID);
                            group.add(stopServerAction);
                        }
                        if (IntelliJPlatformUtils.isDevMode()) {
                            // In dev mode, enable the "Pause" action
                            AnAction pauseServerAction = ActionManager.getInstance().getAction(PauseServerAction.ACTION_ID);
                            group.add(pauseServerAction);
                        }
                        break;
                    case stopping:
                    case stopped:
                        // Restart language server action
                        AnAction restartServerAction = ActionManager.getInstance().getAction(RestartServerAction.ACTION_ID);
                        group.add(restartServerAction);
                        break;
                }
                AnAction testStartServerAction = ActionManager.getInstance().getAction(CopyStartServerCommandAction.ACTION_ID);
                group.add(testStartServerAction);
            }
        }
    }

    /**
     * Handle selection when multiple objects are selected. Enables exporting multiple servers
     */
    private void handleMultipleSelections(Component comp, int x, int y, TreePath[] paths, DefaultActionGroup group) {
        List<LanguageServerDefinition> languageServerDefinitions = new ArrayList<>();
        for (TreePath path : paths) {
            Object node = path.getLastPathComponent();
            if (node instanceof LanguageServerTreeNode serverTreeNode) {
                languageServerDefinitions.add(serverTreeNode.getServerDefinition());
            }
        }
        if (languageServerDefinitions.isEmpty() || languageServerDefinitions.size() == 1) {
            // Fallback to single selection if no multiple language servers were selected
            handleSingleSelection(comp, x, y, group);
        }
        group.add(new ExportServerAction(languageServerDefinitions));

    }

    private static void loadLanguageServerDefinitions(DefaultMutableTreeNode top) {
        LanguageServersRegistry.getInstance()
                .getServerDefinitions()
                .stream()
                .sorted(Comparator.comparing(LanguageServerDefinition::getDisplayName))
                .map(LanguageServerTreeNode::new)
                .forEach(top::add);
    }

    public Tree getTree() {
        return tree;
    }

    @Override
    public void dispose() {
        this.disposed = true;
        tree.removeTreeSelectionListener(treeSelectionListener);
        LanguageServerLifecycleManager.getInstance(panel.getProject())
                .removeLanguageServerLifecycleListener(listener);
        LanguageServersRegistry.getInstance().removeLanguageServerDefinitionListener(definitionListener);
    }

    public boolean isDisposed() {
        return disposed || getProject().isDisposed() || listener.isDisposed();
    }

    public void showTrace(LanguageServerProcessTreeNode processTreeNode, String message) {
        panel.showTrace(processTreeNode, message);
    }

    public void showError(LanguageServerProcessTreeNode processTreeNode, Throwable exception) {
        panel.showError(processTreeNode, exception);
    }

    /**
     * Remove the detail, console panel of the given language server tree node.
     *
     * @param serverTreeNode the language server tree node.
     */
    public void removeConsolePanel(LanguageServerTreeNode serverTreeNode) {
        panel.removeConsolePanel(serverTreeNode);
    }

    public DefaultTreeModel getTreeModel() {
        return (DefaultTreeModel) tree.getModel();
    }

    public void selectAndExpand(DefaultMutableTreeNode treeNode) {
        var treePath = new TreePath(treeNode.getPath());
        tree.setSelectionPath(treePath);
        if (!tree.isExpanded(treePath)) {
            tree.expandPath(treePath);
        }
    }

    public Project getProject() {
        return panel.getProject();
    }

    /**
     * Initialize language server process with the started language servers.
     */
    public void load() {
        LanguageServiceAccessor.getInstance(getProject()).getStartedServers()
                .forEach(ls -> {
                    Throwable serverError = ls.getServerError();
                    listener.handleStatusChanged(ls);
                    if (serverError != null) {
                        listener.handleError(ls, serverError);
                    }
                });
    }

    /**
     * Returns true if the command of the given language server node is editing and false otherwise.
     *
     * @return true if the command of the given language server node is editing and false otherwise.
     */
    public boolean isEditingCommand(LanguageServerTreeNode serverNode) {
        return panel.isEditingCommand(serverNode);
    }
}
