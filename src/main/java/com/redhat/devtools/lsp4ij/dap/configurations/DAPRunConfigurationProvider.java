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

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import org.jetbrains.annotations.NotNull;

/**
 * Default Debug Adapter Protocol (DAP) run configuration provider.
 */
public class DAPRunConfigurationProvider extends LazyRunConfigurationProducer<DAPRunConfiguration> {

    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
        return DAPRunConfigurationType.getInstance().getConfigurationFactories()[0];
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull DAPRunConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
        var location = context.getLocation();
        if (location == null) {
            return false;
        }
        var file = location.getVirtualFile();
        if (file == null) {
            return false;
        }
        var project= location.getProject();
        if (project.isDisposed()) {
            return false;
        }
        return DebugAdapterManager.getInstance().prepareConfiguration(configuration, file, project);
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull DAPRunConfiguration configuration,
                                              @NotNull ConfigurationContext context) {
        var location = context.getLocation();
        if (location == null) {
            return false;
        }
        var file = location.getVirtualFile();
        if (file == null) {
            return false;
        }
        return configuration.isDebuggableFile(file, configuration.getProject());
    }
}
