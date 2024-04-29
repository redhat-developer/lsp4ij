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
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.IconUtil;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.ui.NewLanguageServerDialog;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * UI settings which show:
 *
 * <ul>
 *     <li>list of language server as master on the left</li>
 *     <li>the settings detail of the selected language server on the right</li>
 * </ul>
 */
public class LanguageServerListConfigurable extends MasterDetailsComponent implements SearchableConfigurable {

    @NonNls
    private static final String ID = "LanguageServers";

    private final Project project;
    private final LanguageServerDefinitionListener listener = new LanguageServerDefinitionListener() {

        @Override
        public void handleAdded(@NotNull LanguageServerDefinitionListener.LanguageServerAddedEvent event) {
            reloadTree();
        }

        @Override
        public void handleRemoved(@NotNull LanguageServerDefinitionListener.LanguageServerRemovedEvent event) {
            reloadTree();
        }

        @Override
        public void handleChanged(@NotNull LanguageServerChangedEvent event) {
            // Do nothing
        }
    };

    private boolean isTreeInitialized;

    public LanguageServerListConfigurable(Project project) {
        this.project = project;
        LanguageServersRegistry.getInstance().addLanguageServerDefinitionListener(listener);
    }

    @Override
    @NotNull
    public JComponent createComponent() {
        if (!isTreeInitialized) {
            initTree();
            isTreeInitialized = true;
        }
        return super.createComponent();
    }

    @Override
    public @NotNull @NonNls String getId() {
        return ID;
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return LanguageServerBundle.message("language.servers");
    }

    @Nullable
    @Override
    public Runnable enableSearch(final String option) {
        return () -> Objects.requireNonNull(SpeedSearchSupply.getSupply(myTree, true)).findAndSelectElement(option);
    }

    @Override
    protected void initTree() {
        super.initTree();
        TreeUIHelper.getInstance()
                .installTreeSpeedSearch(myTree, treePath -> ((MyNode) treePath.getLastPathComponent()).getDisplayName(), true);
    }

    @Override
    protected @Nullable List<AnAction> createActions(boolean fromPopup) {
        var addAction = new DumbAwareAction(LanguageServerBundle.message("language.server.action.add"), null, IconUtil.getAddIcon()) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                NewLanguageServerDialog dialog = new NewLanguageServerDialog(project);
                dialog.show();
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        };
        var removeAction = new DumbAwareAction(LanguageServerBundle.message("language.server.action.remove"), null, IconUtil.getRemoveIcon()) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                MyNode[] selectedNodes = myTree.getSelectedNodes(MyNode.class, null);
                for (var selectedNode : selectedNodes) {
                    if (isUserDefinedLanguageServerDefinition(selectedNode)) {
                        var serverDefinition = ((LanguageServerConfigurable) selectedNode.getConfigurable()).getEditableObject();
                        LanguageServersRegistry.getInstance().removeServerDefinition(serverDefinition);
                    }
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                MyNode[] selectedNodes = myTree.getSelectedNodes(MyNode.class, null);
                boolean enabled = selectedNodes.length > 0 && Stream.of(selectedNodes)
                        .anyMatch(LanguageServerListConfigurable::isUserDefinedLanguageServerDefinition);
                e.getPresentation().setEnabled(enabled);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        };

        return Arrays.asList(addAction, removeAction);
    }

    private static boolean isUserDefinedLanguageServerDefinition(MyNode node) {
        var serverDefinition = ((LanguageServerConfigurable) node.getConfigurable()).getEditableObject();
        return serverDefinition instanceof UserDefinedLanguageServerDefinition;
    }

    private void addLanguageServerDefinitionNode(LanguageServerDefinition languageServerDefinition) {
        MyNode node = new MyNode(new LanguageServerConfigurable(languageServerDefinition, TREE_UPDATER, project));
        addNode(node, myRoot);
    }

    private void reloadTree() {
        UserDefinedLanguageServerSettings settings = UserDefinedLanguageServerSettings.getInstance(project);
        final String nodeName = settings.getOpenNode();
        boolean nodeIsPresent = false;
        myRoot.removeAllChildren();
        for (LanguageServerDefinition languageServeDefinition : LanguageServersRegistry.getInstance().getServerDefinitions()) {
            if (nodeName != null && languageServeDefinition.getDisplayName().equals(nodeName)) {
                // Set current node if we need to open a specific ls definition
                nodeIsPresent = true;
            }
            addLanguageServerDefinitionNode(languageServeDefinition);
        }
        ((DefaultTreeModel) myTree.getModel()).reload();

        // Reset open node and select the correct node
        settings.setOpenNode(null);
        if (nodeIsPresent) {
            ApplicationManager.getApplication().invokeLater(() -> {
                selectNodeInTree(nodeName);
                myTree.updateUI();
                myTree.repaint();
            });
        }
    }

    @Override
    public void reset() {
        reloadTree();
        super.reset();
    }

    @Override
    public void disposeUIResources() {
        super.disposeUIResources();
        LanguageServersRegistry.getInstance().removeLanguageServerDefinitionListener(listener);
    }

}
