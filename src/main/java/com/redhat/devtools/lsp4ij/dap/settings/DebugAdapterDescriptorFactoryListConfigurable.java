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
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactoryListener;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.settings.ui.NewDebugAdapterDescriptorFactoryDialog;
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
 *  Show list of Debug Adapter descriptor factories as master.
 */
public class DebugAdapterDescriptorFactoryListConfigurable extends MasterDetailsComponent implements SearchableConfigurable {

    @NonNls
    private static final String ID = "debugAdapterDescriptorFactories";
    private String displayNodeName = null;
    private final Project project;

    private final DebugAdapterDescriptorFactoryListener listener = new DebugAdapterDescriptorFactoryListener() {

        @Override
        public void handleAdded(@NotNull DebugAdapterDescriptorFactoryAddedEvent event) {
            reloadTree();
        }

        @Override
        public void handleRemoved(@NotNull DebugAdapterDescriptorFactoryRemovedEvent event) {
            reloadTree();
        }

        @Override
        public void handleChanged(@NotNull DebugAdapterDescriptorFactoryChangedEvent event) {
            // Do nothing
        }

    };

    private boolean isTreeInitialized;

    public DebugAdapterDescriptorFactoryListConfigurable(@NotNull Project project) {
        this.project = project;
        DebugAdapterManager.getInstance().addDebugAdapterDescriptorFactoryListener(listener);
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
                var dialog = new NewDebugAdapterDescriptorFactoryDialog(project);
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
                        var descriptorFactory = ((DebugAdapterDescriptorFactoryConfigurable) selectedNode.getConfigurable()).getEditableObject();
                        DebugAdapterManager.getInstance().removeDebugAdapterDescriptorFactory(descriptorFactory);
                    }
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                MyNode[] selectedNodes = myTree.getSelectedNodes(MyNode.class, null);
                boolean enabled = selectedNodes.length > 0 && Stream.of(selectedNodes)
                        .anyMatch(DebugAdapterDescriptorFactoryListConfigurable::isUserDefined);
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
        var descriptorFactory = ((DebugAdapterDescriptorFactoryConfigurable) node.getConfigurable()).getEditableObject();
        return descriptorFactory instanceof UserDefinedDebugAdapterDescriptorFactory;
    }

    private void addDebugAdapterProtocolDefinitionNode(DebugAdapterDescriptorFactory descriptorFactory) {
        MyNode node = new MyNode(new DebugAdapterDescriptorFactoryConfigurable(descriptorFactory, TREE_UPDATER, project));
        addNode(node, myRoot);
    }

    private void reloadTree() {
        myRoot.removeAllChildren();
        for (var descriptorFactory : DebugAdapterManager.getInstance().getFactories()) {
            addDebugAdapterProtocolDefinitionNode(descriptorFactory);
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
        DebugAdapterManager.getInstance().removeDebugAdapterDescriptorFactoryListener(listener);
    }

}
