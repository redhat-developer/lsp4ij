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
import com.intellij.lang.Language;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.ConnectingServerStrategy;
import com.redhat.devtools.lsp4ij.dap.DebuggingType;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactoryRegistry;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.List;

/**
 * Debug Adapter Protocol (DAP) run configuration.
 */
public class DAPRunConfiguration extends RunConfigurationBase<DAPRunConfigurationOptions> {

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

    // Configuration settings

    public String getWorkingDirectory() {
        return getOptions().getWorkingDirectory();
    }

    public void setWorkingDirectory(String workingDirectory) {
        getOptions().setWorkingDirectory(workingDirectory);
    }

    public String getFile() {
        return getOptions().getFile();
    }

    public void setFile(String file) {
        getOptions().setFile(file);
    }

    public String getLaunchParameters() {
        return getOptions().getLaunchParameters();
    }

    public void setLaunchParameters(String launchParameters) {
        getOptions().setLaunchParameters(launchParameters);
    }

    public String getAttachParameters() {
        return getOptions().getAttachParameters();
    }

    public void setAttachParameters(String attachParameters) {
        getOptions().setAttachParameters(attachParameters);
    }

    public DebuggingType getDebuggingType() {
        return getOptions().getDebuggingType();
    }

    public void setDebuggingType(DebuggingType debuggingType) {
        getOptions().setDebuggingType(debuggingType);
    }


    // Mappings settings

    @NotNull
    public List<ServerMappingSettings> getServerMappings() {
        return getOptions().getServerMappings();
    }

    public void setServerMappings(@NotNull List<ServerMappingSettings> serverMappings) {
        getOptions().setServerMappings(serverMappings);
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

    public ConnectingServerStrategy getConnectingServerStrategy() {
        return getOptions().getConnectingServerStrategy();
    }

    public void setConnectingServerStrategy(ConnectingServerStrategy connectingServerStrategy) {
        getOptions().setConnectingServerStrategy(connectingServerStrategy);
    }

    public int getWaitForTimeout() {
        return getOptions().getWaitForTimeout();
    }

    public void setWaitForTimeout(int waitForTimeout) {
        getOptions().setWaitForTimeout(waitForTimeout);
    }

    public String getWaitForTrace() {
        return getOptions().getWaitForTrace();
    }

    public void setWaitForTrace(String waitForTrace) {
        getOptions().setWaitForTrace(waitForTrace);
    }

    public ServerTrace getServerTrace() {
        return getOptions().getServerTrace();
    }

    public void setServerTrace(ServerTrace serverTrace) {
        getOptions().setServerTrace(serverTrace);
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
        var serverFactory = getServerFactory();
        DebugAdapterDescriptor serverDescriptor = serverFactory != null ?
                serverFactory.createDebugAdapterDescriptor(getOptions(), environment) :
                new DebugAdapterDescriptor(getOptions(), environment, null);
        return new DAPCommandLineState(serverDescriptor, getOptions(), environment);
    }

    /**
     * Returns true if the given file can be debugged (to add/remove breakpoints) and false otherwise.
     *
     * @param file the file to debug.
     * @return true if the given file can be debugged (to add/remove breakpoints) and false otherwise.
     */
    public boolean canDebug(@NotNull VirtualFile file) {
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
                Language fileLanguage = LSPIJUtils.getFileLanguage(file, getProject());
                if (fileLanguage != null && language.equals(fileLanguage.getID())) {
                    return true;
                }
            }
        }
        return false;
    }

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

    @Nullable
    private DebugAdapterDescriptorFactory getServerFactory() {
        String serverId = getOptions().getServerId();
        if (StringUtils.isBlank(serverId)) {
            return null;
        }
        return DebugAdapterDescriptorFactoryRegistry.getInstance().getFactoryById(serverId);
    }

}