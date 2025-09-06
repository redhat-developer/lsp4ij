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
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.FormBuilder;
import com.intellij.xdebugger.XAlternativeSourceHandler;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointHandlerBase;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPExceptionBreakpointsPanel;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.DAPSuspendContext;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.disassembly.DAPAlternativeSourceHandler;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFileRegistry;
import com.redhat.devtools.lsp4ij.dap.disassembly.breakpoints.DisassemblyBreakpointHandlerBase;
import com.redhat.devtools.lsp4ij.dap.threads.ThreadsPanel;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.ui.InstallerPanel;
import org.eclipse.lsp4j.debug.SteppingGranularity;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
public class DAPDebugProcess extends XDebugProcess implements Disposable {

    private final @NotNull DAPCommandLineState dapState;
    private final @NotNull ExecutionResult executionResult;
    private final @NotNull XDebuggerEditorsProvider editorsProvider;
    private final @NotNull DAPBreakpointHandlerBase<?> breakpointHandler;
    private final @NotNull DebugAdapterDescriptor serverDescriptor;
    private final @NotNull DAPServerReadyTracker serverReadyFuture;
    private final ThreadsPanel threadsPanel;
    private @Nullable CompletableFuture<Void> connectToServerFuture;

    private Status status;
    private Supplier<TransportStreams> streamsSupplier;
    private DAPClient parentClient;

    // Disassembly
    private final @NotNull DisassemblyBreakpointHandlerBase<?> disassemblyBreakpointHandler;
    private final @NotNull  DAPAlternativeSourceHandler allternativeSourceHandler;

