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

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.DebugServerWaitStrategy;
import com.redhat.devtools.lsp4ij.dap.configurations.options.FileOptionConfigurable;
import com.redhat.devtools.lsp4ij.dap.configurations.options.WorkingDirectoryConfigurable;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.DefaultDebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Debug Adapter Protocol (DAP) run configuration.
 */
public class DAPRunConfiguration extends RunConfigurationBase<DAPRunConfigurationOptions> implements FileOptionConfigurable, WorkingDirectoryConfigurable, DebuggableFile {

    public static final String DEBUG_ADAPTER_CONFIGURATION = "Debug Adapter Configuration";

    protected DAPRunConfiguration(@NotNull Project project,
                                  @NotNull ConfigurationFactory factory,
                                  @NotNull String name) {
        super(project, factory, name);
    }

    @NotNull
    @Override
    protected DAPRunConfigurationOptions getOptions() {
        return (DAPRunConfigurationOptions) super.getOptions();
    }

    // Server settings

    public String getServerId() {
        return getOptions().getServerId();
    }

    public void setServerId(String serverId) {
        getOptions().setServerId(serverId);
    }

    public String getServerName() {
        return getOptions().getServerName();
    }

    public void setServerName(String serverName) {
        getOptions().setServerName(serverName);
    }

    public String getCommand() {
        return getOptions().getCommand();
    }

    public void setCommand(String command) {
        getOptions().setCommand(command);
    }

    /**
     * Retrieves the current strategy used to wait for the debug server.
     *
     * @return the current {@link DebugServerWaitStrategy} instance.
     */
    public DebugServerWaitStrategy getDebugServerWaitStrategy() {
        return getOptions().getDebugServerWaitStrategy();
    }

    /**
     * Set the current strategy used to wait for the debug server.
     */
    public void setDebugServerWaitStrategy(DebugServerWaitStrategy debugServerWaitStrategy) {
        getOptions().setDebugServerWaitStrategy(debugServerWaitStrategy);
    }

    /**
     * Returns the timeout to use when the DAP client must connect to the DAP server, and 0 otherwise.
     *
     * @return the timeout to use when the DAP client must connect to the DAP server, and 0 otherwise.
     */
    public int getConnectTimeout() {
        return getOptions().getConnectTimeout();
    }

    /**
     * Set the timeout to use when the DAP client must connect to the DAP server, and 0 otherwise.
     *
     * @param connectTimeout the timeout.
     */
    public void setConnectTimeout(int connectTimeout) {
        getOptions().setConnectTimeout(connectTimeout);
    }

    /**
     *
     * @return
     */
    public String getDebugServerReadyPattern() {
        return getOptions().getDebugServerReadyPattern();
    }

    public void setDebugServerReadyPattern(String debugServerReadyPattern) {
        getOptions().setDebugServerReadyPattern(debugServerReadyPattern);
    }

    public String getAttachAddress() {
        return getOptions().getAttachAddress();
    }

    public void setAttachAddress(String attachAddress) {
        getOptions().setAttachAddress(attachAddress);
    }

    public String getAttachPort() {
        return getOptions().getAttachPort();
    }

    public void setAttachPort(String attachPort) {
        getOptions().setAttachPort(attachPort);
    }
    
    public ServerTrace getServerTrace() {
        return getOptions().getServerTrace();
    }

    public void setServerTrace(ServerTrace serverTrace) {
        getOptions().setServerTrace(serverTrace);
    }

    // Mappings settings

    @NotNull
    public List<ServerMappingSettings> getServerMappings() {
        return getOptions().getServerMappings();
    }

    public void setServerMappings(@NotNull List<ServerMappingSettings> serverMappings) {
        getOptions().setServerMappings(serverMappings);
    }

    // Configuration settings

    @Override
    public @Nullable String getWorkingDirectory() {
        return getOptions().getWorkingDirectory();
    }

    @Override
    public void setWorkingDirectory(@Nullable String workingDirectory) {
        getOptions().setWorkingDirectory(workingDirectory);
    }

    @Override
    public @Nullable String getFile() {
        return getOptions().getFile();
    }

    @Override
    public void setFile(@Nullable String file) {
        getOptions().setFile(file);
    }
    
    public DebugMode getDebugMode() {
        return getOptions().getDebugMode();
    }

