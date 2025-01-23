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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getFilePath;

/**
 * Debug Adapter Protocol (DAP) manager.
 */
public class DebugAdapterManager {

    public static DebugAdapterManager getInstance() {
        return ApplicationManager.getApplication().getService(DebugAdapterManager.class);
    }

    private final Map<String, DebugAdapterDescriptorFactory> factories = new HashMap<>();

    public DebugAdapterManager() {
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
        if (findFactoryFor(file, project) != null) {
            return true;
        }

        // Search canDebug in existing DAP run configuration
        return findExistingConfigurationFor(file, project, false) != null;
    }

    /**
     * Returns the existing run configuration for the given file and null otherwise.
     * @param file the file.
     * @param project the project.
     * @return the existing run configuration  for the given file and null otherwise.
     */
    private static RunConfiguration findExistingConfigurationFor(@NotNull VirtualFile file,
                                                                 @NotNull Project project,
                                                                 boolean checkFile) {
        List<RunConfiguration> all = RunManager.getInstance(project).getAllConfigurationsList();
        for (var runConfiguration : all) {
            if (runConfiguration instanceof DAPRunConfiguration dapConfig) {
                if (dapConfig.canDebug(file)) {
                    if (checkFile) {
                        String existingFile = ((DAPRunConfiguration) runConfiguration).getFile();
                        if (!getFilePath(file).equals(existingFile)) {
                            return null;
                        }
                    }
                    return runConfiguration;
                }
            }
        }
        return null;
    }

    /**
     * Returns the DAP descriptor factory for the given file and null otherwise.
     * @param file the file.
     * @param project the project.
     * @return the DAP descriptor factory for the given file and null otherwise.
     */
    @Nullable
    private DebugAdapterDescriptorFactory findFactoryFor(@NotNull VirtualFile file,
                                                         @NotNull Project project) {
        for(var factory : factories.values()) {
            if(factory.canDebug(file, project)) {
                return factory;
            }
        }
        return null;
    }

    public boolean prepareConfiguration(@NotNull RunConfiguration configuration,
                                        @NotNull VirtualFile file,
                                        @NotNull Project project) {
        RunConfiguration existingConfiguration = findExistingConfigurationFor(file, project, true);
        if(existingConfiguration != null
                && existingConfiguration instanceof DAPRunConfiguration existingDapConfiguration
                && configuration instanceof DAPRunConfiguration dapConfiguration) {
            existingDapConfiguration.copyTo(dapConfiguration);
            return true;
        }
        DebugAdapterDescriptorFactory factory = findFactoryFor(file, project);
        if (factory != null) {
            return factory.prepareConfiguration(configuration, file, project);
        }
        return false;
    }
}
