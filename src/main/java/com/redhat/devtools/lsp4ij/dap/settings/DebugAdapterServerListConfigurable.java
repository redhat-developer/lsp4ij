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
package com.redhat.devtools.lsp4ij.dap.settings;

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
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterServerListener;
import com.redhat.devtools.lsp4ij.dap.settings.ui.NewDebugAdapterServerDialog;
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
 *  Show list of Debug Adapter Servers as master.
 */
public class DebugAdapterServerListConfigurable extends MasterDetailsComponent implements SearchableConfigurable {

    @NonNls
    private static final String ID = "debugAdapterDescriptorFactories";
    private String displayNodeName = null;
    private final Project project;

    private final DebugAdapterServerListener listener = new DebugAdapterServerListener() {

        @Override
        public void handleAdded(@NotNull DebugAdapterServerListener.AddedEvent event) {
            reloadTree();
        }

        @Override
        public void handleRemoved(@NotNull DebugAdapterServerListener.RemovedEvent event) {
            reloadTree();
        }

        @Override
        public void handleChanged(@NotNull DebugAdapterServerListener.ChangedEvent event) {
            // Do nothing
        }

    };

    private boolean isTreeInitialized;

    public DebugAdapterServerListConfigurable(@NotNull Project project) {
        this.project = project;
        DebugAdapterManager.getInstance().addDebugAdapterServerListener(listener);
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
        return DAPBundle.message("debug.adapter");
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
        var addAction = new DumbAwareAction(DAPBundle.message("debug.adapter.action.add"), null, IconUtil.getAddIcon()) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                var dialog = new NewDebugAdapterServerDialog(project);
                dialog.show();
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        };
        var removeAction = new DumbAwareAction(DAPBundle.message("debug.adapter.action.remove"), null, IconUtil.getRemoveIcon()) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                MyNode[] selectedNodes = myTree.getSelectedNodes(MyNode.class, null);
                for (var selectedNode : selectedNodes) {
                    if (isUserDefined(selectedNode)) {
                        var serverDefinition = ((DebugAdapterServerConfigurable) selectedNode.getConfigurable()).getEditableObject();
                        DebugAdapterManager.getInstance().removeDebugAdapterServer(serverDefinition);
                    }
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                MyNode[] selectedNodes = myTree.getSelectedNodes(MyNode.class, null);
                boolean enabled = selectedNodes.length > 0 && Stream.of(selectedNodes)
                        .anyMatch(DebugAdapterServerListConfigurable::isUserDefined);
                e.getPresentation().setEnabled(enabled);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }
        };

        return Arrays.asList(addAction, removeAction);
    }

    private static boolean isUserDefined(MyNode node) {
        var serverDefinition = ((DebugAdapterServerConfigurable) node.getConfigurable()).getEditableObject();
        return serverDefinition instanceof UserDefinedDebugAdapterServerDefinition;
    }

    private void addDebugAdapterProtocolDefinitionNode(DebugAdapterServerDefinition serverDefinition) {
        MyNode node = new MyNode(new DebugAdapterServerConfigurable(serverDefinition, TREE_UPDATER, project));
        addNode(node, myRoot);
    }

    private void reloadTree() {
        myRoot.removeAllChildren();
        for (var serverDefinition : DebugAdapterManager.getInstance().getDebugAdapterServers()) {
            addDebugAdapterProtocolDefinitionNode(serverDefinition);
        }
        ((DefaultTreeModel) myTree.getModel()).reload();

        // Select the correct node if name is set, reset name
        if (displayNodeName != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                selectNodeInTree(displayNodeName);
                displayNodeName = null;
            });
        }
    }

    /**
     * Set the node which should be displayed when opening the setting
     * @param displayNodeName display name of the language server definition
     */
    public void setDisplayNodeName(String displayNodeName) {
        this.displayNodeName = displayNodeName;
    }

    @Override
    public void reset() {
        reloadTree();
        super.reset();
    }

    @Override
    public void disposeUIResources() {
        super.disposeUIResources();
        DebugAdapterManager.getInstance().removeDebugAdapterServerListener(listener);
    }

}