    public void setDebugMode(DebugMode debugMode) {
        getOptions().setDebugMode(debugMode);
    }

    public String getLaunchConfigurationId() {
        return getOptions().getLaunchConfigurationId();
    }

    public void setLaunchConfigurationId(String launchConfigurationId) {
        getOptions().setLaunchConfigurationId(launchConfigurationId);
    }

    public String getLaunchConfiguration() {
        return getOptions().getLaunchConfiguration();
    }

    public void setLaunchConfiguration(String launchConfiguration) {
        getOptions().setLaunchConfiguration(launchConfiguration);
    }

    public String getAttachConfigurationId() {
        return getOptions().getAttachConfigurationId();
    }

    public void setAttachConfigurationId(String attachConfigurationId) {
        getOptions().setAttachConfigurationId(attachConfigurationId);
    }

    public String getAttachConfiguration() {
        return getOptions().getAttachConfiguration();
    }

    public void setAttachConfiguration(String attachConfiguration) {
        getOptions().setAttachConfiguration(attachConfiguration);
    }

    public String getInstallerConfiguration() {
        return getOptions().getInstallerConfiguration();
    }

    public void setInstallerConfiguration(String installerConfiguration) {
        getOptions().setInstallerConfiguration(installerConfiguration);
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        var serverFactory = getServerFactory();
        return serverFactory != null ?
                serverFactory.getConfigurationEditor(getProject()) :
                new DAPSettingsEditor(getProject());
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor,
                                    @NotNull ExecutionEnvironment environment) {
        var debugAdapterServer = getDebugAdapterServer();
        DebugAdapterDescriptor serverDescriptor = debugAdapterServer != null ?
                debugAdapterServer.getFactory().createDebugAdapterDescriptor(getOptions(), environment) :
                new DefaultDebugAdapterDescriptor(getOptions(), environment, getName());
        return new DAPCommandLineState(serverDescriptor, getOptions(), environment);
    }

    @Override
    public boolean isDebuggableFile(@NotNull VirtualFile file, @NotNull Project project) {
        return getOptions().isDebuggableFile(file, project);
    }

    /**
     * Returns true if the given executor id (ex: 'Debug') can be executed and false otherwise.
     *
     * @param executorId the executor id (ex: 'Debug').
     * @return true if the given executor id (ex: 'Debug') can be executed and false otherwise.
     */
    public boolean canRun(@NotNull String executorId) {
        var serverFactory = getServerFactory();
        return serverFactory != null ? serverFactory.canRun(executorId) : true;
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (StringUtils.isBlank(getCommand()) && StringUtils.isBlank(getServerId())) {
            throw new RuntimeConfigurationException("Server command is required", DEBUG_ADAPTER_CONFIGURATION);
        }
    }

    /**
     * Returns the server DAP factory descriptor and null otherwise.
     *
     * @return the server DAP factory descriptor and null otherwise.
     */
    @Nullable
    private DebugAdapterDescriptorFactory getServerFactory() {
        var server = getDebugAdapterServer();
        return server != null ? server.getFactory() : null;
    }

    @Nullable
    private DebugAdapterServerDefinition getDebugAdapterServer() {
        return getOptions().getDebugAdapterServer();
    }

    /**
     * Copy the configuration into the given configuration.
     *
     * @param configuration the configuration where values must be copied.
     */
    public void copyTo(@NotNull DAPRunConfiguration configuration) {
        // Configuration
        configuration.setWorkingDirectory(getWorkingDirectory());
        configuration.setFile(getFile());
        configuration.setDebugMode(getDebugMode());
        configuration.setLaunchConfiguration(getLaunchConfiguration());
        configuration.setAttachConfiguration(getAttachConfiguration());

        // Mappings
        configuration.setServerMappings(getServerMappings());

        // Server
        configuration.setServerId(getServerId());
        configuration.setServerName(getServerName());
        configuration.setCommand(getCommand());
        configuration.setDebugServerWaitStrategy(getDebugServerWaitStrategy());
        configuration.setConnectTimeout(getConnectTimeout());
        configuration.setDebugServerReadyPattern(getDebugServerReadyPattern());
        configuration.setAttachAddress(getAttachAddress());
        configuration.setAttachPort(getAttachPort());
        configuration.setServerTrace(getServerTrace());
    }

}