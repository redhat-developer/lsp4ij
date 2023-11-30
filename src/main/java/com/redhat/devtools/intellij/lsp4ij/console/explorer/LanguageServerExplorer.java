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
package com.redhat.devtools.intellij.lsp4ij.console.explorer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.redhat.devtools.intellij.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.intellij.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.intellij.lsp4ij.console.LSPConsoleToolWindowPanel;
import com.redhat.devtools.intellij.lsp4ij.console.explorer.actions.CopyStartServerCommandAction;
import com.redhat.devtools.intellij.lsp4ij.console.explorer.actions.RestartServerAction;
import com.redhat.devtools.intellij.lsp4ij.console.explorer.actions.PauseServerAction;
import com.redhat.devtools.intellij.lsp4ij.console.explorer.actions.StopServerAction;
import com.redhat.devtools.intellij.lsp4ij.lifecycle.LanguageServerLifecycleManager;

import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;

/**
 * Language server explorer which shows language servers and their process.
 *
 * @author Angelo ZERR
 */
public class LanguageServerExplorer extends SimpleToolWindowPanel implements Disposable {

    private final LSPConsoleToolWindowPanel panel;

    private final Tree tree;
    private final LanguageServerExplorerLifecycleListener listener;
    private boolean disposed;

    private TreeSelectionListener treeSelectionListener = event -> {
        if (isDisposed()) {
            return;
        }
        TreePath selectionPath = event.getPath();
        Object selectedItem = selectionPath != null ? selectionPath.getLastPathComponent() : null;
        if (selectedItem instanceof LanguageServerTreeNode) {
            LanguageServerTreeNode node = (LanguageServerTreeNode) selectedItem;
            onLanguageServerSelected(node);
        } else if (selectedItem instanceof LanguageServerProcessTreeNode) {
            LanguageServerProcessTreeNode node = (LanguageServerProcessTreeNode) selectedItem;
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
        LanguageServersRegistry.getInstance().getAllDefinitions().stream()
                .sorted(Comparator.comparing(LanguageServersRegistry.LanguageServerDefinition::getDisplayName))
                .map(LanguageServerTreeNode::new)
                .forEach(top::add);

        tree.setCellRenderer(new LanguageServerTreeRenderer());

        tree.addTreeSelectionListener(treeSelectionListener);

        tree.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(Component comp, int x, int y) {
                if (isDisposed()) {
                    return;
                }
                final TreePath path = tree.getSelectionPath();
                if (path != null) {
                    DefaultActionGroup group = null;
                    Object node = path.getLastPathComponent();
                    if (node instanceof LanguageServerProcessTreeNode) {
                        LanguageServerProcessTreeNode processTreeNode = (LanguageServerProcessTreeNode) node;
                        switch (processTreeNode.getServerStatus()) {
                            case starting:
                            case started:
                                // Stop and disable the language server action
                                group = new DefaultActionGroup();
                                AnAction stopServerAction = ActionManager.getInstance().getAction(StopServerAction.ACTION_ID);
                                group.add(stopServerAction);
                                if (Boolean.getBoolean("idea.is.internal")) {
                                    // In dev mode, enable the "Pause" action
                                    AnAction pauseServerAction = ActionManager.getInstance().getAction(PauseServerAction.ACTION_ID);
                                    group.add(pauseServerAction);
                                }
                                break;
                            case stopping:
                            case stopped:
                                // Restart language server action
                                group = new DefaultActionGroup();
                                AnAction restartServerAction = ActionManager.getInstance().getAction(RestartServerAction.ACTION_ID);
                                group.add(restartServerAction);
                                break;
                        }
                        if (group == null) {
                            group = new DefaultActionGroup();
                        }
                        AnAction testStartServerAction = ActionManager.getInstance().getAction(CopyStartServerCommandAction.ACTION_ID);
                        group.add(testStartServerAction);
                    }

                    if (group != null) {
                        ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group);
                        menu.getComponent().show(comp, x, y);
                    }
                }
            }
        });
        tree.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true);

        ((DefaultTreeModel) tree.getModel()).reload(top);
        return tree;
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
    }

    public boolean isDisposed() {
        return disposed || getProject().isDisposed() || listener.isDisposed();
    }

    public void showMessage(LanguageServerProcessTreeNode processTreeNode, String message) {
        panel.showMessage(processTreeNode, message);
    }

    public void showError(LanguageServerProcessTreeNode processTreeNode, Throwable exception) {
        panel.showError(processTreeNode, exception);
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
}
