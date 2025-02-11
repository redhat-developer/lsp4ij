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
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointHandler;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.DAPSuspendContext;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Debug Adapter Protocol (DAP) debug process.
 */
public class DAPDebugProcess extends XDebugProcess {

    private final @NotNull ExecutionResult executionResult;
    private final @NotNull XDebuggerEditorsProvider editorsProvider;
    private final @NotNull DAPBreakpointHandler breakpointHandler;
    private final @NotNull DebugAdapterDescriptor serverDescriptor;
    private final @NotNull DAPServerReadyTracker serverReadyFuture;
    private final boolean isDebug;
    private @Nullable CompletableFuture<Void> connectToServerFuture;

    private boolean isConnected;

    private Supplier<TransportStreams> streamsSupplier;
    private DAPClient parentClient;

    public DAPDebugProcess(@NotNull DAPCommandLineState dapState,
                           @NotNull XDebugSession session,
                           @NotNull ExecutionResult executionResult,
                           boolean isDebug) {
        super(session);
        this.executionResult = executionResult;
        this.isDebug = isDebug;
        this.editorsProvider = new DAPDebuggerEditorsProvider(dapState.getFileType(), this);
        this.breakpointHandler = new DAPBreakpointHandler();
        this.serverDescriptor = dapState.getServerDescriptor();
        this.isConnected = true;

        // At this step, the DAP server process is launched (but we don't know if the process is started correctly)
        serverReadyFuture = DAPServerReadyTracker.getServerReadyTracker(getProcessHandler());
        Integer port = serverReadyFuture.getPort();
        ProgressManager.getInstance().run(new Task.Backgroundable(getSession().getProject(),
                getConnectingTitle(dapState.getServerName(), port), true) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {

                // The Debug server is launched, create a DAP client and connect to the server when it is ready:
                // - wait for socket port
                // - or wait for some ms

                try {
                    // Wait for DAP server is ready...
                    serverReadyFuture.track();
                    CompletableFutures.waitUntilDone(serverReadyFuture);
                    if (CompletableFutures.isDoneNormally(serverReadyFuture)) {
                        // At this step the DAP server is started and ready to consume it with DAP clients

                        // 1. get socket stream or simple stream
                        streamsSupplier = () -> {
                            try {
                                // Call again serverReadyFuture.getPort() because the trace tracker could extract it.
                                // ex: with Go DAP server, the server start command doesn't define some port, but the DAP server generates
                                // the following trace "DAP server listening at: 127.0.0.1:51694"
                                return getTransportStreams(executionResult, serverReadyFuture.getPort());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        };

                        // 2. Connect DAP client to the DAP server by using Socket port or simple streams.
                        DebugMode debugMode = dapState.getDebugMode();
                        ServerTrace serverTrace = dapState.getServerTrace();
                        var parameters = new HashMap<>(dapState.getDAPParameters());
                        parameters.put("noDebug", !isDebug); // standard DAP parameter
                        parentClient = serverDescriptor.createClient(DAPDebugProcess.this, parameters, isDebug, debugMode, serverTrace, null);
                        connectToServerFuture = parentClient.connectToServer(indicator);

                        // Wait for DAP client is connecting to the DAP server...
                        CompletableFutures.waitUntilDone(connectToServerFuture);
                    }
                } catch (ProcessCanceledException e) {
                    // Ignore error
                    stop();
                } catch (CancellationException e) {
                    // Ignore error
                    stop();
                } catch (Throwable e) {
                    if (e instanceof ExecutionException && e.getCause() != null) {
                        e = e.getCause();
                    }
                    print(e.getMessage(), ConsoleViewContentType.LOG_ERROR_OUTPUT);
                    stop();
                }
            }
        });
    }

    private @NlsContexts.ProgressTitle @NotNull String getConnectingTitle(@Nullable String serverName,
                                                                          @Nullable Integer port) {
        if (serverName == null) {
            serverName = "DAP server";
        } else {
            serverName = "'" + serverName + "' server";
        }
        return port != null ?
                DAPBundle.message("dap.client.connecting.at.port.title", serverName, String.valueOf(port)) :
                DAPBundle.message("dap.client.connecting.no.port.title", serverName);
    }

