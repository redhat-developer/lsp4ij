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
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.FormBuilder;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointTypeBase;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPForwardedLineBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointHandlerBase;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPDataBreakpointHandler;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPExceptionBreakpointsPanel;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPFunctionBreakpointHandler;
import com.redhat.devtools.lsp4ij.dap.breakpoints.RunToCursorBreakpoint;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPDropFrameHandler;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.DAPSuspendContext;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPFileRegistry;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPSourceReferencePosition;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValueFormat;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.disassembly.DAPAlternativeSourceHandler;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile;
import com.redhat.devtools.lsp4ij.dap.disassembly.breakpoints.DisassemblyBreakpointHandlerBase;
import com.redhat.devtools.lsp4ij.dap.sources.LoadedSourcesPanel;
import com.redhat.devtools.lsp4ij.dap.sources.ModulesPanel;
import com.redhat.devtools.lsp4ij.dap.stepping.DAPSmartStepIntoHandler;
import com.redhat.devtools.lsp4ij.dap.stepping.DAPStepIntoVariant;
import com.redhat.devtools.lsp4ij.dap.threads.ThreadsPanel;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.awaitWithCheckCanceled;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.ui.InstallerPanel;
import org.eclipse.lsp4j.debug.LoadedSourceEventArguments;
import org.eclipse.lsp4j.debug.Module;
import org.eclipse.lsp4j.debug.ModuleEventArguments;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SteppingGranularity;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Debug Adapter Protocol (DAP) debug process.
 */
public class DAPDebugProcess extends XDebugProcess implements Disposable {

    /**
     * The exit code of the debugged program, which the debug adapter reports with the DAP 'exited' event. It is put on
     * the process handler of the session, which is the debug adapter's: the handler LSP4IJ creates shows it when the
     * process ends, and a custom one can read it.
     */
    public static final Key<Integer> PROGRAM_EXIT_CODE = Key.create("lsp4ij.dap.programExitCode");

    /**
     * Keeps the exit code that a DAP session's 'exited' event reports on the session's process handler, under
     * {@link #PROGRAM_EXIT_CODE}. The child sessions that 'startDebugging' starts share that process: the exit code of
     * the session that started them wins, and a child's is kept only until that session reports one.
     *
     * @param processHandler the process handler of the debug session.
     * @param exitCode       the exit code that the 'exited' event reports.
     * @param childSession   true for a child session started with 'startDebugging', and false otherwise.
     */
    public static void keepProgramExitCode(@NotNull ProcessHandler processHandler, int exitCode, boolean childSession) {
        if (childSession) {
            processHandler.putUserDataIfAbsent(PROGRAM_EXIT_CODE, exitCode);
        } else {
            processHandler.putUserData(PROGRAM_EXIT_CODE, exitCode);
        }
    }

    private final @NotNull DAPCommandLineState dapState;
    private final @NotNull ExecutionResult executionResult;
    private final @NotNull XDebuggerEditorsProvider editorsProvider;
    private final @NotNull DAPBreakpointHandlerBase<?> breakpointHandler;
    private final @NotNull DAPFunctionBreakpointHandler functionBreakpointHandler;
    private final @NotNull DAPDataBreakpointHandler dataBreakpointHandler;

    private final @NotNull RunToCursorBreakpoint runToCursorBreakpoint;
    private final @NotNull DebugAdapterDescriptor serverDescriptor;
    private final @NotNull DAPServerReadyTracker serverReadyFuture;
    private final ThreadsPanel threadsPanel;
    private final @NotNull LoadedSourcesPanel<DAPClient> loadedSourcesPanel;
    private final @NotNull ModulesPanel<DAPClient> modulesPanel;

    /**
     * The layout of the debug tab, which the Loaded Sources and Modules tabs are added to once the DAP server reports
     * loaded sources or modules.
     */
    private volatile @Nullable RunnerLayoutUi layoutUi;
    // Whether the DAP server reported loaded sources, or modules, which may be before the layout of the debug tab exists
    private volatile boolean loadedSourcesReported;
    private volatile boolean modulesReported;
    private final AtomicBoolean loadedSourcesShown = new AtomicBoolean();
    private final AtomicBoolean modulesShown = new AtomicBoolean();

