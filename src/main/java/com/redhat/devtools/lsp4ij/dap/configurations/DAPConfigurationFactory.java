/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter Protocol (DAP) configuration factory.
 */
public class DAPConfigurationFactory extends ConfigurationFactory {

    public DAPConfigurationFactory(@NotNull ConfigurationType type) {
        super(type);
    }

    @Override
    public @NotNull String getId() {
        return getType().getId();
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(
            @NotNull Project project) {
        // Without a working directory: the Debug Adapter then runs in the directory of the configuration's file, or
        // in the project's directory without a file, which the empty field shows
        return new DAPRunConfiguration(project, this, "DAP");
    }

    @Nullable
    @Override
    public Class<? extends BaseState> getOptionsClass() {
        return DAPRunConfigurationOptions.class;
    }

    @Override
    public boolean isEditableInDumbMode() {
        return true;
    }
}