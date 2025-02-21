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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.NetUtils;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.configurations.DebuggableFile;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.internal.IntelliJPlatformUtils;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArgumentsPathFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.createCommandLine;

public abstract class DebugAdapterDescriptor implements DebuggableFile {

    protected static final String $_PORT = "${port}";

    protected static final @NotNull Key<Integer> SERVER_PORT = Key.create("dap.server.port");

    protected final @NotNull RunConfigurationOptions options;
    protected final @NotNull ExecutionEnvironment environment;
    private final @Nullable DebugAdapterServerDefinition serverDefinition;
    private @NotNull DebugAdapterVariableSupport variableSupport;

    public DebugAdapterDescriptor(@NotNull RunConfigurationOptions options,
                                  @NotNull ExecutionEnvironment environment,
                                  @Nullable DebugAdapterServerDefinition serverDefinition) {
        this.options = options;
        this.environment = environment;
        this.serverDefinition = serverDefinition;
    }

    // Start the Debug Adapter server.

    /**
     * Start the Debug Adapter server.
     *
     * @return the process of the Debug Adapter server.
     * @throws ExecutionException
     */
    public abstract ProcessHandler startServer() throws ExecutionException;

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

    protected @NotNull GeneralCommandLine createStartServerCommandLine(@Nullable String command) throws ExecutionException {
        return createStartServerCommandLine(command, Collections.emptyMap(), false);

    }

    protected @NotNull GeneralCommandLine createStartServerCommandLine(@Nullable String command,
                                                                       @NotNull Map<String, String> userEnvironmentVariables,
                                                                       boolean includeSystemEnvironmentVariables) throws ExecutionException {
        if (StringUtils.isBlank(command)) {
            throw new ExecutionException("DAP server command must be specified.");
        }
        Integer port = null;
        int portIndex = command.indexOf($_PORT);
        if (portIndex != -1) {
            port = getAvailablePort();
            command = command.replace($_PORT, String.valueOf(port));
        }
        GeneralCommandLine commandLine = createCommandLine(command, userEnvironmentVariables, includeSystemEnvironmentVariables);
        if (port != null) {
            commandLine.putUserData(DebugAdapterDescriptor.SERVER_PORT, port);
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

    @NotNull
    public DAPClient createClient(@NotNull DAPDebugProcess debugProcess,
                                  @NotNull Map<String, Object> dapParameters,
                                  boolean isDebug,
                                  @NotNull DebugMode debugMode,
                                  @NotNull ServerTrace serverTrace,
                                  @Nullable DAPClient parentClient) {
        return new DAPClient(debugProcess, dapParameters, isDebug, debugMode, serverTrace, parentClient);
    }


    /**
     * Returns the port used by the DAP server and null otherwise.
     *
     * @param processHandler the process which starts the DAP sever.
     * @return the port used by the DAP server and null otherwise.
     */
    @Nullable
    public static Integer getServerPort(ProcessHandler processHandler) {
        return processHandler.getUserData(DebugAdapterDescriptor.SERVER_PORT);
    }

    public @NotNull InitializeRequestArguments createInitializeRequestArguments(@NotNull Map<String, Object> dapParameters) {
        final var args = new InitializeRequestArguments();
        args.setClientID("lsp4ij.debug");
        args.setClientName(IntelliJPlatformUtils.getClientInfo().getName());
        String adapterId = "adapterId";
        if (dapParameters.get("type") instanceof String type) {
            adapterId = type;
        }
        args.setAdapterID(adapterId);
        args.setPathFormat(InitializeRequestArgumentsPathFormat.PATH);
        args.setSupportsVariableType(true);
        args.setSupportsVariablePaging(false);
        args.setLinesStartAt1(true);
        args.setColumnsStartAt1(true);
        // arguments.setSupportsRunInTerminalRequest(true);
        args.setSupportsStartDebuggingRequest(true);
        return args;
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
    public abstract Map<String, Object> getDapParameters();

    /**
     * Returns the debug mode (launch or attach).
     *
     * @return the debug mode (launch or attach).
     */
    @NotNull
    public DebugMode getDebugMode() {
        return DebugMode.LAUNCH;
    }

    @NotNull
    public ServerTrace getServerTrace() {
        return ServerTrace.getDefaultValue();
    }

    /**
     * Returns the strategy to use to know when DAP server is started and DAP client can connect to it.
     *
     * @return the strategy to use to know when DAP server is started and DAP client can connect to it.
     */
    @NotNull
    public abstract ServerReadyConfig getServerReadyConfig(@NotNull DebugMode debugMode);

    public abstract @Nullable FileType getFileType();

    /**
     * Returns the DAP server name.
     *
     * @return the DAP server name.
     */
    @Nullable
    public String getServerName() {
        return serverDefinition != null ? serverDefinition.getName() : null;
    }

    public @Nullable DebugAdapterServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    @Override
    public boolean isDebuggableFile(@NotNull VirtualFile file, @NotNull Project project) {
        return serverDefinition != null && serverDefinition.isDebuggableFile(file, project);
    }
}
