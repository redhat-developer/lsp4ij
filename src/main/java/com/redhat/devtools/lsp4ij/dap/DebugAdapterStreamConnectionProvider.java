/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.util.Key;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.net.NetUtils;
import com.redhat.devtools.lsp4ij.dap.configurations.extractors.ExtractorResult;
import com.redhat.devtools.lsp4ij.dap.configurations.extractors.NetworkAddressExtractor;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.LanguageServerLogErrorHandler;
import com.redhat.devtools.lsp4ij.server.ProcessDataProvider;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.createCommandLine;

/**
 * Stream connection provider which starts a debug adapter as a debug session of LSP4IJ starts it, for the
 * {@code initialize} task of its installer: with its command line, whose {@code ${port}} becomes a free port, and its
 * environment. As a debug session, it talks to the debug adapter over a socket to that port, or to the address and port
 * which the debug adapter prints as its {@code debugServerReadyPattern} says, and otherwise over the standard input and
 * output of its process.
 * <p>
 * {@link #start()} returns once connected, and {@link #stop()} ends it while it waits, and stops the debug adapter.
 * Each provider starts at most one debug adapter.
 */
public class DebugAdapterStreamConnectionProvider implements StreamConnectionProvider, ProcessDataProvider {

    // The macro of the command line which becomes a free port, as for a debug session (DebugAdapterDescriptor)
    private static final String PORT_MACRO = "${port}";

    // While the debug adapter doesn't print its address or doesn't listen yet, the pause between two attempts
    private static final long RETRY_PAUSE_MILLIS = 50;

    private final @NotNull String commandLine;
    private final @NotNull Map<String, String> userEnvironmentVariables;
    private final boolean includeSystemEnvironmentVariables;
    private final @Nullable NetworkAddressExtractor debugServerReadyPattern;
    private final List<LanguageServerLogErrorHandler> logErrorHandlers = new CopyOnWriteArrayList<>();
    // The address and port which the debug adapter printed, as its debugServerReadyPattern says
    private final CompletableFuture<ExtractorResult> printedAddress = new CompletableFuture<>();

    // Changed under the monitor, so that stop() ends whatever start() has made
    private volatile boolean started;
    private volatile boolean stopped;
    private volatile @Nullable OSProcessHandler processHandler;
    private volatile @Nullable GeneralCommandLine startedCommandLine;
    private volatile @Nullable TransportStreams streams;
    private volatile @Nullable String connectionDescription;

    /**
     * Creates a provider.
     *
     * @param commandLine                       the command line of the debug adapter, with its macros resolved but
     *                                          {@code ${port}}.
     * @param userEnvironmentVariables          the environment variables of the user.
     * @param includeSystemEnvironmentVariables true if the debug adapter gets the environment variables of the system
     *                                          too.
     * @param debugServerReadyPattern           the pattern of what the debug adapter prints once it listens, with the
     *                                          address and the port it listens on, or null if it doesn't.
     */
    public DebugAdapterStreamConnectionProvider(@NotNull String commandLine,
                                                @NotNull Map<String, String> userEnvironmentVariables,
                                                boolean includeSystemEnvironmentVariables,
                                                @Nullable NetworkAddressExtractor debugServerReadyPattern) {
        this.commandLine = commandLine;
        this.userEnvironmentVariables = userEnvironmentVariables;
        this.includeSystemEnvironmentVariables = includeSystemEnvironmentVariables;
        this.debugServerReadyPattern = debugServerReadyPattern;
    }

    @Override
    public void addLogErrorHandler(LanguageServerLogErrorHandler handler) {
        logErrorHandlers.add(handler);
    }

    /**
     * Starts the debug adapter and connects to it.
     *
     * @throws CannotStartProcessException if this provider was started or stopped already, the debug adapter has no
     *                                     command, or it can't be started or connected to.
     */
    @Override
    public void start() throws CannotStartProcessException {
        synchronized (this) {
            if (started || stopped) {
                throw new CannotStartProcessException("This connection provider has already been started or stopped");
            }
            started = true;
        }
        try {
            connect();
        } catch (CannotStartProcessException e) {
            stop();
            throw e;
        } catch (Exception e) {
            stop();
            throw new CannotStartProcessException(e);
        }
    }

