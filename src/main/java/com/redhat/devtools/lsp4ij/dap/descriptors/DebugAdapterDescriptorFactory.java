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
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointType;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPSettingsEditor;
import com.redhat.devtools.lsp4ij.dap.configurations.DebuggableFile;
import com.redhat.devtools.lsp4ij.dap.configurations.options.FileOptionConfigurable;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.installation.ServerInstaller;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getFilePath;

/**
 * Debug Adapter Protocol (DAP) server descriptor factory.
 */
@ApiStatus.Experimental
public abstract class DebugAdapterDescriptorFactory implements DebuggableFile {

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
                    """, DebugMode.LAUNCH);

    private static final LaunchConfiguration DEFAULT_ATTACH_CONFIGURATION = new LaunchConfiguration("default_attach", "Attach file",
            // language=json
            """                        
                    {
                       "type": "undefined",
                       "name": "Attach to process",
                       "request": "attach",
                       "port": 5858
                     }
                    """, DebugMode.ATTACH);

    public static final LaunchConfiguration[] DEFAULT_LAUNCH_CONFIGURATION_ARRAY = new LaunchConfiguration[]{DEFAULT_LAUNCH_CONFIGURATION};

    public static final LaunchConfiguration[] DEFAULT_ATTACH_CONFIGURATION_ARRAY = new LaunchConfiguration[]{DEFAULT_ATTACH_CONFIGURATION};

    public static final List<LaunchConfiguration> DEFAULT_LAUNCH_CONFIGURATIONS = Arrays.asList(
            DEFAULT_LAUNCH_CONFIGURATION,
            DEFAULT_ATTACH_CONFIGURATION
    );

    private DebugAdapterServerDefinition serverDefinition;

    private ServerTrace serverTrace;

    public DebugAdapterDescriptor createDebugAdapterDescriptor(@NotNull RunConfigurationOptions options,
                                                               @NotNull ExecutionEnvironment environment) {
        return new DefaultDebugAdapterDescriptor(options, environment, getServerDefinition());
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

    @Override
    public boolean isDebuggableFile(@NotNull VirtualFile file,
                                    @NotNull Project project) {
        return getServerDefinition().isDebuggableFile(file, project);
    }

    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor(@NotNull Project project) {
        return new DAPSettingsEditor(project);
    }

    public boolean canRun(@NotNull String executorId) {
        return true;
    }

    public boolean prepareConfiguration(@NotNull RunConfiguration configuration,
                                        @NotNull VirtualFile file,
                                        @NotNull Project project) {
        if (configuration instanceof FileOptionConfigurable fileOptionAware) {
            // Configuration
            fileOptionAware.setFile(getFilePath(file));
        }
        if (configuration instanceof DAPRunConfiguration dapConfiguration) {
            configuration.setName(file.getName());

            // Configuration
            dapConfiguration.setDebugMode(DebugMode.LAUNCH);

            // Server
            dapConfiguration.setServerId(serverDefinition.getId());
            dapConfiguration.setServerName(serverDefinition.getName());
            return true;
        }
        return false;
    }

    @NotNull
    public List<LaunchConfiguration> getLaunchConfigurations() {
        return DEFAULT_LAUNCH_CONFIGURATIONS;
    }

    @NotNull
    public DebugAdapterServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    @ApiStatus.Internal
    public final void setServerDefinition(DebugAdapterServerDefinition serverDefinition) {
        this.serverDefinition = serverDefinition;
    }

    public static Path getDebugAdapterServerPath(@NotNull String pluginId,
                                                 @NotNull String serverPath) {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId(pluginId));
        assert descriptor != null;
        Path pluginPath = descriptor.getPluginPath();
        assert pluginPath != null;
        pluginPath = pluginPath.toAbsolutePath();
        return pluginPath.resolve(serverPath);
    }

    /**
     * Creates a server installer intended to install a server shared by all projects (global scope).
     *
     * <p>
     * The server may be installed in the user's home directory, making it accessible to all projects.
     * </p>
     *
     * <p>
     * To install a server for a specific project only (project scope), use {@link LSPClientFeatures#setServerInstaller(ServerInstaller)} instead.
     * </p>
     *
     * @return a {@link ServerInstaller} for global use, or {@code null} if no global installer is provided.
     */
    @Nullable
    public ServerInstaller createServerInstaller() {
        return null;
    }

    /**
     * Determines whether the given breakpoint type is supported by this debugger.
     * <p>
     * The default implementation returns {@code true} if the given {@code breakpointType}
     * is exactly of type {@link DAPBreakpointType} (i.e., {@code breakpointType.getClass() == DAPBreakpointType.class}).
     * </p>
     *
     * @param breakpointType the breakpoint type to check
     * @return {@code true} if the breakpoint type is supported, {@code false} otherwise
     */
    public boolean supportsBreakpointType(@NotNull XBreakpointType breakpointType) {
        return breakpointType.getClass() == DAPBreakpointType.class;
    }
}