    public DAPDebugProcess(@NotNull DAPCommandLineState dapState,
                           @NotNull XDebugSession session,
                           @NotNull ExecutionResult executionResult,
                           boolean isDebug) {
        super(session);
        this.dapState = dapState;
        var project = getSession().getProject();
        this.executionResult = executionResult;
        this.serverDescriptor = dapState.getServerDescriptor();
        this.editorsProvider = serverDescriptor.createDebuggerEditorsProvider(dapState.getFileType(), this);
        this.breakpointHandler = serverDescriptor.createBreakpointHandler(session, project);
        this.disassemblyBreakpointHandler = serverDescriptor.createDisassemblyBreakpointHandler(session, project);
        this.allternativeSourceHandler = new DAPAlternativeSourceHandler(this);
        this.threadsPanel = new ThreadsPanel(this);
        this.status = Status.NONE;

        // At this step, the DAP server process is launched (but we don't know if the process is started correctly)
        serverReadyFuture = DAPServerReadyTracker.getServerReadyTracker(getProcessHandler());
        Integer port = serverReadyFuture.getPort();
        String address = serverReadyFuture.getAddress();
        String taskTitle = getStartingServerTaskTitle(dapState.getFile(), dapState.getServerName(), address, port, dapState.getDebugMode());

        ProgressManager.getInstance().run(new Task.Backgroundable(project, taskTitle, true) {

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

                        // Refresh Threads panel
                        parentClient
                                .getThreads(true);
                    }
                } catch (ProcessCanceledException e) {
                    stop();
                    throw e;
                } catch (CancellationException e) {
                    // Ignore error
                    stop();
                } catch (Throwable e) {
                    Throwable throwable = e;
                    if (throwable instanceof ExecutionException && throwable.getCause() != null) {
                        throwable = throwable.getCause();
                    }
                    print(throwable.getMessage(), ConsoleViewContentType.LOG_ERROR_OUTPUT);
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
            if (address != null) {
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
        if (status != Status.STOPPED && status != Status.STOPPING) {
            ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> {
                        try {
                            status = Status.STOPPING;
                            dispose();
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
                var granularity = getSteppingGranularity(client);
                client.next(threadId, granularity);
            }
        }
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
        if (context instanceof DAPSuspendContext dapContext) {
            Integer threadId = dapContext.getThreadId();
            if (threadId != null) {
                DAPClient client = dapContext.getClient();
                var granularity = getSteppingGranularity(client);
                client.stepOut(threadId, granularity);
            }
        }
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
        if (context instanceof DAPSuspendContext dapContext) {
            Integer threadId = dapContext.getThreadId();
            if (threadId != null) {
                DAPClient client = dapContext.getClient();
                var granularity = getSteppingGranularity(client);
                client.stepIn(threadId, granularity);
            }
        }
    }

    private @Nullable SteppingGranularity getSteppingGranularity(@NotNull DAPClient client) {
        return client.canDisassemble() && getAlternativeSourceHandler()
                .getAlternativeSourceKindState().getValue() ? SteppingGranularity.INSTRUCTION : null;
    }

    @Override
    public void resume(@Nullable XSuspendContext context) {
        resume(context, null);
    }

    private void resume(@Nullable XSuspendContext context,
                        @Nullable XSourcePosition runToCursorPosition) {
        if (context instanceof DAPSuspendContext dapContext) {
            Integer threadId = dapContext.getThreadId();
            if (threadId != null) {
                // Continue...
                DAPClient client = dapContext.getClient();
                var result = client.continue_(threadId);
                if (runToCursorPosition != null) {
                    // "Run to Cursor" has been used, remove her temporary breakpoint
                    // after the 'continue' DAP request.
                    result
                            .thenRun(() -> getBreakpointHandler()
                                    .unregisterTemporaryBreakpoint(runToCursorPosition));
                }
            }
        }
    }

    public Supplier<TransportStreams> getStreamsSupplier() {
        return streamsSupplier;
    }

    public void print(@Nullable String message, @NotNull ConsoleViewContentType type) {
        if (message == null || StringUtils.isBlank(message)) {
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
        return new XBreakpointHandler[]{breakpointHandler, disassemblyBreakpointHandler};
    }

    public @NotNull DAPBreakpointHandlerBase<?> getBreakpointHandler() {
        return breakpointHandler;
    }

    public @NotNull DisassemblyBreakpointHandlerBase<?> getDisassemblyBreakpointHandler() {
        return disassemblyBreakpointHandler;
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
                .registerTemporaryBreakpoint(position)// Send a temporary breakpoint with the proper position to the DAP server
                .thenApply(unused -> {
                    resume(context, position); // and resume the debugger
                    return null;
                });
    }

    @Override
    public @NotNull XDebugTabLayouter createTabLayouter() {
        return new XDebugTabLayouter() {

            private static void resisterInstallerTab(@NotNull RunnerLayoutUi ui,
                                                     @Nullable String installerConfiguration,
                                                     @NotNull Project project) {
                Content customTab = ui.createContent(
                        "dap-installer-tab",
                        createInstallerPanel(installerConfiguration, project),
                        DAPBundle.message("dap.settings.editor.installer.tab"),
                        AllIcons.Actions.Install,
                        null
                );
                ui.addContent(customTab);
            }

            private static JComponent createInstallerPanel(@Nullable String installerConfiguration,
                                                           @NotNull Project project) {
                FormBuilder builder = FormBuilder.createFormBuilder();
                var installerPanel = new InstallerPanel(builder, true, project);
                if (installerConfiguration != null) {
                    ApplicationManager.getApplication()
                            .invokeLater(() -> installerPanel.getInstallerConfigurationWidget().setText(installerConfiguration));
                }
                return builder.getPanel();
            }

            @Override
            public void registerAdditionalContent(@NotNull RunnerLayoutUi ui) {
                // Register "Exception Breakpoints" panel
                registerBreakpointsPanel(ui);

                // Register "Threads" panel
                registerThreadsPanel(ui);

                if (serverDescriptor.isShowInstallerTab()) {
                    // Register the "Installer" tab
                    String installerConfiguration = DAPDebugProcess.this.dapState.getInstallerConfiguration();
                    var project = DAPDebugProcess.this.getProject();
                    resisterInstallerTab(ui, installerConfiguration, project);
                }
            }

            private void registerBreakpointsPanel(@NotNull RunnerLayoutUi ui) {
                var breakpointsPanel = breakpointHandler.getExceptionBreakpointsPanel();
                final Content breakpointsContent = ui.createContent(
                        DAPExceptionBreakpointsPanel.ID, breakpointsPanel, DAPBundle.message("dap.panels.exception.breakpoints"), null, breakpointsPanel.getDefaultFocusedComponent());
                breakpointsContent.setCloseable(false);
                ui.addContent(breakpointsContent, 0, PlaceInGrid.left, false);
            }

            private void registerThreadsPanel(@NotNull RunnerLayoutUi ui) {
                final Content threadsContent = ui.createContent(
                        "dap-threads-panel", threadsPanel, DAPBundle.message("dap.panels.threads"), null, threadsPanel.getDefaultFocusedComponent());
                threadsContent.setCloseable(false);
                ui.addContent(threadsContent, 0, PlaceInGrid.left, false);
            }
        };
    }

    public CompletableFuture<@Nullable Thread[]> getThreads() {
        if (parentClient != null) {
            return parentClient
                    .getThreads(false);
        }
        return CompletableFuture.completedFuture(null);
    }

    public void refreshThreads(Thread[] threads) {
        threadsPanel.refreshThreads(threads);
    }

    public void refreshThread(ThreadEventArguments args) {
        threadsPanel.refreshThread(args);
    }

    @Override
    public @Nullable DAPAlternativeSourceHandler getAlternativeSourceHandler() {
        return allternativeSourceHandler;
    }

    public @Nullable DisassemblyFile getDisassemblyFile() {
        if (parentClient == null || !parentClient.canDisassemble()) {
            return null;
        }
        String configName = dapState.getEnvironment().getRunProfile().getName();
        return DisassemblyFileRegistry.getInstance()
                .getOrCreateDisassemblyFile(configName, getProject());
    }

    public @NotNull Project getProject() {
        return getSession().getProject();
    }

    @Override
    public void dispose() {
        CancellationSupport.cancel(serverReadyFuture);
        CancellationSupport.cancel(connectToServerFuture);
        if (parentClient != null) {
            parentClient.terminate();
        }
        breakpointHandler.dispose();
        disassemblyBreakpointHandler.dispose();
        getAlternativeSourceHandler().dispose();
        var disassemblyFile = getDisassemblyFile();
        if (disassemblyFile != null) {
            disassemblyFile.dispose();
        }
    }

    private enum Status {
        NONE,
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }
}
