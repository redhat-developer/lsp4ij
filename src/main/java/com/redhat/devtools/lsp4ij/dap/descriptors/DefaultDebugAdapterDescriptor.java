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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.LaunchUtils;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.resolveCommandLine;

/**
 * Debug Adapter Protocol (DAP) server descriptor.
 */
@ApiStatus.Experimental
public class DefaultDebugAdapterDescriptor extends DebugAdapterDescriptor {

    public DefaultDebugAdapterDescriptor(@NotNull RunConfigurationOptions options,
                                         @NotNull ExecutionEnvironment environment,
                                         @Nullable DebugAdapterServerDefinition serverDefinition) {
        super(options, environment, serverDefinition);
    }

    // Start the Debug Adapter server.

    @Override
    public ProcessHandler startServer() throws ExecutionException {
        GeneralCommandLine commandLine = createStartServerCommandLine(options);
        if (commandLine == null) {
            throw new ExecutionException("Cannot starts the server, the command must be specified!");
        }
        return startServer(commandLine);
    }

    @Nullable
    protected GeneralCommandLine createStartServerCommandLine(@NotNull RunConfigurationOptions options) throws ExecutionException {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            String command = dapOptions.getCommand();
            if (StringUtils.isBlank(command)) {
                var server = dapOptions.getDebugAdapterServer();
                if (server instanceof UserDefinedDebugAdapterServerDefinition userDefinedServer) {
                    command = userDefinedServer.getCommandLine();
                }
            }
            // TODO : store env configuration in the options
            Map<String, String> userEnvironmentVariables = new HashMap<>();
            boolean includeSystemEnvironmentVariables = true;
            String resolvedCommandLine = resolveCommandLine(command, environment.getProject());
            return createStartServerCommandLine(resolvedCommandLine, userEnvironmentVariables, includeSystemEnvironmentVariables);
        }
        return null;
    }

    /**
     * Returns the strategy to use to know when DAP server is started and DAP client can connect to it.
     *
     * @return the strategy to use to know when DAP server is started and DAP client can connect to it.
     */
    @NotNull
    public ServerReadyConfig getServerReadyConfig(@NotNull DebugMode debugMode) {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            if (debugMode == DebugMode.ATTACH) {
                String address = LaunchUtils.resolveAttachAddress(dapOptions.getAttachAddress(), getDapParameters());
                int port = LaunchUtils.resolveAttachPort(dapOptions.getAttachPort(), getDapParameters());
                return new ServerReadyConfig(address, port);
            }
            var strategy = dapOptions.getDebugServerWaitStrategy();
            switch (strategy) {
                case TIMEOUT:
                    return new ServerReadyConfig(dapOptions.getConnectTimeout());
                case TRACE:
                    return new ServerReadyConfig(dapOptions.getNetworkAddressExtractor());
                default:
                    return new ServerReadyConfig(0);
            }
        }
        return new ServerReadyConfig(500);
    }

    public @Nullable FileType getFileType() {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            String file = dapOptions.getFile();
            int index = file != null ? file.lastIndexOf('.') : -1;
            if (index != -1) {
                String fileExtension = file.substring(index + 1, file.length());
                return FileTypeManager.getInstance().getFileTypeByExtension(fileExtension);
            }
        }
        return null;
    }

    @NotNull
    public Map<String, Object> getDapParameters() {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            return LaunchUtils.getDapParameters(dapOptions);
        }
        return Collections.emptyMap();
    }

    /**
     * Returns the debug mode (launch or attach).
     *
     * @return the debug mode (launch or attach).
     */
    @NotNull
    public DebugMode getDebugMode() {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            return dapOptions.getDebugMode();
        }
        return super.getDebugMode();
    }

    @NotNull
    public ServerTrace getServerTrace() {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            return dapOptions.getServerTrace();
        }
        return super.getServerTrace();
    }

    /**
     * Returns the DAP server name.
     *
     * @return the DAP server name.
     */
    @Nullable
    public String getServerName() {
        String serverName = null;
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            serverName = dapOptions.getServerName();
        }
        if (StringUtils.isBlank(serverName) ) {
            return super.getServerName();
        }
        return serverName;
    }

    @Override
    public boolean isDebuggableFile(@NotNull VirtualFile file, @NotNull Project project) {
        if(getServerDefinition() != null &&
                super.isDebuggableFile(file, project)) {
            return true;
        }
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            return dapOptions.isDebuggableFile(file, project);
        }
        return false;
    }
}