    // Disassembly
    private final @NotNull DisassemblyBreakpointHandlerBase<?> disassemblyBreakpointHandler;

    // Native line breakpoints (JS, Python, …) forwarded to the DAP server on mapped files
    private final @NotNull java.util.List<XBreakpointHandler<?>> forwardedBreakpointHandlers;
    private final @NotNull DAPAlternativeSourceHandler alternativeSourceHandler;
    private final long sessionId;

    // Smart Step Into
    private DAPSmartStepIntoHandler smartStepIntoHandler;

    // Reset Frame
    private final @NotNull DAPDropFrameHandler dropFrameHandler;

    private @Nullable CompletableFuture<Void> connectToServerFuture;
    private Status status;
    private Supplier<TransportStreams> streamsSupplier;
    private DAPClient parentClient;
    private final @NotNull DAPValueFormat valueFormat = new DAPValueFormat();

    public DAPDebugProcess(@NotNull DAPCommandLineState dapState,
                           @NotNull XDebugSession session,
                           @NotNull ExecutionResult executionResult,
                           boolean isDebug) {
        super(session);
        this.sessionId = System.currentTimeMillis();
        this.dapState = dapState;
        var project = getSession().getProject();
        this.executionResult = executionResult;
        this.serverDescriptor = dapState.getServerDescriptor();
        this.editorsProvider = serverDescriptor.createDebuggerEditorsProvider(dapState.getFileType(), this);
        this.breakpointHandler = serverDescriptor.createBreakpointHandler(session, project);
        this.forwardedBreakpointHandlers = createForwardedBreakpointHandlers(breakpointHandler, serverDescriptor, project);
        this.functionBreakpointHandler = serverDescriptor.createFunctionBreakpointHandler(session, project);
        this.dataBreakpointHandler = serverDescriptor.createDataBreakpointHandler(session, project);
        this.runToCursorBreakpoint = new RunToCursorBreakpoint(breakpointHandler);
        this.disassemblyBreakpointHandler = serverDescriptor.createDisassemblyBreakpointHandler(session, project);
        this.alternativeSourceHandler = new DAPAlternativeSourceHandler(this);
        this.threadsPanel = new ThreadsPanel(this);
        this.dropFrameHandler = new DAPDropFrameHandler(this);
        this.loadedSourcesPanel = new LoadedSourcesPanel<>(this::openLoadedSource);
        this.modulesPanel = new ModulesPanel<>();
        this.status = Status.NONE;

        // At this step, the DAP server process is launched (but we don't know if the process is started correctly)
        serverReadyFuture = DAPServerReadyTracker.getServerReadyTracker(getProcessHandler());
        Integer port = serverReadyFuture.getPort();
        String address = serverReadyFuture.getAddress();
        String taskTitle = getStartingServerTaskTitle(dapState.getFile(), dapState.getServerName(), address, port, dapState.getDebugMode());
        // Resolved here (not inside the background task) because it accesses DataContext, which requires a read action
        Map<String, Object> resolvedParams;
        try {
            resolvedParams = dapState.getDAPParameters();
        } catch (Exception e) {
            resolvedParams = new HashMap<>();
        }
        var dapParameters = resolvedParams;

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
                    awaitWithCheckCanceled(serverReadyFuture);
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
                        var parameters = new HashMap<>(dapParameters);
                        if (debugMode == DebugMode.LAUNCH) {
                            parameters.put("noDebug", !isDebug); // standard DAP parameter
                        }
                        parentClient = serverDescriptor.createClient(DAPDebugProcess.this, parameters, isDebug, debugMode, serverTrace, null);
                        connectToServerFuture = parentClient.connectToServer(indicator);

                        // Wait for DAP client is connecting to the DAP server...
                        awaitWithCheckCanceled(connectToServerFuture);
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
        runToCursorBreakpoint.ended();
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

    @Override
    public void startPausing() {
        DAPClient client = parentClient;
        if (client != null) {
            client.pause()
                    .exceptionally(error -> {
                        print("Pausing failed: " + error.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
                        return null;
                    });
        }
    }

    @Override
    public @Nullable XSmartStepIntoHandler<DAPStepIntoVariant> getSmartStepIntoHandler() {
        if (smartStepIntoHandler == null) {
            smartStepIntoHandler = new DAPSmartStepIntoHandler(getSession());
        }
        return smartStepIntoHandler;
    }

    @Override
    public @NotNull DAPDropFrameHandler getDropFrameHandler() {
        return dropFrameHandler;
    }

    private @Nullable SteppingGranularity getSteppingGranularity(@NotNull DAPClient client) {
        return client.canDisassemble() && getAlternativeSourceHandler()
                .getAlternativeSourceKindState().getValue() ? SteppingGranularity.INSTRUCTION : null;
    }

    @Override
    public void resume(@Nullable XSuspendContext context) {
        if (context instanceof DAPSuspendContext dapContext) {
            Integer threadId = dapContext.getThreadId();
            if (threadId != null) {
                // Continue... the temporary breakpoint of "Run to Cursor" stays until the program next stops
                DAPClient client = dapContext.getClient();
                client.continue_(threadId);
            }
        }
    }

    public Supplier<TransportStreams> getStreamsSupplier() {
        return streamsSupplier;
    }

    public void print(@Nullable String message, @NotNull ConsoleViewContentType type) {
        if (StringUtils.isEmpty(message)) {
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
        java.util.List<XBreakpointHandler<?>> handlers = new java.util.ArrayList<>();
        handlers.add(breakpointHandler);
        handlers.add(functionBreakpointHandler);
        handlers.add(dataBreakpointHandler);
        handlers.add(disassemblyBreakpointHandler);
        // Forward native line breakpoints (JS, Python, …) on DAP-mapped files while this session runs.
        handlers.addAll(forwardedBreakpointHandlers);
        return handlers.toArray(new XBreakpointHandler[0]);
    }

    public @NotNull DAPBreakpointHandlerBase<?> getBreakpointHandler() {
        return breakpointHandler;
    }

    /**
     * Returns the handler of the function breakpoints, which it sends with 'setFunctionBreakpoints'.
     *
     * @return the handler of the function breakpoints.
     */
    public @NotNull DAPFunctionBreakpointHandler getFunctionBreakpointHandler() {
        return functionBreakpointHandler;
    }

    /**
     * Returns the handler of the data breakpoints, which it sends with 'setDataBreakpoints'.
     *
     * @return the handler of the data breakpoints.
     */
    public @NotNull DAPDataBreakpointHandler getDataBreakpointHandler() {
        return dataBreakpointHandler;
    }

    /**
     * Returns the IntelliJ breakpoint, a line, a function or a data breakpoint, of the given DAP breakpoint id, and
     * null otherwise.
     *
     * @param id the DAP breakpoint id.
     * @return the IntelliJ breakpoint of the given DAP breakpoint id, and null otherwise.
     */
    public @Nullable XBreakpoint<?> findBreakpointById(@Nullable Integer id) {
        XBreakpoint<?> breakpoint = breakpointHandler.findBreakpointById(id);
        if (breakpoint == null) {
            breakpoint = functionBreakpointHandler.findBreakpointById(id);
        }
        return breakpoint != null ? breakpoint : dataBreakpointHandler.findBreakpointById(id);
    }

    /**
     * Returns the temporary breakpoint of "Run to Cursor", which the program removes when it next stops.
     *
     * @return the temporary breakpoint of "Run to Cursor".
     */
    public @NotNull RunToCursorBreakpoint getRunToCursorBreakpoint() {
        return runToCursorBreakpoint;
    }

    public @NotNull DisassemblyBreakpointHandlerBase<?> getDisassemblyBreakpointHandler() {
        return disassemblyBreakpointHandler;
    }

    /**
     * Build one {@link DAPForwardedLineBreakpointHandler} per registered NATIVE line-breakpoint type
     * (every {@link XLineBreakpointType} that is not our own DAP type), so that while this DAP
     * session runs, native breakpoints on files mapped to the DAP server are forwarded via
     * {@code setBreakpoints}. Nothing here is language-specific — it is driven purely by the DAP
     * server's file mappings ({@link DebugAdapterDescriptor#isDebuggableFile}).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @NotNull java.util.List<XBreakpointHandler<?>> createForwardedBreakpointHandlers(@NotNull DAPBreakpointHandlerBase<?> sharedHandler,
                                                                                                  @NotNull DebugAdapterDescriptor descriptor,
                                                                                                  @NotNull com.intellij.openapi.project.Project project) {
        java.util.List<XBreakpointHandler<?>> result = new java.util.ArrayList<>();
        try {
            for (XBreakpointType type : XBreakpointType.EXTENSION_POINT_NAME.getExtensionList()) {
                if (!(type instanceof XLineBreakpointType<?>)) {
                    continue; // only line breakpoints
                }
                if (type instanceof DAPBreakpointTypeBase<?>) {
                    continue; // our own DAP breakpoint type is already handled by the DAP handler
                }
                Class<? extends XBreakpointType<XLineBreakpoint<?>, ?>> typeClass =
                        (Class<? extends XBreakpointType<XLineBreakpoint<?>, ?>>) type.getClass();
                result.add(new DAPForwardedLineBreakpointHandler(typeClass, sharedHandler, descriptor, project));
            }
        } catch (Exception e) {
            // Never let breakpoint forwarding break the debug session; fall back to DAP-only breakpoints.
        }
        return result;
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
        runToCursorBreakpoint
                .set(position)// Send a temporary breakpoint with the proper position to the DAP server
                .thenApply(unused -> {
                    resume(context); // and resume the debugger
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
                // The "Loaded Sources" and "Modules" panels are added once the DAP server reports any, and those it
                // reported before this layout existed, as when the client connected first, are added now
                layoutUi = ui;
                if (loadedSourcesReported) {
                    showLoadedSources();
                }
                if (modulesReported) {
                    showModules();
                }

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

    /**
     * Shows the sources which the DAP server of the given client reports as loaded, in the "Loaded Sources" tab, which
     * is added the first time.
     *
     * @param client  the client whose DAP server reports the sources.
     * @param sources the loaded sources.
     */
    public void refreshLoadedSources(@NotNull DAPClient client, @NotNull Source[] sources) {
        loadedSourcesReported = true;
        showLoadedSources();
        loadedSourcesPanel.refresh(client, sources);
    }

    /**
     * Updates the "Loaded Sources" tab, which is added the first time, with a 'loadedSource' event.
     *
     * @param client the client whose DAP server sent the event.
     * @param args   the 'loadedSource' event.
     */
    public void updateLoadedSource(@NotNull DAPClient client, @NotNull LoadedSourceEventArguments args) {
        loadedSourcesReported = true;
        showLoadedSources();
        loadedSourcesPanel.update(client, args);
    }

    /**
     * Shows the modules which the DAP server of the given client reports, in the "Modules" tab, which is added the
     * first time.
     *
     * @param client  the client whose DAP server reports the modules.
     * @param modules the modules.
     */
    public void refreshModules(@NotNull DAPClient client, @NotNull Module[] modules) {
        modulesReported = true;
        showModules();
        modulesPanel.refresh(client, modules);
    }

    /**
     * Updates the "Modules" tab, which is added the first time, with a 'module' event.
     *
     * @param client the client whose DAP server sent the event.
     * @param args   the 'module' event.
     */
    public void updateModule(@NotNull DAPClient client, @NotNull ModuleEventArguments args) {
        modulesReported = true;
        showModules();
        modulesPanel.update(client, args);
    }

    /**
     * @return the "Loaded Sources" panel.
     */
    public @NotNull LoadedSourcesPanel<DAPClient> getLoadedSourcesPanel() {
        return loadedSourcesPanel;
    }

    /**
     * @return the "Modules" panel.
     */
    public @NotNull ModulesPanel<DAPClient> getModulesPanel() {
        return modulesPanel;
    }

    private void showLoadedSources() {
        showContent(loadedSourcesShown, LoadedSourcesPanel.ID, loadedSourcesPanel,
                DAPBundle.message("dap.panels.loaded.sources"), loadedSourcesPanel.getDefaultFocusedComponent());
    }

    private void showModules() {
        showContent(modulesShown, ModulesPanel.ID, modulesPanel,
                DAPBundle.message("dap.panels.modules"), modulesPanel.getDefaultFocusedComponent());
    }

    /**
     * Adds the given panel to the debug tab, once: when the layout of the debug tab does not exist yet, it is added
     * when the layout is created.
     */
    private void showContent(@NotNull AtomicBoolean shown,
                             @NotNull String id,
                             @NotNull JComponent panel,
                             @NotNull String title,
                             @NotNull JComponent focused) {
        RunnerLayoutUi ui = layoutUi;
        if (ui == null || !shown.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            final Content content = ui.createContent(id, panel, title, null, focused);
            content.setCloseable(false);
            ui.addContent(content, 0, PlaceInGrid.left, false);
        });
    }

    /**
     * Opens a loaded source: one with a source reference in the text its DAP server gives for it, another in its file.
     *
     * @param client the client whose DAP server reported the source.
     * @param source the source to open.
     */
    private void openLoadedSource(@NotNull DAPClient client, @NotNull Source source) {
        int sourceReference = source.getSourceReference() != null ? source.getSourceReference() : 0;
        if (sourceReference > 0) {
            var file = DAPFileRegistry.getInstance().getOrCreateDAPFile(getConfigName(), LoadedSourcesPanel.name(source), getProject());
            new DAPSourceReferencePosition(file, sourceReference, 0, client).createNavigatable(getProject()).navigate(true);
            return;
        }
        String path = source.getPath();
        if (path != null) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
                if (file != null) {
                    ApplicationManager.getApplication().invokeLater(() -> new OpenFileDescriptor(getProject(), file).navigate(true));
                }
            });
        }
    }

    @Override
    public @Nullable DAPAlternativeSourceHandler getAlternativeSourceHandler() {
        return alternativeSourceHandler;
    }

    /**
     * Returns the format in which the clients of this debug session ask for values.
     *
     * @return the format in which the clients of this debug session ask for values.
     */
    public @NotNull DAPValueFormat getValueFormat() {
        return valueFormat;
    }

    /**
     * Returns true if the DAP server of this debug session, or that of a child session, formats values, and false
     * otherwise.
     *
     * @return true if the DAP server of this debug session, or that of a child session, formats values, and false
     * otherwise.
     */
    public boolean isSupportsValueFormattingOptions() {
        return parentClient != null && parentClient.isSupportsValueFormattingOptionsInSessions();
    }

    public @Nullable DisassemblyFile getDisassemblyFile() {
        if (parentClient == null || !parentClient.canDisassemble()) {
            return null;
        }
        String configName = getConfigName();
        return (DisassemblyFile) DAPFileRegistry.getInstance()
                .getOrCreateDAPFile(configName, DisassemblyFile.FILE_NAME, getProject());
    }

    public @NotNull String getConfigName() {
        return dapState.getEnvironment().getRunProfile().getName();
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
        functionBreakpointHandler.dispose();
        dataBreakpointHandler.dispose();
        disassemblyBreakpointHandler.dispose();
        getAlternativeSourceHandler().dispose();
        var disassemblyFile = getDisassemblyFile();
        if (disassemblyFile != null) {
            disassemblyFile.dispose();
        }
    }

    public long getSessionId() {
        return sessionId;
    }

    private enum Status {
        NONE,
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }
}
