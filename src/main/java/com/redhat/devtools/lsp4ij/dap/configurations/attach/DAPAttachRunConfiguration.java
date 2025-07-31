/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations.attach;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationBase;
import com.redhat.devtools.lsp4ij.dap.configurations.options.AttachConfigurable;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * DAP run configuration for "attach" mode.
 */
public class DAPAttachRunConfiguration<T extends DAPAttachRunConfigurationOptions> extends DAPRunConfigurationBase<T> implements AttachConfigurable {

    private final @NotNull String serverId;

    protected DAPAttachRunConfiguration(@NotNull Project project,
                                        @Nullable ConfigurationFactory factory,
                                        @Nullable String name,
                                        @NotNull String serverId) {
        super(project, factory, name);
        this.serverId = serverId;
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new DAPAttachSettingsEditor<>(getProject());
    }

    @Override
    protected @Nullable DebugAdapterServerDefinition getDebugAdapterServer() {
        return DebugAdapterManager.getInstance().getDebugAdapterServerById(serverId);
    }

    @Override
    public @Nullable String getAttachAddress() {
        return getOptions().getAttachAddress();
    }

    @Override
    public void setAttachAddress(@Nullable String attachAddress) {
        getOptions().setAttachAddress(attachAddress);
    }

    @Override
    public @Nullable String getAttachPort() {
        return getOptions().getAttachPort();
    }

    @Override
    public void setAttachPort(@Nullable String attachPort) {
        getOptions().setAttachPort(attachPort);
    }

    @Override
    protected @NotNull T getOptions() {
        return (T) super.getOptions();
    }
}
