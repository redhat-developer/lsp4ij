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
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessHandlerFactory;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Key;
import com.intellij.util.net.NetUtils;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebuggingType;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.LaunchUtils;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArgumentsPathFormat;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Debug Adapter Protocol (DAP) server descriptor.
 */
@ApiStatus.Experimental
public class DebugAdapterDescriptor {

    private static final String $_PORT = "${port}";
    private static final @NotNull Key<Integer> SERVER_PORT = Key.create("dap.server.port");

    private final @NotNull RunConfigurationOptions options;
    private final @NotNull ExecutionEnvironment environment;
    private final @Nullable DebugAdapterDescriptorFactory factory;
    private @Nullable DebugAdapterVariableSupport variableSupport;

    public DebugAdapterDescriptor(@NotNull RunConfigurationOptions options,
                                  @NotNull ExecutionEnvironment environment,
                                  @Nullable DebugAdapterDescriptorFactory factory) {
        this.options = options;
        this.environment = environment;
        this.factory = factory;
    }

    // Start the Debug Adapter server.

    /**
     * Start the Debug Adapter server.
     *
     * @return the process of the Debug Adapter server.
     * @throws ExecutionException
     */
    public ProcessHandler startServer() throws ExecutionException {
        GeneralCommandLine commandLine = createStartCommandLine(options);
        if (commandLine == null) {
            throw new ExecutionException("Cannot starts the server, the command must be specified!");
        }
        return startServer(commandLine);
    }

    /**
     * Start the Debug Adapter server with the given command line.
     *
     * @param commandLine the command line which starts the DAP server.
     * @return the process of the Debug Adapter server.
     * @throws ExecutionException
     */
    protected @NotNull OSProcessHandler startServer(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
        OSProcessHandler processHandler =
                ProcessHandlerFactory.getInstance()
                        .createColoredProcessHandler(commandLine);
        ProcessTerminatedListener.attach(processHandler);
        Integer port = commandLine.getUserData(SERVER_PORT);
        if (port != null) {
            processHandler.putUserData(SERVER_PORT, port);
        }
        return processHandler;
    }

    @Nullable
    protected GeneralCommandLine createStartCommandLine(@NotNull RunConfigurationOptions options) throws ExecutionException {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            // Download tar gz at https://github.com/microsoft/vscode-js-debug/releases/
            // GeneralCommandLine commandLine = new GeneralCommandLine("node",
            //        "C:/Users/azerr/Downloads/js-debug-dap-v1.83.0/js-debug/src/dapDebugServer.js",
            //        String.valueOf(port));
            return generateStartDAPClientCommand(dapOptions.getCommand());
        }
        return null;
    }

    protected @NotNull GeneralCommandLine generateStartDAPClientCommand(@Nullable String command) throws ExecutionException {
        if (StringUtils.isBlank(command)) {
            throw new ExecutionException("DAP command must be specified.");
        }
        Integer port = null;
        int portIndex = command.indexOf($_PORT);
        if (portIndex != -1) {
            port = getAvailablePort();
            command = command.replace($_PORT, String.valueOf(port));
        }
        GeneralCommandLine commandLine = new GeneralCommandLine(CommandUtils.createCommands(command));
        if (port != null) {
            commandLine.putUserData(SERVER_PORT, port);
        }
        return commandLine;
    }

    private static int getAvailablePort() {
        try {
            return NetUtils.findAvailableSocketPort();
        } catch (IOException e) {
            return 1234;
        }
    }

    /**
     * Returns the strategy to use to know when DAP server is started and DAP client can connect to it.
     *
     * @return the strategy to use to know when DAP server is started and DAP client can connect to it.
     */
    @NotNull
    public ServerReadyConfig getServerReadyConfig() {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            var strategy = dapOptions.getConnectingServerStrategy();
            switch (strategy) {
                case TIMEOUT:
                    return new ServerReadyConfig(null, dapOptions.getWaitForTimeout());
                case TRACE:
                    return new ServerReadyConfig(dapOptions.getNetworkAddressExtractor(), 0);
                default:
                    return new ServerReadyConfig(null, 0);
            }
        }
        return new ServerReadyConfig(null, 500);
    }

    /**
     * Returns the port used by the DAP server and null otherwise.
     *
     * @param processHandler the process which starts the DAP sever.
     * @return the port used by the DAP server and null otherwise.
     */
    @Nullable
    public static Integer getServerPort(ProcessHandler processHandler) {
        return processHandler.getUserData(SERVER_PORT);
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
     * Returns the debugging type (launch or attach).
     *
     * @return the debugging type (launch or attach).
     */
    @NotNull
    public DebuggingType getDebuggingType() {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            return dapOptions.getDebuggingType();
        }
        return DebuggingType.LAUNCH;
    }

    public @NotNull InitializeRequestArguments createInitializeRequestArguments(@NotNull Map<String, Object> dapParameters) {
        final var arguments = new InitializeRequestArguments();
        arguments.setClientID("lsp4ij.debug");
        String adapterId = "adapterId";
        if (dapParameters.get("type") instanceof String type) {
            adapterId = type;
        }
        arguments.setAdapterID(adapterId);
        arguments.setPathFormat(InitializeRequestArgumentsPathFormat.PATH);
        arguments.setSupportsVariableType(true);
        arguments.setSupportsVariablePaging(false);
        arguments.setLinesStartAt1(true);
        arguments.setColumnsStartAt1(true);
        // arguments.setSupportsRunInTerminalRequest(true);
        arguments.setSupportsStartDebuggingRequest(true);
        return arguments;
    }

    /**
     * Returns the DAP variable support.
     *
     * @return the DAP variable support.
     */
    @NotNull
    public final DebugAdapterVariableSupport getVariableSupport() {
        if (variableSupport == null) {
            initVariableSupport();
        }
        return variableSupport;
    }

    private synchronized void initVariableSupport() {
        if (variableSupport != null) {
            return;
        }
        setVariableSupport(new DebugAdapterVariableSupport());
    }

    /**
     * Initialize the DAP variable support.
     *
     * @param variableSupport the DAP variable support.
     * @return the server descriptor.
     */
    public final DebugAdapterDescriptor setVariableSupport(@NotNull DebugAdapterVariableSupport variableSupport) {
        variableSupport.setServerDescriptor(this);
        this.variableSupport = variableSupport;
        return this;
    }

    @NotNull
    public ServerTrace getServerTrace() {
        if (options instanceof DAPRunConfigurationOptions dapOptions) {
            return dapOptions.getServerTrace();
        }
        return ServerTrace.getDefaultValue();
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
        if (StringUtils.isBlank(serverName) && factory != null) {
            serverName = factory.getName();
        }
        return serverName;
    }

    @NotNull
    public DAPClient createClient(@NotNull DAPDebugProcess debugProcess,
                                  @NotNull Map<String, Object> dapParameters,
                                  boolean debugMode,
                                  @NotNull DebuggingType debuggingType,
                                  @NotNull ServerTrace serverTrace,
                                  @Nullable DAPClient parentClient) {
        return new DAPClient(debugProcess, dapParameters, debugMode, debuggingType, serverTrace, parentClient);
    }

    /**
     * Returns the server factory which have created the server descriptor and null otherwise.
     *
     * @return the server factory which have created the server descriptor and null otherwise.
     */
    public @Nullable DebugAdapterDescriptorFactory getFactory() {
        return factory;
    }
}

