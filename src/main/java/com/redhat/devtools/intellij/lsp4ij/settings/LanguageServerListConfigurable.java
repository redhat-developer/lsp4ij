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
package com.redhat.devtools.intellij.lsp4ij.settings;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.intellij.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;
import java.util.Set;

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
    private final Set<LanguageServersRegistry.LanguageServerDefinition> languageServeDefinitions;

    private boolean isTreeInitialized;

    public LanguageServerListConfigurable(Project project) {
        this.project = project;
        this.languageServeDefinitions = LanguageServersRegistry.getInstance().getAllDefinitions();
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

    private MyNode addLanguageServerDefinitionNode(LanguageServersRegistry.LanguageServerDefinition languageServerDefinition) {
        MyNode node = new MyNode(new LanguageServerConfigurable(languageServerDefinition, TREE_UPDATER, project));
        addNode(node, myRoot);
        return node;
    }

    private void reloadTree() {
        myRoot.removeAllChildren();
        for (LanguageServersRegistry.LanguageServerDefinition languageServeDefinition : languageServeDefinitions) {
            addLanguageServerDefinitionNode(languageServeDefinition);
        }
    }

    @Override
    public void reset() {
        reloadTree();
        super.reset();
    }
}
