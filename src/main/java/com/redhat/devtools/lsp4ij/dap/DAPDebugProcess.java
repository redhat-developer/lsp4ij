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
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
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

    private enum Status {
        NONE,
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }

    private final @NotNull ExecutionResult executionResult;
    private final @NotNull XDebuggerEditorsProvider editorsProvider;
    private final @NotNull DAPBreakpointHandler breakpointHandler;
    private final @NotNull DebugAdapterDescriptor serverDescriptor;
    private final @NotNull DAPServerReadyTracker serverReadyFuture;
    private final boolean isDebug;
    private @Nullable CompletableFuture<Void> connectToServerFuture;

    private Status status;

    private Supplier<TransportStreams> streamsSupplier;
    private DAPClient parentClient;

    public DAPDebugProcess(@NotNull DAPCommandLineState dapState,
                           @NotNull XDebugSession session,
                           @NotNull ExecutionResult executionResult,
                           boolean isDebug) {
        super(session);
        var project = getSession().getProject();
        this.executionResult = executionResult;
        this.isDebug = isDebug;
        this.editorsProvider = new DAPDebuggerEditorsProvider(dapState.getFileType(), this);
        this.serverDescriptor = dapState.getServerDescriptor();
        this.breakpointHandler = new DAPBreakpointHandler(serverDescriptor, project);
        this.status = Status.NONE;

        // At this step, the DAP server process is launched (but we don't know if the process is started correctly)
        serverReadyFuture = DAPServerReadyTracker.getServerReadyTracker(getProcessHandler());
        Integer port = serverReadyFuture.getPort();
        String address = serverReadyFuture.getAddress();
        String taskTitle = getStartingServerTaskTitle(dapState.getFile(), dapState.getServerName(), address, port, dapState.getDebugMode());

        ProgressManager.getInstance().run(new Task.Backgroundable(project,taskTitle, true) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {

                DAPDebugProcess.this.status = Status.STARTING;
                print(taskTitle, ConsoleViewContentType.SYSTEM_OUTPUT);

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
                                return getTransportStreams(executionResult, serverReadyFuture.getAddress(), serverReadyFuture.getPort());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        };

                        // 2. Connect DAP client to the DAP server by using Socket port or simple streams.
                        DebugMode debugMode = dapState.getDebugMode();
                        ServerTrace serverTrace = dapState.getServerTrace();
                        var parameters = new HashMap<>(dapState.getDAPParameters());
                        if (debugMode == DebugMode.LAUNCH) {
                            parameters.put("noDebug", !isDebug); // standard DAP parameter
                        }
                        parentClient = serverDescriptor.createClient(DAPDebugProcess.this, parameters, isDebug, debugMode, serverTrace, null);
                        connectToServerFuture = parentClient.connectToServer(indicator);

                        // Wait for DAP client is connecting to the DAP server...
                        CompletableFutures.waitUntilDone(connectToServerFuture);
                        DAPDebugProcess.this.status = Status.STARTED;
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

    private static @NlsContexts.ProgressTitle @NotNull String getStartingServerTaskTitle(@Nullable String file,
                                                                                         @Nullable String serverName,
                                                                                         @Nullable String address,
                                                                                         @Nullable Integer port,
                                                                                         @NotNull DebugMode debugMode) {
        // Ex :
        // Launching 'test.ts' with 'VSCode JS Debug' at 9999 port...
        // Attaching 'test.ts' with 'VSCode JS Debug' at 9999 port...
        StringBuilder title = new StringBuilder(debugMode == DebugMode.ATTACH ? "Attaching" : "Launching");

        // Add file info
        if (file != null) {
            int index = file.lastIndexOf('/');
            if (index == -1) {
                index = file.lastIndexOf('\\');
            }
            title.append(" '");
            title.append(index != -1 ? file.substring(index + 1) : file);
            title.append("'");
        }

        // Add server info
        title.append(" ");
        if (file != null) {
            title.append("with ");
        }
        title.append("'");
        title.append(serverName == null ? "DAP server" : serverName);
        title.append("'");

        // Add port info
        if (address != null || port != null) {
            title.append(" at ");
            if(address != null) {
                title.append(address);
                if (port != null) {
                    title.append(":");
                }
            }
            if (port != null) {
                title.append(port);
            }
        }

        return title.toString();
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
        if (status !=Status.STOPPED && status != Status.STOPPING) {
            ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> {
                        try {
                            status = Status.STOPPING;
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
                            status = Status.STOPPED;
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
                                                        @Nullable String address,
                                                        @Nullable Integer port) throws IOException {
        if (port != null) {
            return new TransportStreams.SocketTransportStreams(address != null ? address : InetAddress.getLoopbackAddress().getHostAddress(), port);
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
        final String text = message;
        AppUIUtil.invokeOnEdt(() -> {
            var consoleView = getSession().getConsoleView();
            if (consoleView == null) {
                return;

            }
            consoleView.print(text, type);
        });
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

    @Override
    public void runToPosition(@NotNull XSourcePosition position,
                              @Nullable XSuspendContext context) {
        getBreakpointHandler()
                .sendTemporaryBreakpoint(position)// Send a temporary breakpoint with the proper position to the DAP server
                .thenApply(unused -> {
                    resume(context); // and resume the debugger
                    return null;
                });
    }
}
