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

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.DebuggingType;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPSettingsEditor;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getFilePath;

/**
 * Debug Adapter Protocol (DAP) server descriptor factory.
 */
@ApiStatus.Experimental
public abstract class DebugAdapterDescriptorFactory {

    public static final DebugAdapterDescriptorFactory NONE = new UserDefinedDebugAdapterDescriptorFactory("none", "NONE", "", Collections.emptyList(), Collections.emptyList());

    private static final LaunchConfiguration DEFAULT_LAUNCH_CONFIGURATION = new LaunchConfiguration("default_launch", "Launch file",
            // language=json
            """                        
                    {
                       "type": "undefined",
                       "name": "Launch file",
                       "request": "launch",
                       "program": "${file}",
                       "cwd": "${workspaceFolder}"
                     }
                    """, DebuggingType.LAUNCH);

    private static final LaunchConfiguration DEFAULT_ATTACH_CONFIGURATION = new LaunchConfiguration("default_attach", "Attach file",
            // language=json
            """                        
                    {
                       "type": "undefined",
                       "name": "Attach to process",
                       "request": "attach",
                       "port": 5858
                     }
                    """, DebuggingType.ATTACH);

    public static final LaunchConfiguration[] DEFAULT_LAUNCH_CONFIGURATION_ARRAY = new LaunchConfiguration[] {DEFAULT_LAUNCH_CONFIGURATION};

    public static final LaunchConfiguration[] DEFAULT_ATTACH_CONFIGURATION_ARRAY = new LaunchConfiguration[] {DEFAULT_ATTACH_CONFIGURATION};

    public static final List<LaunchConfiguration> DEFAULT_LAUNCH_CONFIGURATIONS = Arrays.asList(
            DEFAULT_LAUNCH_CONFIGURATION,
            DEFAULT_ATTACH_CONFIGURATION
    );

    private ServerTrace serverTrace;

    @NotNull
    public abstract String getId();

    @NotNull
    public abstract String getName();

    public DebugAdapterDescriptor createDebugAdapterDescriptor(@NotNull DAPRunConfigurationOptions options,
                                                               @NotNull ExecutionEnvironment environment) {
        return new DebugAdapterDescriptor(options, environment, this);
    }

    /**
     * Set the server trace.
     *
     * @param serverTrace the server trace.
     */
    public void setServerTrace(@NotNull ServerTrace serverTrace) {
        this.serverTrace = serverTrace;
    }

    /**
     * Returns the server trace.
     *
     * @return the server trace.
     */
    @NotNull
    public ServerTrace getServerTrace() {
        return serverTrace != null ? serverTrace : ServerTrace.off;
    }

    /**
     * Returns true if the given file can be debugged (to add/remove breakpoints) and false otherwise.
     *
     * @param file    the file to debug.
     * @param project the project.
     * @return true if the given file can be debugged (to add/remove breakpoints) and false otherwise.
     */
    public abstract boolean canDebug(@NotNull VirtualFile file,
                                     @NotNull Project project);

    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor(@NotNull Project project) {
        return new DAPSettingsEditor(project);
    }

    public boolean canRun(@NotNull String executorId) {
        return true;
    }

    public boolean prepareConfiguration(@NotNull RunConfiguration configuration,
                                        @NotNull VirtualFile file,
                                        @NotNull Project project) {
        if (configuration instanceof DAPRunConfiguration dapConfiguration) {
            configuration.setName(file.getName());

            // Configuration
            dapConfiguration.setFile(getFilePath(file));
            dapConfiguration.setDebuggingType(DebuggingType.LAUNCH);

            // Server
            dapConfiguration.setServerId(this.getId());
            dapConfiguration.setServerName(this.getName());
            return true;
        }
        return false;
    }

    public @NotNull String getDisplayName() {
        return getName();
    }

    @Nullable
    public Icon getIcon() {
        return null;
    }

    @Nullable
    public String getDescription() {
        return null;
    }

    @NotNull
    public List<LaunchConfiguration> getLaunchConfigurations() {
        return DEFAULT_LAUNCH_CONFIGURATIONS;
    }
}
