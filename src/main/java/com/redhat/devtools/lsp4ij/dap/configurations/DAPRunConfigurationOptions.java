/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This file is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.lang.Language;
import com.intellij.openapi.components.StoredProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.DebugServerWaitStrategy;
import com.redhat.devtools.lsp4ij.dap.configurations.extractors.NetworkAddressExtractor;
import com.redhat.devtools.lsp4ij.dap.configurations.options.FileOptionConfigurable;
import com.redhat.devtools.lsp4ij.dap.configurations.options.WorkingDirectoryConfigurable;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.List;

/**
 * Debug Adapter Protocol (DAP) run configuration options.
 */
public class DAPRunConfigurationOptions extends RunConfigurationOptions implements FileOptionConfigurable, WorkingDirectoryConfigurable, DebuggableFile {

    @Nullable
    private NetworkAddressExtractor networkAddressExtractor;

    // Configuration settings
    private final StoredProperty<String> file = string("")
            .provideDelegate(this, "file");

    private final StoredProperty<String> workingDirectory = string("")
            .provideDelegate(this, "workingDirectory");

    private final StoredProperty<String> debugMode = string(DebugMode.LAUNCH.name())
            .provideDelegate(this, "debugMode");

    private final StoredProperty<String> launchConfigurationId = string("")
            .provideDelegate(this, "launchConfigurationId");

    private final StoredProperty<String> launchConfiguration = string("")
            .provideDelegate(this, "launchConfiguration");

    private final StoredProperty<String> attachConfigurationId = string("")
            .provideDelegate(this, "attachConfigurationId");

    private final StoredProperty<String> attachConfiguration = string("")
            .provideDelegate(this, "attachConfiguration");

    // Mappings settings
    private final StoredProperty<List<ServerMappingSettings>> serverMappings = this.<ServerMappingSettings>list()
            .provideDelegate(this, "serverMappings");

    // Server settings
    private final StoredProperty<String> serverId = string("")
            .provideDelegate(this, "serverId");

    private final StoredProperty<String> serverName = string("")
            .provideDelegate(this, "serverName");

    private final StoredProperty<String> command = string("")
            .provideDelegate(this, "command");

    private final StoredProperty<String> debugServerWaitStrategy = string(DebugServerWaitStrategy.TIMEOUT.name())
            .provideDelegate(this, "debugServerWaitStrategy");

    private final StoredProperty<Integer> connectTimeout = property(0)
            .provideDelegate(this, "connectTimeout");

    private final StoredProperty<String> debugServerReadyPattern = string("")
            .provideDelegate(this, "debugServerReadyPattern");

    private final StoredProperty<String> attachAddress = string("")
            .provideDelegate(this, "attachAddress");

    private final StoredProperty<String> attachPort = string("")
            .provideDelegate(this, "attachPort");

    private final StoredProperty<String> serverTrace = string(ServerTrace.getDefaultValue().name())
            .provideDelegate(this, "serverTrace");

    // Configuration settings

    @Override
    @Nullable
    public String getWorkingDirectory() {
        return workingDirectory.getValue(this);
    }

    @Override
    public void setWorkingDirectory(@Nullable String workingDirectory) {
        this.workingDirectory.setValue(this, workingDirectory);
    }

    @Override
    @Nullable
    public String getFile() {
        return file.getValue(this);
    }

    @Override
    public void setFile(@Nullable String file) {
        this.file.setValue(this, file);
    }

    public DebugMode getDebugMode() {
        return DebugMode.get(debugMode.getValue(this));
    }

    public void setDebugMode(DebugMode debugMode) {
        this.debugMode.setValue(this, debugMode.name());
    }

    public String getLaunchConfigurationId() {
        return launchConfigurationId.getValue(this);
    }

    public void setLaunchConfigurationId(String launchConfigurationId) {
        this.launchConfigurationId.setValue(this, launchConfigurationId);
    }

    public String getLaunchConfiguration() {
        return launchConfiguration.getValue(this);
    }

    public void setLaunchConfiguration(String launchConfiguration) {
        this.launchConfiguration.setValue(this, launchConfiguration);
    }

    public String getAttachConfigurationId() {
        return attachConfigurationId.getValue(this);
    }

    public void setAttachConfigurationId(String attachConfigurationId) {
        this.attachConfigurationId.setValue(this, attachConfigurationId);
    }

    public String getAttachConfiguration() {
        return attachConfiguration.getValue(this);
    }

    public void setAttachConfiguration(String attachConfiguration) {
        this.attachConfiguration.setValue(this, attachConfiguration);
    }

    /**
     * Returns the DAP launch/attach parameters according the debugging type.
     *
     * @return the DAP launch/attach parameters according the debugging type.
     */
    public String getDapParameters() {
        return getDebugMode() == DebugMode.ATTACH ? getAttachConfiguration() : getLaunchConfiguration();
    }

    // Mappings settings

    public List<ServerMappingSettings> getServerMappings() {
        return serverMappings.getValue(this);
    }

