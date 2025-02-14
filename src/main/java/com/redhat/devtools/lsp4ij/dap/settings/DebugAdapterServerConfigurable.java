/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.settings.ui.DebugAdapterServerView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * UI settings to configure a given debug adapter server
 */
public class DebugAdapterServerConfigurable extends NamedConfigurable<DebugAdapterServerDefinition> implements DebugAdapterServerView.DebugAdapterServerNameProvider {

    private final DebugAdapterServerDefinition serverDefinition;
    private final @NotNull Project project;

    private DebugAdapterServerView myView;

    public DebugAdapterServerConfigurable(@NotNull DebugAdapterServerDefinition serverDefinition,
                                          @NotNull Runnable updater,
                                          @NotNull Project project) {
        super(serverDefinition instanceof UserDefinedDebugAdapterServerDefinition, updater);
        this.project = project;
        this.serverDefinition = serverDefinition;
    }

    @Override
    public void setDisplayName(String name) {
        // Do nothing: the descriptor name is not editable.
        if (serverDefinition instanceof UserDefinedDebugAdapterServerDefinition userDefinedServer) {
            userDefinedServer.setName(name);
        }
    }

    @Override
    public DebugAdapterServerDefinition getEditableObject() {
        return serverDefinition;
    }

    @Override
    public @NlsContexts.DetailedDescription String getBannerSlogan() {
        return serverDefinition.getDisplayName();
    }

    @Override
    public JComponent createOptionsPanel() {
        if (myView == null) {
            myView = new DebugAdapterServerView(serverDefinition, this, project);
        }
        return myView.getComponent();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return serverDefinition.getDisplayName();
    }

    @Override
    public @Nullable Icon getIcon(boolean expanded) {
        return serverDefinition.getIcon();
    }

    @Override
    public boolean isModified() {
        return myView.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        myView.apply();
    }

    @Override
    public void reset() {
        myView.reset();
    }

    @Override
    public void disposeUIResources() {
        if (myView != null) Disposer.dispose(myView);
    }
}