    @Override
    public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
        return editorsProvider;
    }

    @Nullable
    @Override
    protected ProcessHandler doGetProcessHandler() {
        // Override this method to see traces of the DAP client process (ex: start command, Listening on ...) in the console.
        return executionResult.getProcessHandler();
    }

    @Override
    public @NotNull ExecutionConsole createConsole() {
        // Override this method to avoid creating a new console and use the console from the execution result.
        return executionResult.getExecutionConsole();
    }

    @Override
    public void stop() {
        if (isConnected) {
            ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> {
                        try {
                            if (serverReadyFuture != null && !CompletableFutures.isDoneNormally(serverReadyFuture)) {
                                serverReadyFuture.cancel(true);
                            }
                            if (connectToServerFuture != null && !CompletableFutures.isDoneNormally(connectToServerFuture)) {
                                connectToServerFuture.cancel(true);
                            }
                            if (parentClient != null) {
                                parentClient.terminate();
                            }
                            print("Disconnected successfully from the debug server.",
                                    ConsoleViewContentType.SYSTEM_OUTPUT);
                        } catch (Exception e) {
                            print("Disconnected exceptionally from the debug server: " + e.getMessage(),
                                    ConsoleViewContentType.ERROR_OUTPUT);
                        } finally {
                            XDebugSession session = getSession();
                            if (session != null) {
                                session.stop();
                            }
                            isConnected = false;
                        }
                    });
        }
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
        if (context instanceof DAPSuspendContext dapContext) {
            Integer threadId = dapContext.getThreadId();
            if (threadId != null) {
                DAPClient client = dapContext.getClient();
                client.next(threadId);
            }
        }
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
        if (context instanceof DAPSuspendContext dapContext) {
            Integer threadId = dapContext.getThreadId();
            if (threadId != null) {
                DAPClient client = dapContext.getClient();
                client.stepOut(threadId);
            }
        }
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
        if (context instanceof DAPSuspendContext dapContext) {
            Integer threadId = dapContext.getThreadId();
            if (threadId != null) {
                DAPClient client = dapContext.getClient();
                client.stepIn(threadId);
            }
        }
    }

    @Override
    public void resume(@Nullable XSuspendContext context) {
        if (context instanceof DAPSuspendContext dapContext) {
            Integer threadId = dapContext.getThreadId();
            if (threadId != null) {
                DAPClient client = dapContext.getClient();
                client.continue_(threadId);
            }
        }
    }

    private static TransportStreams getTransportStreams(@NotNull ExecutionResult executionResult,
                                                        @Nullable Integer port) throws IOException {
        if (port != null) {
            return new TransportStreams.SocketTransportStreams(InetAddress.getLoopbackAddress().getHostAddress(), port);
        }
        var processHandler = executionResult.getProcessHandler();
        DAPProcessListener processListener = new DAPProcessListener();
        processHandler.addProcessListener(processListener);
        return new TransportStreams.DefaultTransportStreams(processListener.getInputStream(), processHandler.getProcessInput());
    }

    public Supplier<TransportStreams> getStreamsSupplier() {
        return streamsSupplier;
    }

    public void print(@Nullable String message, @NotNull ConsoleViewContentType type) {
        if (StringUtils.isBlank(message)) {
            return;
        }
        if (message.charAt(message.length() - 1) != '\n') {
            message += "\n";
        }
        var consoleView = getSession().getConsoleView();
        if (consoleView == null) {
            return;

        }
        consoleView.print(message, type);
    }

    @Override
    public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
        return new XBreakpointHandler[]{breakpointHandler};
    }

    public @NotNull DAPBreakpointHandler getBreakpointHandler() {
        return breakpointHandler;
    }

    @NotNull
    public DebugAdapterDescriptor getServerDescriptor() {
        return serverDescriptor;
    }

    public @Nullable DAPStackFrame getCurrentDapStackFrame() {
        XStackFrame stackFrame = getSession().getCurrentStackFrame();
        if (stackFrame instanceof DAPStackFrame dapStackFrame) {
            return dapStackFrame;
        }
        return null;
    }
}