    public void setServerMappings(List<ServerMappingSettings> serverMappings) {
        this.serverMappings.setValue(this, serverMappings);
    }

    // Server settings

    public String getServerId() {
        return serverId.getValue(this);
    }

    public void setServerId(String serverId) {
        this.serverId.setValue(this, serverId);
    }

    public String getServerName() {
        return serverName.getValue(this);
    }

    public void setServerName(String serverName) {
        this.serverName.setValue(this, serverName);
    }

    /**
     * Returns the command to execute to start the Debug Adapter Protocol server.
     *
     * <p>
     * ex : node path/to/js-debug/src/dapDebugServer.js ${port}
     * </p>
     *
     * @return the command to execute to start the Debug Adapter Protocol server.
     */
    public String getCommand() {
        return command.getValue(this);
    }

    /**
     * Set the command to execute to start the Debug Adapter Protocol server.
     *
     * <p>
     * ex : node path/to/js-debug/src/dapDebugServer.js ${port}
     * </p>
     *
     * @param command the command to execute to start the Debug Adapter Protocol server.
     */
    public void setCommand(String command) {
        this.command.setValue(this, command);
    }

    /**
     * Retrieves the current strategy used to wait for the debug server.
     *
     * @return the current {@link DebugServerWaitStrategy} instance.
     */
    public DebugServerWaitStrategy getDebugServerWaitStrategy() {
        return DebugServerWaitStrategy.get(debugServerWaitStrategy.getValue(this));
    }

    /**
     * Set the current strategy used to wait for the debug server.
     */
    public void setDebugServerWaitStrategy(DebugServerWaitStrategy debugServerWaitStrategy) {
        this.debugServerWaitStrategy.setValue(this, debugServerWaitStrategy.name());
    }

    /**
     * Returns the timeout to use when the DAP client must connect to the DAP server, and 0 otherwise.
     *
     * @return the timeout to use when the DAP client must connect to the DAP server, and 0 otherwise.
     */
    public int getConnectTimeout() {
        return connectTimeout.getValue(this);
    }

    /**
     * Set the timeout to use when the DAP client must connect to the DAP server, and 0 otherwise.
     *
     * @param connectTimeout the timeout.
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout.setValue(this, connectTimeout);
    }

    public String getDebugServerReadyPattern() {
        return debugServerReadyPattern.getValue(this);
    }

    public void setDebugServerReadyPattern(String debugServerReadyPattern) {
        this.debugServerReadyPattern.setValue(this, debugServerReadyPattern);
        this.networkAddressExtractor = null;
    }
    
    public String getAttachAddress() {
        return attachAddress.getValue(this);
    }
    
    public void setAttachAddress(String attachAddress) {
        this.attachAddress.setValue(this, attachAddress);
    }

    public String getAttachPort() {
        return attachPort.getValue(this);
    }

    public void setAttachPort(String attachPort) {
        this.attachPort.setValue(this, attachPort);
    }
    
    public ServerTrace getServerTrace() {
        return ServerTrace.get(serverTrace.getValue(this));
    }

    public void setServerTrace(ServerTrace serverTrace) {
        this.serverTrace.setValue(this, serverTrace.name());
    }

    public @Nullable NetworkAddressExtractor getNetworkAddressExtractor() {
        if (networkAddressExtractor != null) {
            return networkAddressExtractor;
        }
        String trackTrace = getDebugServerReadyPattern();
        if (StringUtils.isNotBlank(trackTrace)) {
            networkAddressExtractor = new NetworkAddressExtractor(trackTrace);
        }
        return networkAddressExtractor;
    }

    /**
     * Returns the server DAP factory descriptor and null otherwise.
     *
     * @return the server DAP factory descriptor and null otherwise.
     */
    public @Nullable DebugAdapterServerDefinition getDebugAdapterServer() {
        String serverId = getServerId();
        if (StringUtils.isBlank(serverId)) {
            return null;
        }
        return DebugAdapterManager.getInstance().getDebugAdapterServerById(serverId);
    }

    @Override
    public boolean isDebuggableFile(@NotNull VirtualFile file, @NotNull Project project) {
        // Match mappings?
        for (var mapping : getServerMappings()) {
            // Match file type?
            String fileType = mapping.getFileType();
            if (StringUtils.isNotBlank(fileType)) {
                if (fileType.equals(file.getFileType().getName())) {
                    return true;
                }
            }
            // Match file name patterns?
            if (mapping.getFileNamePatterns() != null) {
                for (var pattern : mapping.getFileNamePatterns()) {
                    var p = FileNameMatcherFactory.getInstance().createMatcher(pattern);
                    if (p.acceptsCharSequence(file.getName())) {
                        return true;
                    }
                }
            }
            // Match language?
            String language = mapping.getLanguage();
            if (StringUtils.isNotBlank(language)) {
                Language fileLanguage = LSPIJUtils.getFileLanguage(file, project);
                if (fileLanguage != null && language.equals(fileLanguage.getID())) {
                    return true;
                }
            }
        }
        return false;
    }
}