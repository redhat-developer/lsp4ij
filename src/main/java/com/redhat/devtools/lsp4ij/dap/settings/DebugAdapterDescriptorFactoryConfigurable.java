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
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.settings.ui.DebugAdapterDescriptorFactoryView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * UI settings to configure a given debug adapter descriptor factory
 */
public class DebugAdapterDescriptorFactoryConfigurable extends NamedConfigurable<DebugAdapterDescriptorFactory> implements DebugAdapterDescriptorFactoryView.DebugAdapterDescriptorFactoryNameProvider {

    private final DebugAdapterDescriptorFactory descriptorFactory;
    private final @NotNull Project project;

    private DebugAdapterDescriptorFactoryView myView;

    public DebugAdapterDescriptorFactoryConfigurable(@NotNull DebugAdapterDescriptorFactory descriptorFactory,
                                                     @NotNull Runnable updater,
                                                     @NotNull Project project) {
        super(descriptorFactory instanceof UserDefinedDebugAdapterDescriptorFactory, updater);
        this.project = project;
        this.descriptorFactory = descriptorFactory;
    }

    @Override
    public void setDisplayName(String name) {
        // Do nothing: the descriptor name is not editable.
        if (descriptorFactory instanceof UserDefinedDebugAdapterDescriptorFactory userDefinedFactory) {
            userDefinedFactory.setName(name);
        }
    }

    @Override
    public DebugAdapterDescriptorFactory getEditableObject() {
        return descriptorFactory;
    }

    @Override
    public @NlsContexts.DetailedDescription String getBannerSlogan() {
        return descriptorFactory.getDisplayName();
    }

    @Override
    public JComponent createOptionsPanel() {
        if (myView == null) {
            myView = new DebugAdapterDescriptorFactoryView(descriptorFactory, this, project);
        }
        return myView.getComponent();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return descriptorFactory.getDisplayName();
    }

    @Override
    public @Nullable Icon getIcon(boolean expanded) {
        return descriptorFactory.getIcon();
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