    private void connect() throws Exception {
        if (StringUtils.isBlank(commandLine)) {
            throw new CannotStartProcessException("The debug adapter has no command");
        }
        Integer port = null;
        String command = commandLine;
        if (command.contains(PORT_MACRO)) {
            port = NetUtils.findAvailableSocketPort();
            command = command.replace(PORT_MACRO, String.valueOf(port));
        }
        GeneralCommandLine cmdLine = createCommandLine(command, null, userEnvironmentVariables,
                includeSystemEnvironmentVariables);
        DAPProcessListener standardStreams = port == null && debugServerReadyPattern == null ? new DAPProcessListener() : null;
        OSProcessHandler handler;
        synchronized (this) {
            // Once the process is there, stop() destroys it, as soon as it is started below
            checkNotStopped();
            handler = new OSProcessHandler(cmdLine) {
                @Override
                protected BaseOutputReader.@NotNull Options readerOptions() {
                    return BaseOutputReader.Options.forMostlySilentProcess();
                }
            };
            processHandler = handler;
            startedCommandLine = cmdLine;
        }
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                if (ProcessOutputType.isStderr(outputType)) {
                    logErrorHandlers.forEach(logErrorHandler -> logErrorHandler.logError(event.getText().stripTrailing()));
                }
                if (debugServerReadyPattern != null && !printedAddress.isDone()) {
                    // As the trace strategy of a debug session, on any output
                    var result = debugServerReadyPattern.extract(event.getText());
                    if (result.matches()) {
                        printedAddress.complete(result);
                    }
                }
            }
        });
        if (standardStreams != null) {
            handler.addProcessListener(standardStreams);
        }
        handler.startNotify();
        if (standardStreams != null) {
            connected(new TransportStreams.DefaultTransportStreams(standardStreams.getInputStream(),
                    handler.getProcessInput()), "stdio");
            return;
        }
        String host = null;
        if (debugServerReadyPattern != null) {
            var address = waitForPrintedAddress(handler);
            if (port == null && address.port() != null) {
                port = Integer.valueOf(address.port());
            }
            host = address.address();
        }
        if (port == null) {
            throw new CannotStartProcessException("The debug adapter printed no port to connect to: " + this);
        }
        connectToSocket(host, port, handler);
    }

    private @NotNull ExtractorResult waitForPrintedAddress(@NotNull OSProcessHandler handler) throws Exception {
        while (true) {
            try {
                return printedAddress.get(RETRY_PAUSE_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException notPrintedYet) {
                checkNotStopped();
                if (handler.isProcessTerminated()) {
                    throw new CannotStartProcessException("The debug adapter ended before it printed its address: " + this);
                }
            } catch (ExecutionException e) {
                throw new CannotStartProcessException(e);
            }
        }
    }

    private void connectToSocket(@Nullable String host, int port, @NotNull OSProcessHandler handler) throws Exception {
        String address = (host != null ? host : "localhost") + ":" + port;
        while (true) {
            checkNotStopped();
            TransportStreams socket;
            try {
                socket = new TransportStreams.SocketTransportStreams(host, port);
            } catch (RuntimeException notListeningYet) {
                if (handler.waitFor(RETRY_PAUSE_MILLIS)) {
                    throw new CannotStartProcessException("The debug adapter ended before it accepted a connection on "
                            + address + ": " + this);
                }
                continue;
            }
            connected(socket, "connect to " + address);
            return;
        }
    }

    private void connected(@NotNull TransportStreams connection, @NotNull String description) throws CannotStartProcessException {
        synchronized (this) {
            if (!stopped) {
                streams = connection;
                connectionDescription = description;
                return;
            }
        }
        closeQuietly(connection);
        throw stoppedException();
    }

    private void checkNotStopped() throws CannotStartProcessException {
        if (stopped) {
            throw stoppedException();
        }
    }

    private @NotNull CannotStartProcessException stoppedException() {
        return new CannotStartProcessException("The connection provider was stopped while it started: " + this);
    }

    private static void closeQuietly(@NotNull TransportStreams connection) {
        try {
            connection.close();
        } catch (RuntimeException e) {
            // Closed already
        }
    }

    @Override
    public boolean isAlive() {
        var handler = processHandler;
        return streams != null && handler != null && handler.isStartNotified() && !handler.isProcessTerminated();
    }

    @Override
    public InputStream getInputStream() {
        var connection = streams;
        return connection != null ? connection.in : null;
    }

    @Override
    public OutputStream getOutputStream() {
        var connection = streams;
        return connection != null ? connection.out : null;
    }

    /**
     * Permanently stops this provider: it closes the connection, or ends a connection which is being made, and stops
     * the debug adapter. Repeated calls have no effect.
     */
    @Override
    public void stop() {
        OSProcessHandler handler;
        TransportStreams connection;
        synchronized (this) {
            if (stopped) {
                return;
            }
            stopped = true;
            handler = processHandler;
            connection = streams;
        }
        printedAddress.cancel(false);
        if (handler != null && !handler.isProcessTerminated()) {
            ExecutionManagerImpl.stopProcess(handler);
        }
        // The end of the process closes its standard streams
        if (connection instanceof TransportStreams.SocketTransportStreams) {
            closeQuietly(connection);
        }
    }

    @Override
    public @Nullable Long getPid() {
        var handler = processHandler;
        return handler != null ? handler.getProcess().pid() : null;
    }

    @Override
    public List<String> getCommands() {
        var cmdLine = startedCommandLine;
        if (cmdLine == null) {
            return List.of();
        }
        List<String> commands = new ArrayList<>();
        commands.add(cmdLine.getExePath());
        commands.addAll(cmdLine.getParametersList().getParameters());
        return commands;
    }

    @Override
    public @Nullable String getConnectionDescription() {
        return connectionDescription;
    }

    @Override
    public String toString() {
        return "DebugAdapterStreamConnectionProvider [commandLine=" + commandLine + "]";
    }
}
