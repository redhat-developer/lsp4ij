/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.console.explorer.TracingMessageConsumer;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.TransportStreams;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPDataBreakpointHandler;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValueFormat;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile;
import com.redhat.devtools.lsp4ij.dap.runInTerminal.RunInTerminalManager;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Module;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.intellij.codeInsight.hint.HintManagerImpl.getHintPosition;

/**
 * Debug Protocol Adapter (DAP) client implementation.
 */
public class DAPClient implements IDebugProtocolClient, Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DAPClient.class);

    public static final org.eclipse.lsp4j.debug.Thread[] EMPTY_THREADS = new org.eclipse.lsp4j.debug.Thread[0];

    private static final int MAX_EXCEPTION_HINT_STACK_LINES = 10;
    private static final long EXCEPTION_INFO_TIMEOUT_SECONDS = 1;
    /**
     * Any events we receive from the adapter that require further contact with the
     * adapter needs to be farmed off to another thread as the events arrive at the
     * same thread. (Note for requests, use the *Async versions on
     * completable future to achieve the same effect.)
     */
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    /**
     * Debug adapters are not supposed to send initialized event until after
     * replying to initializeRequest with Capabilities. However some debug adapters
     * don't respect this (or didn't in the past). Even if they do respect this,
     * sometimes due to the multithreaded event handling, the initialized event
     * arrives before the capabilities are stored. Therefore use a future to guard
     * reading the capabilities before they are ready.
     */
    private final CompletableFuture<@Nullable Capabilities> capabilitiesFuture = new CompletableFuture<>();
    /**
     * The capabilities of the 'initialize' response, updated by the 'capabilities' events.
     */
    private final DAPCapabilities adapterCapabilities = new DAPCapabilities();
    /**
     * Guards what the breakpoints and the exception breakpoint filters were sent with: a step of the configuration
     * reads the current capabilities and sends with them, and a 'capabilities' event changes them and sends again what
     * it changed, one after the other.
     */
    private final Object capabilitiesLock = new Object();
    // The capabilities which the function breakpoints, the data breakpoints and the exception breakpoint filters were
    // last sent to the DAP server with, null until the configuration sends them. An event which arrives during the
    // configuration changes what its next steps send, and what its steps before sent is sent again once it is done.
    // Guarded by capabilitiesLock.
    private @Nullable Capabilities functionBreakpointsSentWith;
    private @Nullable Capabilities dataBreakpointsSentWith;
    private @Nullable Capabilities exceptionFiltersSentWith;
    // Whether the disassembly breakpoint handler sends the instruction breakpoints to the DAP server: it is initialized
    // for it once, when the DAP server supports disassembling. Guarded by capabilitiesLock.
    private boolean instructionBreakpointsInitialized;
    // Whether the configuration is done: from then on, a 'capabilities' event sends again what it changes. Guarded by
    // capabilitiesLock.
    private boolean configured;
    /**
     * Once we have received initialized event, this member will be "done" as a flag
     */
    private final CompletableFuture<@Nullable Void> initialized = new CompletableFuture<>();

    private final TransportStreams transportStreams;
    private final @NotNull DAPDebugProcess debugProcess;
    private final @NotNull Map<String, Object> dapParameters;
    private final @Nullable DAPClient parentClient;
    private final boolean isDebug;
    private final @NotNull DebugMode debugMode;
    private final @NotNull ServerTrace serverTrace;
    private Future<Void> debugProtocolFuture;
    private IDebugProtocolServer debugProtocolServer;
    private @NotNull
    final List<DAPClient> childrenClient;
    // Child sessions which sent 'terminated': out of childrenClient so they get no more requests, kept to be disposed
    private final List<DAPClient> terminatedChildren = new CopyOnWriteArrayList<>();
    private volatile boolean terminatedReceived;
    // Set once the client ends the session, with terminate() or dispose(): the end of its connection is expected then
    private volatile boolean ending;
    // dispose() runs once: the end of the connection, which it causes, may dispose the client too
    private final AtomicBoolean disposed = new AtomicBoolean();
    private boolean sentTerminateRequest;
    // Whether the loaded sources and modules were asked for. They are asked for at the first stop, and then follow the
    // 'loadedSource' and 'module' events: asking at each stop would get all of them again at each step.
    private final AtomicBoolean loadedSourcesAndModulesAsked = new AtomicBoolean();
    // Whether the last request that resumed the program was a step that resumed only its thread. A breakpoint which
    // doesn't suspend then resumes only the stopped thread, so the threads the step kept stopped stay stopped.
    private volatile boolean singleThreadResumed;

    public DAPClient(@NotNull DAPDebugProcess debugProcess,
                     @NotNull Map<String, Object> dapParameters,
                     boolean isDebug,
                     @NotNull DebugMode debugMode,
                     @NotNull ServerTrace serverTrace,
                     @Nullable DAPClient parentClient) {
        this.transportStreams = debugProcess.getStreamsSupplier().get();
        this.debugProcess = debugProcess;
        this.dapParameters = dapParameters;
        this.isDebug = isDebug;
        this.debugMode = debugMode;
        this.serverTrace = serverTrace;
        this.parentClient = parentClient;
        this.childrenClient = new CopyOnWriteArrayList<>();
    }

    @NotNull
    public CompletableFuture<Void> connectToServer(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        TracingMessageConsumer tracing = serverTrace != ServerTrace.off ? new TracingMessageConsumer() : null;

        UnaryOperator<MessageConsumer> wrapper = consumer -> {
            MessageConsumer result = consumer;
            if (tracing != null) {
                result = message -> {
                    // Display DAP message in the console
                    String log = tracing.log(message, consumer, serverTrace);
                    debugProcess.print(log, ConsoleViewContentType.SYSTEM_OUTPUT);
                    consumer.consume(message);
                    // Display DAP response error in the console if needed
                    if (message instanceof ResponseMessage responseMessage) {
                        ResponseError responseError = responseMessage.getError();
                        if (responseError != null && StringUtils.isNotBlank(responseError.getMessage())) {
                            debugProcess.print(responseError.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
                        }
                    }
                };
            }
            return result;
        };

        Launcher<? extends IDebugProtocolServer> debugProtocolLauncher = createLauncher(wrapper,
                transportStreams.in,
                transportStreams.out,
                threadPool);

        debugProtocolFuture = debugProtocolLauncher.startListening();
        endWhenTheConnectionCloses(debugProtocolFuture);
        debugProtocolServer = debugProtocolLauncher.getRemoteProxy();

        return initialize(dapParameters, indicator);
    }

    private CompletableFuture<Void> initialize(@NotNull Map<String, Object> dapParameters,
                                               @NotNull ProgressIndicator monitor) {
        final var arguments = debugProcess.getServerDescriptor().createInitializeRequestArguments(dapParameters);

        monitor.checkCanceled();
        monitor.setText2("Initializing connection to debug adapter");

        CompletableFuture<?> launchAttachFuture = getDebugProtocolServer()
                .initialize(arguments)
                .thenAccept(capabilities -> {
                    monitor.checkCanceled();
                    monitor.setFraction(10d / 100d);
                    if (capabilities == null) {
                        debugProcess.print("Debug adapter unexpectedly returned 'null' Capabilities from initializeRequest. "
                                + "A default Capabilities will be used instead.", ConsoleViewContentType.LOG_WARNING_OUTPUT);
                        capabilities = new Capabilities();
                    }
                    // with the capabilities events which arrived before this response
                    capabilitiesFuture.complete(adapterCapabilities.initialize(capabilities));
                }).thenCompose(unused -> {
                    monitor.checkCanceled();
                    monitor.setFraction(20d / 100d);
                    boolean isLaunchRequest = debugMode == DebugMode.LAUNCH;
                    if (isLaunchRequest) {
                        monitor.setText2("Launching program");
                        return getDebugProtocolServer().launch(dapParameters);
                    } else {
                        monitor.setText2("Attaching to running program");
                        return getDebugProtocolServer().attach(dapParameters);
                    }
                }).handle((q, t) -> {
                    if (t != null) {
                        initialized.completeExceptionally(t);
                    }
                    return q;
                });
        CompletableFuture<@Nullable Void> configurationDoneFuture = CompletableFuture
                .allOf(initialized, capabilitiesFuture).thenRun(() -> monitor.setFraction(30d / 100d));
        if (isDebug) {
            configurationDoneFuture = configurationDoneFuture
                    .thenCompose(v -> {
                        // client sends zero or more setBreakpoints requests
                        monitor.checkCanceled();
                        monitor.setFraction(50d / 100d);
                        monitor.setText2("Sending breakpoints");
                        return debugProcess
                                .getBreakpointHandler()
                                .initialize(getDebugProtocolServer(), this::getCapabilities);
                    })
                    .thenCompose(v -> {
                        // client sends a setFunctionBreakpoints request
                        // if supportsFunctionBreakpoints returns true
                        monitor.checkCanceled();
                        monitor.setFraction(55d / 100d);
                        monitor.setText2("Sending function breakpoints");
                        synchronized (capabilitiesLock) {
                            functionBreakpointsSentWith = getCapabilities();
                            return debugProcess
                                    .getFunctionBreakpointHandler()
                                    .initialize(getDebugProtocolServer(), this::getCapabilities);
                        }
                    })
                    .thenCompose(v -> {
                        // client sends a setDataBreakpoints request
                        // if supportsDataBreakpoints returns true
                        monitor.checkCanceled();
                        monitor.setFraction(57d / 100d);
                        monitor.setText2("Sending data breakpoints");
                        synchronized (capabilitiesLock) {
                            dataBreakpointsSentWith = getCapabilities();
                            return debugProcess
                                    .getDataBreakpointHandler()
                                    .initialize(getDebugProtocolServer(), this::getCapabilities);
                        }
                    })
                    .thenCompose(v -> {
                        // client sends a setExceptionBreakpoints request
                        // if one or more exceptionBreakpointFilters have been defined
                        // (or if supportsConfigurationDoneRequest is not true)
                        monitor.checkCanceled();
                        monitor.setFraction(60d / 100d);
                        monitor.setText2("Sending exception breakpoints filters");

                        synchronized (capabilitiesLock) {
                            exceptionFiltersSentWith = getCapabilities();
                            var filters = exceptionFiltersSentWith.getExceptionBreakpointFilters();
                            if (filters != null) {
                                return debugProcess
                                        .getBreakpointHandler()
                                        .sendExceptionBreakpointFilters(filters, getDebugProtocolServer());
                            }
                            return CompletableFuture.completedFuture(null);
                        }
                    })
                    .thenCompose(v -> {
                        monitor.checkCanceled();
                        monitor.setFraction(70d / 100d);
                        // client sends a setInstructionsBreakpoints request
                        // if supportsDisassembleRequest returns true
                        if (!canDisassemble()) {
                            return CompletableFuture.completedFuture(null);
                        }
                        monitor.setText2("Sending instruction breakpoints");
                        synchronized (capabilitiesLock) {
                            return initializeInstructionBreakpoints();
                        }
                    });
        }
        configurationDoneFuture = configurationDoneFuture
                .thenCompose(v -> {
                    // client sends one configurationDone request to indicate the end of the configuration.
                    monitor.checkCanceled();
                    monitor.setFraction(80d / 100d);
                    monitor.setText2("Sending configuration done");
                    if (Boolean.TRUE.equals(getCapabilities().getSupportsConfigurationDoneRequest())) {
                        return getDebugProtocolServer()
                                .configurationDone(new ConfigurationDoneArguments());
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .thenRun(this::endConfiguration);
        return CompletableFuture.allOf(launchAttachFuture, configurationDoneFuture);
    }

    /**
     * Ends the configuration: what a 'capabilities' event changed after a step of the configuration read the
     * capabilities is sent again now, and what the next events change is sent again as they arrive.
     */
    private void endConfiguration() {
        synchronized (capabilitiesLock) {
            configured = true;
            sendWhatTheCapabilitiesChanged();
        }
    }

    /**
     * Sends again to the DAP server what was sent to it with capabilities which changed since: the function
     * breakpoints, or the data breakpoints, when their support was turned on or off, and the exception breakpoint
     * filters when the filters or their options changed. The instruction breakpoints are sent once the DAP server
     * supports disassembling. Line breakpoints are sent with their conditions and log messages whatever the
     * capabilities, so they are not sent again.
     * <p>
     * Called under {@link #capabilitiesLock}, once the configuration is done.
     */
    private void sendWhatTheCapabilitiesChanged() {
        if (!isDebug) {
            // The configuration sent no breakpoints: the program runs without debugging
            return;
        }
        var server = getDebugProtocolServer();
        Capabilities current = getCapabilities();
        if (isTurnedOnOrOff(functionBreakpointsSentWith, current, Capabilities::getSupportsFunctionBreakpoints)) {
            debugProcess.getFunctionBreakpointHandler().sendBreakpoints(server);
        }
        if (isTurnedOnOrOff(dataBreakpointsSentWith, current, Capabilities::getSupportsDataBreakpoints)) {
            debugProcess.getDataBreakpointHandler().sendBreakpoints(server);
        }
        var filters = current.getExceptionBreakpointFilters();
        if (filters != null && isExceptionFiltersChanged(exceptionFiltersSentWith, current)) {
            sendExceptionBreakpointFilters(filters, server);
        }
        initializeInstructionBreakpoints();
        functionBreakpointsSentWith = current;
        dataBreakpointsSentWith = current;
        exceptionFiltersSentWith = current;
    }

    /**
     * Returns true if the given capability was turned on or off since it was sent with the given capabilities, and
     * false otherwise, or if it was not sent.
     */
    private static boolean isTurnedOnOrOff(@Nullable Capabilities sentWith,
                                           @NotNull Capabilities current,
                                           @NotNull Function<Capabilities, @Nullable Boolean> capability) {
        return sentWith != null
                && Boolean.TRUE.equals(capability.apply(sentWith)) != Boolean.TRUE.equals(capability.apply(current));
    }

    /**
     * Returns true if the exception breakpoint filters which the DAP server offers, or its support of their options,
     * changed since they were sent with the given capabilities, and false otherwise, or if they were not sent.
     * <p>
     * Whether a filter is enabled by default is left out: the user's choice for a filter which was already offered
     * replaces it, and the "Exception Breakpoints" panel keeps that choice in the filter itself.
     *
     * @param sentWith the capabilities which the exception breakpoint filters were sent with, or null.
     * @param current  the current capabilities.
     * @return true if the exception breakpoint filters or their options changed, and false otherwise.
     */
    private static boolean isExceptionFiltersChanged(@Nullable Capabilities sentWith, @NotNull Capabilities current) {
        return sentWith != null
                && (Boolean.TRUE.equals(sentWith.getSupportsExceptionFilterOptions()) != Boolean.TRUE.equals(current.getSupportsExceptionFilterOptions())
                || !describeExceptionFilters(sentWith).equals(describeExceptionFilters(current)));
    }

    private static @NotNull List<List<Object>> describeExceptionFilters(@NotNull Capabilities capabilities) {
        var filters = capabilities.getExceptionBreakpointFilters();
        if (filters == null) {
            // Offers no filter, as an empty array does
            return List.of();
        }
        return Arrays.stream(filters)
                .map(filter -> Arrays.<Object>asList(filter.getFilter(), filter.getLabel(), filter.getDescription(),
                        filter.getSupportsCondition(), filter.getConditionDescription()))
                .toList();
    }

    /**
     * Shows the given exception breakpoint filters in the "Exception Breakpoints" panel, with the user's choices for
     * those which it showed already, and sends those which are enabled to the given DAP server. The panel is changed
     * on the EDT, also while a modal dialog is open, so that the filters take effect at once.
     *
     * @param filters the exception breakpoint filters which the DAP server offers.
     * @param server  the DAP server.
     */
    private void sendExceptionBreakpointFilters(ExceptionBreakpointsFilter @NotNull [] filters,
                                                @NotNull IDebugProtocolServer server) {
        ApplicationManager.getApplication().invokeLater(
                () -> debugProcess.getBreakpointHandler().sendExceptionBreakpointFilters(filters, server),
                ModalityState.any(),
                ignored -> disposed.get());
    }

    /**
     * Initializes the disassembly breakpoint handler for the DAP server, so that it sends the instruction breakpoints
     * to it, once the DAP server supports disassembling. It is initialized once for a DAP server.
     * <p>
     * Called under {@link #capabilitiesLock}.
     *
     * @return the future of the sent instruction breakpoints.
     */
    private CompletableFuture<@Nullable Void> initializeInstructionBreakpoints() {
        if (instructionBreakpointsInitialized || !canDisassemble()) {
            return CompletableFuture.completedFuture(null);
        }
        instructionBreakpointsInitialized = true;
        return debugProcess
                .getDisassemblyBreakpointHandler()
                .initialize(getDebugProtocolServer(), this::getCapabilities);
    }

    public IDebugProtocolServer getDebugProtocolServer() {
        return debugProtocolServer;
    }


    /**
     * Return the Capabilities of the currently attached debug adapter.
     * <p>
     * Clients should not call this method until after the debug adapter has been
     * initialized.
     *
     * @return the current capabilities if they have been retrieved, or else
     * return a default capabilities
     */
    @NotNull
    Capabilities getCapabilities() {
        return adapterCapabilities.get();
    }

    protected Launcher<? extends IDebugProtocolServer> createLauncher(UnaryOperator<MessageConsumer> wrapper,
                                                                      InputStream in, OutputStream out,
                                                                      ExecutorService threadPool) {
        return DSPLauncher.createClientLauncher(this, in, out, threadPool, wrapper);
    }

    @Override
    public void initialized() {
        initialized.complete(null);
    }

    @Override
    public void capabilities(CapabilitiesEventArguments args) {
        synchronized (capabilitiesLock) {
            adapterCapabilities.update(args);
            if (configured) {
                // During the configuration, its next steps read the new capabilities, and it applies them to what its
                // steps before sent once it is done
                sendWhatTheCapabilitiesChanged();
            }
        }
    }

    @Override
    public CompletableFuture<Void> startDebugging(StartDebuggingRequestArguments args) {
        final var parameters = new HashMap<String, Object>(args.getConfiguration());
        DAPClient client = getServerDescriptor().createClient(debugProcess, parameters, isDebug, debugMode, serverTrace, this);
        childrenClient.add(client);
        return client.connectToServer(new EmptyProgressIndicator());
    }

    @Override
    public void process(ProcessEventArguments args) {
        IDebugProtocolClient.super.process(args);
    }

    @Override
    public void exited(ExitedEventArguments args) {
        // The process of the session is the debug adapter's, which ends with its own exit code: keep the program's
        var processHandler = debugProcess.getProcessHandler();
        if (processHandler != null) {
            DAPDebugProcess.keepProgramExitCode(processHandler, args.getExitCode(), parentClient != null);
        }
    }

    @Override
    public void output(OutputEventArguments args) {
        debugProcess.printOutput(args.getOutput(), getContentType(args.getCategory()));
    }

    private static ConsoleViewContentType getContentType(String category) {
        if (category != null) {
            switch (category) {
                case OutputEventArgumentsCategory.STDERR:
                    return ConsoleViewContentType.LOG_ERROR_OUTPUT;
                case OutputEventArgumentsCategory.IMPORTANT:
                    return ConsoleViewContentType.LOG_VERBOSE_OUTPUT;
                case OutputEventArgumentsCategory.TELEMETRY:
                    return ConsoleViewContentType.LOG_DEBUG_OUTPUT;
                default:
                    return ConsoleViewContentType.LOG_INFO_OUTPUT;
            }
        }
        return ConsoleViewContentType.LOG_INFO_OUTPUT;
    }

    @Override
    public CompletableFuture<RunInTerminalResponse> runInTerminal(@NotNull RunInTerminalRequestArguments args) {
        return RunInTerminalManager.getInstance(getProject()).runInTerminal(args, this);
    }

    @Override
    public void breakpoint(BreakpointEventArguments args) {
        if (BreakpointEventArgumentsReason.CHANGED.equals(args.getReason())) {
            var breakpoint = args.getBreakpoint();
            if (breakpoint != null) {
                // breakpoint with a given id changed (ex: verified changed)
                // update the Intellij Breakpoint, a line, a function or a data breakpoint, with those information.
                debugProcess.getBreakpointHandler()
                        .updateBreakpoint(getDebugProtocolServer(), breakpoint, null);
                debugProcess.getFunctionBreakpointHandler()
                        .updateBreakpoint(getDebugProtocolServer(), breakpoint, null);
                debugProcess.getDataBreakpointHandler()
                        .updateBreakpoint(getDebugProtocolServer(), breakpoint, null);
            }
        }
    }

    @Override
    public void stopped(StoppedEventArguments args) {
        // "Run to Cursor" ends at this stop, at its temporary breakpoint or anywhere else
        debugProcess.getRunToCursorBreakpoint().stopped();
        var server = getDebugProtocolServer();
        Integer threadId = args.getThreadId();
        if (server == null || threadId == null) {
            return;
        }
        if (loadedSourcesAndModulesAsked.compareAndSet(false, true)) {
            refreshLoadedSourcesAndModules(server);
        }
        getThreads(true).thenAcceptAsync(threads -> {
            var stoppedThread = Arrays.stream(threads)
                    .filter(thread -> threadId.equals(thread.getId()))
                    .findFirst();
            // A thread which the debug adapter doesn't report doesn't suspend the session
            stoppedThread.ifPresent(thread -> getStackFrames(server, threadId)
                    .thenAcceptAsync(frames -> suspend(server, args, threads, thread, frames)));
        });
    }

    /**
     * Returns the stack frames of the given thread, innermost first, and none if the debug adapter returns none.
     *
     * @param server   the debug adapter.
     * @param threadId the DAP thread.
     * @return the stack frames of the given thread, innermost first.
     */
    private static CompletableFuture<StackFrame[]> getStackFrames(@NotNull IDebugProtocolServer server, int threadId) {
        StackTraceArguments args = new StackTraceArguments();
        args.setThreadId(threadId);
        return server.stackTrace(args)
                .thenApply(response -> response != null && response.getStackFrames() != null ? response.getStackFrames() : new StackFrame[0]);
    }

    /**
     * Suspends the session where the given thread stopped. When a breakpoint stopped it, the session is told which
     * one; otherwise, the session is suspended at the top frame, and an exception which stopped the thread is shown
     * there. What a data breakpoint saw change, as the stopped event describes it, is shown where the program stopped.
     *
     * @param server        the debug adapter.
     * @param args          the stopped event.
     * @param threads       the threads the debug adapter reports.
     * @param stoppedThread the thread which stopped.
     * @param frames        the stack frames of the stopped thread, innermost first.
     */
    private void suspend(@NotNull IDebugProtocolServer server,
                         @NotNull StoppedEventArguments args,
                         org.eclipse.lsp4j.debug.Thread[] threads,
                         org.eclipse.lsp4j.debug.Thread stoppedThread,
                         StackFrame[] frames) {
        if (frames.length == 0) {
            return;
        }
        StackFrame topFrame = frames[0];
        DAPSuspendContext context = getSuspendContext(threads, stoppedThread, frames);
        var breakpointHandler = debugProcess.getBreakpointHandler();
        XBreakpoint<?> breakpoint = getStopBreakpoint(args, id -> debugProcess.findBreakpointById(server, id), () -> breakpointHandler.findBreakpoint(topFrame));
        if (breakpoint != null) {
            breakpointReached(getSession(), server, breakpoint, context, topFrame.getId(), stoppedThread.getId(),
                    singleThreadResumed ? Boolean.TRUE : null)
                    .exceptionally(this::reportBreakpointFailure);
            if (breakpoint.getSuspendPolicy() != SuspendPolicy.NONE) {
                showDataBreakpointHint(getProject(), context, args, DAPClient::showInformationHint);
            }
            return;
        }
        getSession().positionReached(context);
        if (StoppedEventArgumentsReason.EXCEPTION.equals(args.getReason())) {
            showExceptionHint(getProject(), context, server, getCapabilities(), args, DAPClient::showErrorHint);
        } else {
            showDataBreakpointHint(getProject(), context, args, DAPClient::showInformationHint);
        }
    }

    /**
     * Returns whether the debug adapter says that a breakpoint stopped the program. A step, a pause or an exception
     * which stops on the line of a breakpoint doesn't stop at that breakpoint, which would resume the program if it
     * doesn't suspend.
     *
     * @param args the stopped event.
     * @return true if a breakpoint stopped the program and false otherwise.
     */
    static boolean isBreakpointStop(@NotNull StoppedEventArguments args) {
        Integer[] hitBreakpointIds = args.getHitBreakpointIds();
        return StoppedEventArgumentsReason.BREAKPOINT.equals(args.getReason())
                || (hitBreakpointIds != null && hitBreakpointIds.length > 0);
    }

    /**
     * Returns the IntelliJ breakpoint which stopped the program, and null if none did. When the debug adapter names
     * the breakpoints which stopped the program, one which suspends is preferred, and a breakpoint which is not an
     * IntelliJ breakpoint, such as the temporary breakpoint of "Run to Cursor", keeps the program stopped: the program
     * is resumed only when each breakpoint which stopped it doesn't suspend. When it names none, the breakpoint is the
     * one on the line where the program stopped.
     *
     * @param args             the stopped event.
     * @param breakpointsById  returns the IntelliJ breakpoint of a DAP breakpoint id, and null if there is none.
     * @param breakpointOnLine returns the IntelliJ breakpoint on the line where the program stopped, and null if there
     *                         is none.
     * @return the IntelliJ breakpoint which stopped the program, and null if none did.
     */
    static @Nullable XBreakpoint<?> getStopBreakpoint(@NotNull StoppedEventArguments args,
                                                      @NotNull Function<Integer, ? extends @Nullable XBreakpoint<?>> breakpointsById,
                                                      @NotNull Supplier<? extends @Nullable XBreakpoint<?>> breakpointOnLine) {
        if (!isBreakpointStop(args)) {
            return null;
        }
        Integer[] hitBreakpointIds = args.getHitBreakpointIds();
        if (hitBreakpointIds == null || hitBreakpointIds.length == 0) {
            return breakpointOnLine.get();
        }
        XBreakpoint<?> stopBreakpoint = null;
        boolean otherBreakpoint = false;
        for (Integer id : hitBreakpointIds) {
            XBreakpoint<?> breakpoint = breakpointsById.apply(id);
            if (breakpoint == null) {
                otherBreakpoint = true;
            } else if (breakpoint.getSuspendPolicy() != SuspendPolicy.NONE) {
                return breakpoint;
            } else if (stopBreakpoint == null) {
                stopBreakpoint = breakpoint;
            }
        }
        return otherBreakpoint ? null : stopBreakpoint;
    }

    /**
     * Returns the session's suspend context, or a new one if the session is not suspended, with an execution stack
     * per thread: the stopped thread's has the frames it stopped in, and the others' are computed when shown.
     */
    private @NotNull DAPSuspendContext getSuspendContext(org.eclipse.lsp4j.debug.Thread[] threads,
                                                        org.eclipse.lsp4j.debug.Thread stoppedThread,
                                                        StackFrame[] frames) {
        var context = getSession().getSuspendContext() instanceof DAPSuspendContext suspended ? suspended : new DAPSuspendContext(this);
        for (var thread : threads) {
            context.addToExecutionStack(thread, thread.equals(stoppedThread) ? frames : null);
        }
        return context;
    }

    /**
     * Shows the error hint of an exception stop where the program stopped, in an editor of the top frame's file,
     * with the text of {@link #getExceptionHint(IDebugProtocolServer, Capabilities, StoppedEventArguments)}. When no
     * editor shows the file, nothing is asked of the debug adapter.
     *
     * @param project      the project.
     * @param context      the suspend context of the stop.
     * @param server       the debug adapter.
     * @param capabilities the debug adapter capabilities.
     * @param args         the stopped event, with the reason 'exception'.
     * @param display      shows the text of the hint in the editor, at the offset where the program stopped.
     * @return the future, completed once the text of the hint is known and its display scheduled.
     */
    static CompletableFuture<@Nullable Void> showExceptionHint(@NotNull Project project,
                                                               @NotNull XSuspendContext context,
                                                               @NotNull IDebugProtocolServer server,
                                                               @NotNull Capabilities capabilities,
                                                               @NotNull StoppedEventArguments args,
                                                               @NotNull HintDisplay display) {
        return showStopHint(project, context, () -> getExceptionHint(server, capabilities, args), display);
    }

    /**
     * Shows the hint of a data breakpoint stop where the program stopped, in an editor of the top frame's file, with
     * the stopped event's description of what changed, such as {@code Counter.count changed from 0 to 2}. A stop of
     * another reason, or with no description, shows nothing.
     *
     * @param project the project.
     * @param context the suspend context of the stop.
     * @param args    the stopped event.
     * @param display shows the text of the hint in the editor, at the offset where the program stopped.
     * @return the future, completed once the display of the hint is scheduled, if there is one.
     */
    static CompletableFuture<@Nullable Void> showDataBreakpointHint(@NotNull Project project,
                                                                    @NotNull XSuspendContext context,
                                                                    @NotNull StoppedEventArguments args,
                                                                    @NotNull HintDisplay display) {
        String description = args.getDescription();
        if (!StoppedEventArgumentsReason.DATA_BREAKPOINT.equals(args.getReason()) || StringUtils.isBlank(description)) {
            return CompletableFuture.completedFuture(null);
        }
        return showStopHint(project, context, () -> CompletableFuture.completedFuture(description), display);
    }

    /**
     * Shows a hint where the program stopped, in an editor of the top frame's file, with the text which the given
     * supplier computes. When no editor shows the file, the text is not computed.
     */
    private static CompletableFuture<@Nullable Void> showStopHint(@NotNull Project project,
                                                                  @NotNull XSuspendContext context,
                                                                  @NotNull Supplier<CompletableFuture<String>> hintText,
                                                                  @NotNull HintDisplay display) {
        var stack = context.getActiveExecutionStack();
        var topFrame = stack != null ? stack.getTopFrame() : null;
        var position = topFrame != null ? topFrame.getSourcePosition() : null;
        if (position == null) {
            return CompletableFuture.completedFuture(null);
        }
        Editor[] editors = LSPIJUtils.editorsForFile(position.getFile(), project);
        if (editors == null || editors.length == 0) {
            return CompletableFuture.completedFuture(null);
        }
        Editor editor = editors[0];
        int offset = position.getOffset();
        // Wait briefly to be sure that scroll of the editor
        // has occurred before computing the error hint position.
        // TODO: remove this timer by detecting that scroll editor is finished
        // to display the error hint
        return hintText.get()
                .thenAccept(hint -> AppExecutorUtil.getAppScheduledExecutorService().schedule(
                        () -> ApplicationManager.getApplication().invokeLater(() -> display.show(editor, hint, offset)),
                        1, TimeUnit.SECONDS));
    }

    /**
     * Shows the text of a hint in an editor, at an offset.
     */
    @FunctionalInterface
    interface HintDisplay {
        void show(@NotNull Editor editor, @NotNull String text, int offset);
    }

    /**
     * Returns the text of the error hint shown where the program stopped at an exception. When the debug adapter
     * supports the 'exceptionInfo' request, the text has the exception's id, its description and the first lines
     * of its stack trace; otherwise, or if the request fails, it has the stopped event's text and description.
     *
     * @param server       the debug adapter.
     * @param capabilities the debug adapter capabilities.
     * @param args         the stopped event, with the reason 'exception'.
     * @return the text of the error hint.
     */
    static CompletableFuture<String> getExceptionHint(@NotNull IDebugProtocolServer server,
                                                      @NotNull Capabilities capabilities,
                                                      @NotNull StoppedEventArguments args) {
        String stoppedHint = getExceptionHint(args.getText(), args.getDescription(), null);
        if (!Boolean.TRUE.equals(capabilities.getSupportsExceptionInfoRequest()) || args.getThreadId() == null) {
            return CompletableFuture.completedFuture(stoppedHint);
        }
        ExceptionInfoArguments exceptionInfoArgs = new ExceptionInfoArguments();
        exceptionInfoArgs.setThreadId(args.getThreadId());
        return server.exceptionInfo(exceptionInfoArgs)
                // a debug adapter that doesn't answer must not keep the hint from being shown
                .orTimeout(EXCEPTION_INFO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((info, error) -> {
                    if (error != null || info == null) {
                        return stoppedHint;
                    }
                    var details = info.getDetails();
                    String exception = StringUtils.isNotBlank(info.getExceptionId()) || details == null ?
                            info.getExceptionId() : details.getTypeName();
                    return getExceptionHint(exception, info.getDescription(), details != null ? details.getStackTrace() : null);
                });
    }

    private static String getExceptionHint(@Nullable String exception,
                                            @Nullable String description,
                                            @Nullable String stackTrace) {
        StringBuilder hint = new StringBuilder("Exception has occurred: ");
        hint.append(exception);
        if (StringUtils.isNotBlank(description)) {
            hint.append("\n");
            hint.append(description);
        }
        if (StringUtils.isNotBlank(stackTrace)) {
            // The hint shows where the exception comes from, not its whole stack
            var lines = stackTrace.strip().lines().toList();
            lines.stream()
                    .limit(MAX_EXCEPTION_HINT_STACK_LINES)
                    .forEach(line -> hint.append("\n").append(line));
            if (lines.size() > MAX_EXCEPTION_HINT_STACK_LINES) {
                hint.append("\n...");
            }
        }
        return hint.toString();
    }

    private @Nullable Void reportBreakpointFailure(@NotNull Throwable error) {
        // e.g. the program could not be resumed: say so instead of leaving it stopped silently
        debugProcess.print("Cannot handle the breakpoint: " + getCause(error).getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
        return null;
    }

    static @NotNull Throwable getCause(@NotNull Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /**
     * Tells the session that the program stopped at the given breakpoint, with the value of the breakpoint's
     * "Evaluate and log" expression in the stopped frame, and resumes the program if the breakpoint doesn't suspend.
     * <p>
     * A breakpoint which doesn't suspend is sent as a logpoint, so a debug adapter which supports logpoints doesn't
     * stop at it; one which doesn't support them stops, and is resumed here once IntelliJ has logged.
     *
     * @param session      the debug session.
     * @param server       the debug adapter.
     * @param breakpoint   the IntelliJ breakpoint where the program stopped.
     * @param context      the suspend context of the stop.
     * @param frameId      the DAP frame where the program stopped.
     * @param threadId     the DAP thread which stopped.
     * @param singleThread true to resume only the stopped thread, when a step resumed it alone, and null to resume
     *                     every thread.
     * @return the future, completed once the session is told and the program resumed if it must be.
     */
    static CompletableFuture<@Nullable Void> breakpointReached(@NotNull XDebugSession session,
                                                               @NotNull IDebugProtocolServer server,
                                                               @NotNull XBreakpoint<?> breakpoint,
                                                               @NotNull XSuspendContext context,
                                                               int frameId,
                                                               int threadId,
                                                               @Nullable Boolean singleThread) {
        return evaluateLogExpression(server, breakpoint, frameId)
                .thenCompose(logged -> {
                    if (session.breakpointReached(breakpoint, logged, context)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    // The breakpoint doesn't suspend: resume the program
                    ContinueArguments args = new ContinueArguments();
                    args.setThreadId(threadId);
                    args.setSingleThread(singleThread);
                    return server.continue_(args).thenAccept(response -> {
                    });
                });
    }

    private static CompletableFuture<@Nullable String> evaluateLogExpression(@NotNull IDebugProtocolServer server,
                                                                             @NotNull XBreakpoint<?> breakpoint,
                                                                             int frameId) {
        var logExpression = breakpoint.getLogExpressionObject();
        if (logExpression == null || StringUtils.isBlank(logExpression.getExpression())) {
            return CompletableFuture.completedFuture(null);
        }
        String expression = logExpression.getExpression();
        EvaluateArguments args = new EvaluateArguments();
        args.setExpression(expression);
        args.setFrameId(frameId);
        args.setContext(EvaluateArgumentsContext.WATCH);
        return server.evaluate(args)
                .handle((response, error) -> {
                    if (error != null) {
                        return "Cannot evaluate '" + expression + "': " + getCause(error).getMessage();
                    }
                    return response != null ? response.getResult() : null;
                });
    }

    static void showErrorHint(@NotNull Editor editor, @NotNull String text, int offset) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return;
        }
        String html = toHintHtml(text);
        if (ApplicationManager.getApplication().isDispatchThread()) {
            doShowErrorHint(editor, html, offset);
        } else {
            ApplicationManager.getApplication()
                    .invokeLater(() -> doShowErrorHint(editor, html, offset));
        }
    }

    /**
     * Returns the text of an error hint as the HTML the hint shows: its lines on lines of their own, and
     * characters such as {@code <} as themselves.
     *
     * @param text the text of the error hint.
     * @return the HTML of the error hint.
     */
    static @NotNull String toHintHtml(@NotNull String text) {
        return StringUtil.escapeXmlEntities(text).replace("\n", "<br>");
    }

    static void doShowErrorHint(@NotNull Editor editor, @NotNull String hintText, int offset) {
        if (editor.isDisposed()) {
            // The editor was closed between the stop and the hint
            return;
        }
        doShowHint(editor, HintUtil.createErrorLabel(hintText), offset);
    }

    static void showInformationHint(@NotNull Editor editor, @NotNull String text, int offset) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return;
        }
        String html = toHintHtml(text);
        if (ApplicationManager.getApplication().isDispatchThread()) {
            doShowInformationHint(editor, html, offset);
        } else {
            ApplicationManager.getApplication()
                    .invokeLater(() -> doShowInformationHint(editor, html, offset));
        }
    }

    private static void doShowInformationHint(@NotNull Editor editor, @NotNull String hintText, int offset) {
        if (editor.isDisposed()) {
            // The editor was closed between the stop and the hint
            return;
        }
        doShowHint(editor, HintUtil.createInformationLabel(hintText), offset);
    }

    private static void doShowHint(@NotNull Editor editor, @NotNull JComponent label, int offset) {
        LightweightHint hint = new LightweightHint(label);
        final VisualPosition pos1 = editor.offsetToVisualPosition(offset);
        final Point p = getHintPosition(hint, editor, pos1, HintManager.DEFAULT);
        int hintFlags = HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_ESCAPE | HintManager.UPDATE_BY_SCROLLING;
        HintManagerImpl.getInstanceImpl()
                .showEditorHint(hint, editor, p, hintFlags, 50000, false);
    }


    @Override
    public void terminated(TerminatedEventArguments args) {
        // TODO : manage args.getRestart()
        endSession();
    }

    /**
     * Ends this session, as its 'terminated' event or the end of its connection says: stops the debug process unless
     * another session of the tree is still alive.
     */
    private void endSession() {
        var server = getDebugProtocolServer();
        if (server != null) {
            debugProcess.getBreakpointHandler().forgetSession(server);
            debugProcess.getFunctionBreakpointHandler().forgetSession(server);
            debugProcess.getDataBreakpointHandler().forgetSession(server);
        }
        // A debug adapter may run a session per target, as vscode-js-debug does with 'startDebugging': when an iframe or
        // a worker goes away only its session terminates, and the debug process must go on for the others
        terminatedReceived = true;
        DAPClient root = this;
        while (root.parentClient != null) {
            root = root.parentClient;
        }
        if (parentClient != null) {
            // In one list or the other at any time, so that a parent being disposed meanwhile disposes it
            parentClient.terminatedChildren.add(this);
            parentClient.childrenClient.remove(this);
        }
        if (root.hasLiveSession()) {
            return;
        }
        root.debugProcess.stop();
    }

    /**
     * Returns true when this session, or a session below it, has not sent 'terminated' yet.
     */
    private boolean hasLiveSession() {
        if (!terminatedReceived) {
            return true;
        }
        for (DAPClient child : childrenClient) {
            if (child.hasLiveSession()) {
                return true;
            }
        }
        // A terminated child may still have live children of its own
        for (DAPClient child : terminatedChildren) {
            if (child.hasLiveSession()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ends this session when its connection closes without a 'terminated' event, as when the debug adapter crashed or
     * was killed, or when the socket of a child session dropped: otherwise the session would count as alive for good,
     * and keep the debug process running and the breakpoints it bound shown as verified. When the client is ending the
     * session itself, it disposes the client instead, since the answer which terminate() waits for won't come.
     *
     * @param listening the future of the message loop, which ends when the connection closes.
     */
    private void endWhenTheConnectionCloses(@NotNull Future<Void> listening) {
        threadPool.execute(() -> {
            try {
                listening.get();
            } catch (InterruptedException e) {
                java.lang.Thread.currentThread().interrupt();
                return;
            } catch (CancellationException | ExecutionException e) {
                // Cancelled by dispose(), or the connection failed: it is gone either way
            }
            if (isEnding()) {
                // terminate() disposes the client once the DAP server answers 'terminate' or 'disconnect', which it
                // can't do now: the debug adapter exited or crashed meanwhile
                dispose();
                return;
            }
            if (terminatedReceived) {
                return;
            }
            debugProcess.print(DAPBundle.message("dap.session.connection.closed"), ConsoleViewContentType.SYSTEM_OUTPUT);
            endSession();
        });
    }

    /**
     * Returns true once the connection to the DAP server has closed, or dispose() has cancelled it.
     */
    private boolean isConnectionClosed() {
        return debugProtocolFuture != null && debugProtocolFuture.isDone();
    }

    /**
     * Returns true when the client ends this session, or a session above it, with terminate() or dispose().
     */
    private boolean isEnding() {
        for (DAPClient client = this; client != null; client = client.parentClient) {
            if (client.ending) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void dispose() {
        ending = true;
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        if (debugProtocolFuture != null) {
            CancellationSupport.cancel(debugProtocolFuture);

            /*
             * If the debugProtocolFuture is running the message loop on the same thread we
             * are in currently, the cancel call will set the interrupt flag. That isn't
             * necessary as the message loop isn't waiting on anything (since this code is
             * running). The interrupt flag may affect future processing, like terminating
             * processes. Therefore clear the flag. See lsp4e#264
             */
            java.lang.Thread.interrupted();
        }
        if (transportStreams != null) {
            transportStreams.close();
        }
        RunInTerminalManager.getInstance(getProject()).releaseClientTerminals(this);
        threadPool.shutdown();
        for (DAPClient child : childrenClient) {
            child.dispose();
        }
        for (DAPClient child : terminatedChildren) {
            child.dispose();
        }
    }

    /**
     * This implementation follows the "Debug session end" guidelines in
     * https://microsoft.github.io/debug-adapter-protocol/overview:
     * <p>
     * "When the development tool ends a debug session, the sequence of events is
     * slightly different based on whether the session has been initially 'launched'
     * or 'attached':
     * <p>
     * Debuggee launched: if a debug adapter supports the terminate request, the
     * development tool uses it to terminate the debuggee gracefully, i.e. it gives
     * the debuggee a chance to cleanup everything before terminating. If the
     * debuggee does not terminate but continues to run (or hits a breakpoint), the
     * debug session will continue, but if the development tool tries again to
     * terminate the debuggee, it will then use the disconnect request to end the
     * debug session unconditionally. The disconnect request is expected to
     * terminate the debuggee (and any child processes) forcefully.
     * <p>
     * Debuggee attached: If the debuggee has been 'attached' initially, the
     * development tool issues a disconnect request. This should detach the debugger
     * from the debuggee but will allow it to continue."
     */
    public void terminate() {
        ending = true;
        IDebugProtocolServer server = getDebugProtocolServer();
        if (server == null || isConnectionClosed()) {
            // A request over a closed connection would never be answered, and the client never disposed
            dispose();
            return;
        }

        boolean shouldSendTerminateRequest = !sentTerminateRequest
                && isSupportsTerminateRequest()
                && debugMode == DebugMode.LAUNCH;
        if (shouldSendTerminateRequest) {
            sentTerminateRequest = true;
            server.terminate(new TerminateArguments())
                    .thenRunAsync(this::dispose);
        } else {
            final var arguments = new DisconnectArguments();
            // Here we don't set terminateDebuggee to true otherwise, the debuggee (ex: debugpy) is terminated.
            // According to the 'setTerminateDebuggee' specification:
            /**
             * Indicates whether the debuggee should be terminated when the debugger is
             * disconnected.
             * If unspecified, the debug adapter is free to do whatever it thinks is best.
             * The attribute is only honored by a debug adapter if the corresponding
             * capability `supportTerminateDebuggee` is true.
             */
            // terminateDebuggee?: boolean;
            // arguments.setTerminateDebuggee(true);
            // TODO: manage this 'terminateDebuggee' with an UI settings
            // to provide the capability to terminate the debuggee when user click on stop button.
            server.disconnect(arguments)
                    .thenRunAsync(this::dispose);
        }
    }

    XDebugSession getSession() {
        return debugProcess.getSession();
    }

    public CompletableFuture<ContinueResponse> continue_(int threadId) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(null);
        }
        ContinueArguments args = new ContinueArguments();
        args.setThreadId(threadId);
        singleThreadResumed = false;
        return debugProtocolServer
                .continue_(args);
    }

    public void next(int threadId, @Nullable SteppingGranularity granularity) {
        var server = debugProtocolServer;
        if (server == null) {
            return;
        }
        var singleThread = singleThreadStep();
        singleThreadResumed = singleThread != null;
        next(server, threadId, granularity, singleThread);
    }

    /**
     * Sends 'next', Step Over, for the given thread.
     *
     * @param server       the DAP server.
     * @param threadId     the thread which steps.
     * @param granularity  the granularity of the step, or null for the DAP server's default.
     * @param singleThread true to resume only the thread which steps, and null to resume every thread.
     */
    static void next(@NotNull IDebugProtocolServer server,
                     int threadId,
                     @Nullable SteppingGranularity granularity,
                     @Nullable Boolean singleThread) {
        NextArguments args = new NextArguments();
        args.setThreadId(threadId);
        args.setGranularity(granularity);
        args.setSingleThread(singleThread);
        server.next(args);
    }

    public void stepOut(int threadId, SteppingGranularity granularity) {
        var server = debugProtocolServer;
        if (server == null) {
            return;
        }
        var singleThread = singleThreadStep();
        singleThreadResumed = singleThread != null;
        stepOut(server, threadId, granularity, singleThread);
    }

    /**
     * Sends 'stepOut', Step Out, for the given thread.
     *
     * @param server       the DAP server.
     * @param threadId     the thread which steps.
     * @param granularity  the granularity of the step, or null for the DAP server's default.
     * @param singleThread true to resume only the thread which steps, and null to resume every thread.
     */
    static void stepOut(@NotNull IDebugProtocolServer server,
                        int threadId,
                        @Nullable SteppingGranularity granularity,
                        @Nullable Boolean singleThread) {
        StepOutArguments args = new StepOutArguments();
        args.setThreadId(threadId);
        args.setGranularity(granularity);
        args.setSingleThread(singleThread);
        server.stepOut(args);
    }

    public void stepIn(int threadId, SteppingGranularity granularity) {
        stepIn(threadId, null, granularity);
    }

    public void stepIn(int threadId, @Nullable Integer targetId, @Nullable SteppingGranularity granularity) {
        var server = debugProtocolServer;
        if (server == null) {
            return;
        }
        var singleThread = singleThreadStep();
        singleThreadResumed = singleThread != null;
        stepIn(server, threadId, targetId, granularity, singleThread);
    }

    /**
     * Sends 'stepIn', Step Into, or Smart Step Into with a target, for the given thread.
     *
     * @param server       the DAP server.
     * @param threadId     the thread which steps.
     * @param targetId     the target to step into, from 'stepInTargets', or null.
     * @param granularity  the granularity of the step, or null for the DAP server's default.
     * @param singleThread true to resume only the thread which steps, and null to resume every thread.
     */
    static void stepIn(@NotNull IDebugProtocolServer server,
                       int threadId,
                       @Nullable Integer targetId,
                       @Nullable SteppingGranularity granularity,
                       @Nullable Boolean singleThread) {
        StepInArguments args = new StepInArguments();
        args.setThreadId(threadId);
        args.setTargetId(targetId);
        args.setGranularity(granularity);
        args.setSingleThread(singleThread);
        server.stepIn(args);
    }

    /**
     * Returns the value of a step request's 'singleThread': true when the user chose to step only the current thread
     * and the DAP server supports single thread execution requests, and null, which leaves it out, otherwise.
     */
    private @Nullable Boolean singleThreadStep() {
        return singleThreadStep(debugProcess.isStepOnlyCurrentThread(), getCapabilities());
    }

    /**
     * Returns the value of a step request's 'singleThread'.
     *
     * @param stepOnlyCurrentThread true if the user chose to step only the current thread.
     * @param capabilities          the capabilities of the DAP server.
     * @return true when the user chose to step only the current thread and the DAP server supports single thread
     * execution requests, and null otherwise.
     */
    static @Nullable Boolean singleThreadStep(boolean stepOnlyCurrentThread, @NotNull Capabilities capabilities) {
        return stepOnlyCurrentThread && Boolean.TRUE.equals(capabilities.getSupportsSingleThreadExecutionRequests())
                ? Boolean.TRUE : null;
    }

    /**
     * Pauses the debuggee. The 'pause' request pauses a thread, while IntelliJ pauses a whole debug session, in which
     * no thread is selected while it runs: like VS Code when no thread is focused, this pauses the first thread the
     * debug adapter reports. It does the same for each child session started with 'startDebugging', which may be the
     * one that runs the program. The adapter's 'stopped' event then suspends the session.
     *
     * @return a future that completes once the 'pause' requests have been answered.
     */
    public CompletableFuture<Void> pause() {
        var requests = new ArrayList<CompletableFuture<Void>>();
        var server = debugProtocolServer;
        if (server != null) {
            requests.add(pauseFirstThread(server));
        }
        for (DAPClient child : List.copyOf(childrenClient)) {
            requests.add(child.pause());
        }
        return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new));
    }

    /**
     * Sends 'pause' for the first thread the debug adapter reports, if it reports any.
     */
    static CompletableFuture<Void> pauseFirstThread(@NotNull IDebugProtocolServer server) {
        return server.threads()
                .thenCompose(response -> {
                    var threads = response != null ? response.getThreads() : null;
                    if (threads == null || threads.length == 0) {
                        return CompletableFuture.completedFuture(null);
                    }
                    PauseArguments args = new PauseArguments();
                    args.setThreadId(threads[0].getId());
                    return server.pause(args);
                });
    }

    public CompletableFuture<StepInTargetsResponse> stepInTargets(int frameId) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(null);
        }
        StepInTargetsArguments args = new StepInTargetsArguments();
        args.setFrameId(frameId);
        return debugProtocolServer.stepInTargets(args);
    }

    /**
     * What the debug console shows for a typed line that it cannot send, since the debug session is not connected to
     * the debug adapter yet.
     */
    public static final String CONSOLE_LINE_NOT_SENT = "The line was not sent: the debug adapter is not connected yet.";

    public CompletableFuture<EvaluateResponse> evaluate(@NotNull String expression,
                                                        @Nullable Integer frameId,
                                                        @NotNull String context) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(null);
        }
        return evaluate(debugProtocolServer, expression, frameId, context, getValueFormat());
    }

    private static CompletableFuture<EvaluateResponse> evaluate(@NotNull IDebugProtocolServer server,
                                                                @NotNull String expression,
                                                                @Nullable Integer frameId,
                                                                @NotNull String context,
                                                                @Nullable ValueFormat format) {
        EvaluateArguments args = new EvaluateArguments();
        args.setExpression(expression);
        args.setFrameId(frameId);
        args.setContext(context);
        args.setFormat(format);
        return server.evaluate(args);
    }

    /**
     * Evaluates a line typed in the debug console, as VS Code's Debug Console does: an evaluate request with the
     * context repl, in the given stack frame when the program is stopped. A debug adapter which takes a running
     * program's input over the debug connection can take the line as the program's input.
     *
     * @param line    the typed line, without its line end.
     * @param frameId the selected stack frame, or null when the program runs.
     * @return the text the console shows for the line: its result, or null when there is none, as when the program
     * took the line as its input; it fails with the debug adapter's error, or with {@link #CONSOLE_LINE_NOT_SENT}
     * when the client is not connected to the debug adapter yet.
     */
    public CompletableFuture<@Nullable String> evaluateConsoleLine(@NotNull String line, @Nullable Integer frameId) {
        return evaluateConsoleLine(debugProtocolServer, line, frameId, getValueFormat());
    }

    static CompletableFuture<@Nullable String> evaluateConsoleLine(@Nullable IDebugProtocolServer server,
                                                                   @NotNull String line,
                                                                   @Nullable Integer frameId,
                                                                   @Nullable ValueFormat format) {
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(CONSOLE_LINE_NOT_SENT));
        }
        return evaluate(server, line, frameId, EvaluateArgumentsContext.REPL, format)
                .thenApply(response -> response != null && !StringUtils.isBlank(response.getResult()) ?
                        response.getResult() : null);
    }

    public CompletableFuture<CompletionsResponse> completion(@NotNull String text,
                                                             int line,
                                                             int column,
                                                             int frameId) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletionsArguments args = new CompletionsArguments();
        args.setText(text);
        args.setLine(line);
        args.setColumn(column);
        args.setFrameId(frameId);
        return debugProtocolServer.completions(args);
    }

    public CompletableFuture<SetVariableResponse> setVariable(String name,
                                                              int variablesReference,
                                                              String value) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(null);
        }
        SetVariableArguments args = new SetVariableArguments();
        args.setName(name);
        args.setVariablesReference(variablesReference);
        args.setValue(value);
        ValueFormat format = getValueFormat();
        args.setFormat(format != null ? format : new ValueFormat());
        return debugProtocolServer.setVariable(args);
    }

    // Threads

    @Override
    public void thread(ThreadEventArguments args) {
        debugProcess.refreshThread(args);
    }

    // Loaded sources and modules

    @Override
    public void loadedSource(LoadedSourceEventArguments args) {
        debugProcess.updateLoadedSource(this, args);
    }

    @Override
    public void module(ModuleEventArguments args) {
        debugProcess.updateModule(this, args);
    }

    /**
     * Shows the sources and modules which the DAP server reports, if it reports them, as the program first stopped.
     *
     * @param server the debug adapter.
     */
    private void refreshLoadedSourcesAndModules(@NotNull IDebugProtocolServer server) {
        getLoadedSources(server, getCapabilities()).thenAccept(sources -> {
            if (sources != null) {
                debugProcess.refreshLoadedSources(this, sources);
            }
        });
        getModules(server, getCapabilities()).thenAccept(modules -> {
            if (modules != null) {
                debugProcess.refreshModules(this, modules);
            }
        });
    }

    /**
     * Returns the sources which the DAP server reports as loaded, or null when it doesn't report them
     * (supportsLoadedSourcesRequest) or the request fails.
     *
     * @param server       the debug adapter.
     * @param capabilities the debug adapter's capabilities.
     * @return the loaded sources, or null.
     */
    static CompletableFuture<Source @Nullable []> getLoadedSources(@NotNull IDebugProtocolServer server,
                                                                   @NotNull Capabilities capabilities) {
        if (!Boolean.TRUE.equals(capabilities.getSupportsLoadedSourcesRequest())) {
            return CompletableFuture.completedFuture(null);
        }
        return server.loadedSources(new LoadedSourcesArguments())
                .thenApply(response -> response != null && response.getSources() != null ? response.getSources() : new Source[0])
                .exceptionally(error -> null);
    }

    /**
     * Returns the modules which the DAP server reports, or null when it doesn't report them (supportsModulesRequest) or
     * the request fails.
     *
     * @param server       the debug adapter.
     * @param capabilities the debug adapter's capabilities.
     * @return the modules, or null.
     */
    static CompletableFuture<Module @Nullable []> getModules(@NotNull IDebugProtocolServer server,
                                                             @NotNull Capabilities capabilities) {
        if (!Boolean.TRUE.equals(capabilities.getSupportsModulesRequest())) {
            return CompletableFuture.completedFuture(null);
        }
        return server.modules(new ModulesArguments())
                .thenApply(response -> response != null && response.getModules() != null ? response.getModules() : new Module[0])
                .exceptionally(error -> null);
    }

    public CompletableFuture<org.eclipse.lsp4j.debug.Thread[]> getThreads(boolean refreshThreads) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(EMPTY_THREADS);
        }
        var result = debugProtocolServer
                .threads()
                .thenApply(response -> {
                    if (response == null) {
                        return EMPTY_THREADS;
                    }
                    return response.getThreads();
                });
        if (refreshThreads) {
            result.thenAccept(threads -> {
                debugProcess.refreshThreads(threads);
            });
        }
        return result;
    }

    // Invalidated

    @Override
    public void invalidated(InvalidatedEventArguments args) {
        invalidated(getSession(), args, () -> getThreads(true), this::reloadStackFrames);
    }

    /**
     * Shows again what the debug adapter says is out of date, such as after an evaluation that changed the program
     * while it is stopped: the threads, the stacks, the variables, or, when the event names no area, all of them.
     * IntelliJ asks the debug adapter for variables each time its views show them, so rebuilding the views shows
     * the new values; the stack frames are kept from the stop, so they are loaded again first.
     *
     * @param session        the debug session
     * @param args           the invalidated event's arguments
     * @param refreshThreads refreshes the threads panel
     * @param reloadStacks   loads again the stack frames of the stopped thread with the given ID, or of every stopped
     *                       thread for null
     * @return a future that completes once the views are rebuilt, if they have to be
     */
    static CompletableFuture<Void> invalidated(@NotNull XDebugSession session,
                                               @NotNull InvalidatedEventArguments args,
                                               @NotNull Runnable refreshThreads,
                                               @NotNull Function<Integer, CompletableFuture<Void>> reloadStacks) {
        // the event may name an area more than once
        List<String> areas = args.getAreas() != null ? Arrays.asList(args.getAreas()) : List.of();
        boolean all = areas.isEmpty() || areas.contains(InvalidatedAreas.ALL);
        if (all || areas.contains(InvalidatedAreas.THREADS)) {
            refreshThreads.run();
        }

        boolean stacks = all || areas.contains(InvalidatedAreas.STACKS);
        if (!session.isSuspended() || !stacks && !areas.contains(InvalidatedAreas.VARIABLES)) {
            // a running program shows no stacks or variables
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> reloaded = stacks ? reloadStacks.apply(args.getThreadId()) : CompletableFuture.completedFuture(null);
        return reloaded
                .exceptionally(error -> {
                    // the other areas are shown again even so
                    LOGGER.warn("Error while loading the stack frames again after a DAP invalidated event", error);
                    return null;
                })
                .thenRun(session::rebuildViews);
    }

    /**
     * Loads again the stack frames of the stopped thread with the given ID, or of every stopped thread for null.
     */
    private CompletableFuture<Void> reloadStackFrames(@Nullable Integer threadId) {
        if (!(getSession().getSuspendContext() instanceof DAPSuspendContext context)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(Arrays.stream(context.getExecutionStacks())
                .map(DAPExecutionStack.class::cast)
                .filter(stack -> threadId == null || stack.getThreadId() == threadId)
                .map(DAPExecutionStack::reloadStackFrames)
                .toArray(CompletableFuture[]::new));
    }

    // Capabilities

    /**
     * Returns true if the debug adapter supports the 'terminate' request and false otherwise.
     *
     * @return true if the debug adapter supports the 'terminate' request and false otherwise.
     */
    public boolean isSupportsTerminateRequest() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsTerminateRequest());
    }

    /**
     * Returns true if the debug adapter supports the 'completions' request and false otherwise.
     *
     * @return true if the debug adapter supports the 'completions' request and false otherwise.
     */
    public boolean isSupportsCompletionsRequest() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsCompletionsRequest());
    }

    /**
     * Returns true if the debug adapter supports setting a variable to a value and false otherwise.
     *
     * @return true if the debug adapter supports setting a variable to a value and false otherwise.
     */
    public boolean isSupportsSetVariable() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsSetVariable());
    }

    /**
     * Returns true if the debug adapter supports evaluate request for data hovers and false otherwise.
     *
     * @return true if the debug adapter supports evaluate request for data hovers and false otherwise.
     */
    public boolean isSupportsEvaluateForHovers() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsEvaluateForHovers());
    }

    /**
     * Returns true if the debug adapter supports the 'stepInTargets' request and false otherwise.
     *
     * @return true if the debug adapter supports the 'stepInTargets' request and false otherwise.
     */
    public boolean isSupportsStepInTargetsRequest() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsStepInTargetsRequest());
    }

    /**
     * Returns true if the debug adapter supports value formatting options and false otherwise.
     *
     * @return true if the debug adapter supports value formatting options and false otherwise.
     */
    public boolean isSupportsValueFormattingOptions() {
        return DAPValueFormat.isSupported(getCapabilities());
    }

    /**
     * Returns true if the debug adapter, or that of a child session started with 'startDebugging', supports value
     * formatting options, and false otherwise. A child session may be the one which runs the program, and its
     * requests ask for values in the format of the debug session.
     *
     * @return true if the debug adapter, or that of a child session, supports value formatting options, and false
     * otherwise.
     */
    /**
     * Returns true if the DAP server supports single thread execution requests, whose 'singleThread' resumes only the
     * thread of a step, and false otherwise.
     *
     * @return true if the DAP server supports single thread execution requests, and false otherwise.
     */
    public boolean isSupportsSingleThreadExecutionRequests() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsSingleThreadExecutionRequests());
    }

    /**
     * Returns true if the DAP server of this client, or that of a child session, supports single thread execution
     * requests, and false otherwise.
     *
     * @return true if the DAP server of this client, or that of a child session, supports single thread execution
     * requests, and false otherwise.
     */
    public boolean isSupportsSingleThreadExecutionRequestsInSessions() {
        if (isSupportsSingleThreadExecutionRequests()) {
            return true;
        }
        for (DAPClient child : List.copyOf(childrenClient)) {
            if (child.isSupportsSingleThreadExecutionRequestsInSessions()) {
                return true;
            }
        }
        return false;
    }

    public boolean isSupportsValueFormattingOptionsInSessions() {
        if (isSupportsValueFormattingOptions()) {
            return true;
        }
        for (DAPClient child : List.copyOf(childrenClient)) {
            if (child.isSupportsValueFormattingOptionsInSessions()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the format in which values are asked for, as the user chose it in the Variables view, and null for the
     * debug adapter's own format.
     *
     * @return the format in which values are asked for, and null for the debug adapter's own format.
     */
    public @Nullable ValueFormat getValueFormat() {
        return debugProcess.getValueFormat().toValueFormat(getCapabilities());
    }

    public boolean canDisassemble() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsDisassembleRequest());
    }

    /**
     * Returns true if the debug adapter supports data breakpoints and false otherwise.
     *
     * @return true if the debug adapter supports data breakpoints and false otherwise.
     */
    public boolean isSupportsDataBreakpoints() {
        return DAPDataBreakpointHandler.isSupportsDataBreakpoints(getCapabilities());
    }

    /**
     * Returns the handler of the data breakpoints of the debug session.
     *
     * @return the handler of the data breakpoints of the debug session.
     */
    public @NotNull DAPDataBreakpointHandler getDataBreakpointHandler() {
        return debugProcess.getDataBreakpointHandler();
    }

    @NotNull
    public DebugAdapterDescriptor getServerDescriptor() {
        return debugProcess.getServerDescriptor();
    }

    @Nullable
    public DisassemblyFile getDisassemblyFile() {
        return debugProcess.getDisassemblyFile();
    }

    @NotNull
    public Project getProject() {
        return debugProcess.getSession().getProject();
    }

    public @NotNull String getConfigName() {
        return debugProcess.getConfigName();
    }

    public long getSessionId() {
        return debugProcess.getSessionId();
    }
}
