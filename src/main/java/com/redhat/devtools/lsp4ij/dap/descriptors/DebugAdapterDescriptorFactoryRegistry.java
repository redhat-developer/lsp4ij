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
package com.redhat.devtools.lsp4ij.dap.descriptors;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplateManager;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.TemplateDebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Debug Adapter Protocol (DAP) server descriptor registry.
 */
public class DebugAdapterDescriptorFactoryRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugAdapterDescriptorFactoryRegistry.class);

    public static DebugAdapterDescriptorFactoryRegistry getInstance() {
        return ApplicationManager.getApplication().getService(DebugAdapterDescriptorFactoryRegistry.class);
    }

    private final Map<String, DebugAdapterDescriptorFactory> factories = new HashMap<>();

    public DebugAdapterDescriptorFactoryRegistry() {
        DAPTemplateManager.getInstance()
                .getTemplates()
                .forEach(template -> addDebugAdapterDescriptorFactory(new TemplateDebugAdapterDescriptorFactory(template)));
    }

    public void addDebugAdapterDescriptorFactory(@NotNull DebugAdapterDescriptorFactory factory) {
        factories.put(factory.getId(), factory);
    }

    public void removeDebugAdapterDescriptorFactory(@NotNull DebugAdapterDescriptorFactory factory) {
        factories.remove(factory.getId());
    }

    @Nullable
    public DebugAdapterDescriptorFactory getFactoryById(@NotNull String factoryId) {
        return factories.get(factoryId);
    }

    public Collection<DebugAdapterDescriptorFactory> getFactories() {
        return Collections.unmodifiableCollection(factories.values());
    }

    public boolean canDebug(@NotNull VirtualFile file,
                            @NotNull Project project) {
        // Search canDebug inside the factories
        for(var factory : factories.values()) {
            if(factory.canDebug(file, project)) {
                return true;
            }
        }

        // Search canDebug in existing DAP run configuration
        List<RunConfiguration> all = RunManager.getInstance(project).getAllConfigurationsList();
        for (var runConfiguration : all) {
            if (runConfiguration instanceof DAPRunConfiguration dapConfig) {
                if (StringUtils.isEmpty(dapConfig.getServerId()) && dapConfig.canDebug(file)) {
                    return true;
                }
            }
        }
        return false;
    }
}
