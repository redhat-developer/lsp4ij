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
import com.redhat.devtools.lsp4ij.dap.breakpoints.BreakpointInternalSupport;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPDataBreakpointHandler;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValueFormat;
import com.redhat.devtools.lsp4ij.dap.console.DAPOutputDecoder;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile;
import com.redhat.devtools.lsp4ij.dap.runInTerminal.RunInTerminalManager;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.Module;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
    // The execution epoch of the program, which ends whenever the program resumes or stops: the frames of a stop have
    // its epoch (DAPStackFrame), as their ids, and the references of their values, are valid only until it ends
    private final DAPExecutionEpoch executionEpoch = new DAPExecutionEpoch();
    // The 'continued' events of the debug adapter: a stop which one of them continued while it was handled doesn't
    // suspend the session
    private final DAPContinuedEvents continuedEvents = new DAPContinuedEvents();
    // Whether each thread runs, is stopped or has exited, which the Threads tab shows: immutable run states, which the
    // events of the debug adapter and the requests of the session which resume the program swap
    private final AtomicReference<DAPThreadRunStates> threadRunStates = new AtomicReference<>(DAPThreadRunStates.INITIAL);
    // Whether the last request that resumed the program was a step that resumed only its thread. A breakpoint which
    // doesn't suspend then resumes only the stopped thread, so the threads the step kept stopped stay stopped.
    private volatile boolean singleThreadResumed;
    // Whether the last request that resumed the program ran it backwards, with 'stepBack' or 'reverseContinue'. A
    // breakpoint which doesn't suspend then resumes it backwards too, with 'reverseContinue', not forward.
    private volatile boolean reverseResumed;
    // The last 'process' event of the DAP server, which describes the process it debugs, or null until it sends one
    private volatile @Nullable ProcessEventArguments processEvent;
    // The groups of the 'output' events of the DAP server, which the console indents
    private final DAPOutputGroups outputGroups = new DAPOutputGroups();
    // Decodes the ANSI escape sequences of the 'output' events of this session, whose styles continue from one event
    // to the next of the same category
    private final DAPOutputDecoder outputDecoder = new DAPOutputDecoder();

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
        return DAPClientLauncher.create(this, in, out, threadPool, wrapper, this::getCapabilities);
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

    /**
     * Shows in the console which process the DAP server debugs, with its PID, as its 'process' event describes it. In
     * launch mode the process of the debug session is the debug adapter's, so the event is how the user learns the
     * program's PID. The line of a child session started with 'startDebugging' names that session.
     */
    @Override
    public void process(ProcessEventArguments args) {
        processEvent = args;
        debugProcess.print(getProcessMessage(args, getChildSessionName()), ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    /**
     * Returns the last 'process' event of the DAP server, which describes the process it debugs: its name, PID, start
     * method and pointer size, as far as the DAP server knows them.
     *
     * @return the last 'process' event of the DAP server, or null if it sent none.
     */
    public @Nullable ProcessEventArguments getProcessEvent() {
        return processEvent;
    }

    /**
     * Returns the name of this session if it is a child session started with 'startDebugging': the name of the
     * configuration it was started with, as VS Code names a child session, or else the name of the run configuration.
     * Returns null for the root session.
     */
    private @Nullable String getChildSessionName() {
        if (parentClient == null) {
            return null;
        }
        return dapParameters.get("name") instanceof String name && StringUtils.isNotBlank(name) ? name : getConfigName();
    }

    /**
     * Returns the line which the console shows for a 'process' event, such as
     * {@code Debugging process '/path/app' (PID 1234, launched)}. What the event leaves out is left out of the line.
     *
     * @param args             the 'process' event.
     * @param childSessionName the name of the child session which received the event, or null for the root session.
     * @return the line which the console shows for the 'process' event.
     */
    static @NotNull String getProcessMessage(@NotNull ProcessEventArguments args, @Nullable String childSessionName) {
        var details = new ArrayList<String>();
        if (args.getSystemProcessId() != null) {
            // A string, since MessageFormat would group the digits of a number, as in 1,234
            details.add(DAPBundle.message("dap.process.pid", String.valueOf(args.getSystemProcessId())));
        }
        if (Boolean.FALSE.equals(args.getIsLocalProcess())) {
            details.add(DAPBundle.message("dap.process.remote"));
        }
        if (args.getStartMethod() != null) {
            details.add(switch (args.getStartMethod()) {
                case LAUNCH -> DAPBundle.message("dap.process.launch");
                case ATTACH -> DAPBundle.message("dap.process.attach");
                case ATTACH_FOR_SUSPENDED_LAUNCH -> DAPBundle.message("dap.process.attach.for.suspended.launch");
            });
        }
        String name = args.getName();
        String message = StringUtils.isNotBlank(name) ?
                DAPBundle.message("dap.process", name) : DAPBundle.message("dap.process.unnamed");
        if (!details.isEmpty()) {
            message = DAPBundle.message("dap.process.details", message, String.join(", ", details));
        }
        return childSessionName != null ? DAPBundle.message("dap.process.child.session", message, childSessionName) : message;
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
        if (isTelemetry(args)) {
            // DAP: "Send the output to telemetry instead of the client". It isn't for the user, and VS Code doesn't
            // show it either, but whoever diagnoses a DAP server can log it
            LOGGER.debug("Telemetry of the DAP server: {}", args.getOutput());
            return;
        }
        // The groups indent the lines, whose ANSI escape sequences are then decoded
        debugProcess.printOutput(outputDecoder.decode(outputGroups.indent(args), args.getCategory()));
    }

    /**
     * Returns whether the given output event is telemetry, which DAP sends to telemetry instead of the client, so that
     * the debug console doesn't show it.
     *
     * @param args the output event.
     * @return true if the output event has the category 'telemetry', and false otherwise.
     */
    static boolean isTelemetry(@NotNull OutputEventArguments args) {
        return OutputEventArgumentsCategory.TELEMETRY.equals(args.getCategory());
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
        int stopEpoch = executionEpoch.end();
        // Taken as the event arrives: a 'continued' event which arrives while the stop is handled ends the stop
        int continuedMark = continuedEvents.mark();
        // The Threads tab shows the thread which stopped, or all threads, as stopped. The Frames view of the stop shows
        // no frames for a thread which leaves it after this version, as when it continues alone
        int threadRunStatesVersion = updateThreadRunStates(states -> states.stopped(args)).getVersion();
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
        BooleanSupplier continued = () -> continuedEvents.continuedSince(continuedMark, threadId);
        getThreads(true).thenAcceptAsync(threads -> {
            var stoppedThread = Arrays.stream(threads)
                    .filter(thread -> threadId.equals(thread.getId()))
                    .findFirst();
            // A thread which the debug adapter doesn't report doesn't suspend the session
            stoppedThread.ifPresent(thread -> getStackFrames(server, threadId)
                    .thenAcceptAsync(frames -> suspend(server, args, stopEpoch, threadRunStatesVersion, continued,
                            threads, thread, frames)));
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
     * there. A stop which the debug adapter reports as caused by a breakpoint of any kind, such as an exception
     * breakpoint, brings the debugger to the front as an IntelliJ breakpoint does. What a data breakpoint saw change, as the stopped event describes it, is shown where the program stopped.
     * <p>
     * When the debug adapter said that the stopped thread continued since it stopped, the stop is over, and the session
     * isn't suspended there.
     *
     * @param server                 the debug adapter.
     * @param args                   the stopped event.
     * @param stopEpoch              the execution epoch of the stop.
     * @param threadRunStatesVersion the version of the run states of the threads at the stop.
     * @param continued              returns whether the debug adapter said that the stopped thread continued since it
     *                               stopped.
     * @param threads                the threads the debug adapter reports.
     * @param stoppedThread          the thread which stopped.
     * @param frames                 the stack frames of the stopped thread, innermost first.
     */
    private void suspend(@NotNull IDebugProtocolServer server,
                         @NotNull StoppedEventArguments args,
                         int stopEpoch,
                         int threadRunStatesVersion,
                         @NotNull BooleanSupplier continued,
                         org.eclipse.lsp4j.debug.Thread[] threads,
                         org.eclipse.lsp4j.debug.Thread stoppedThread,
                         StackFrame[] frames) {
        if (frames.length == 0) {
            return;
        }
        if (continued.getAsBoolean()) {
            // The thread continued while the threads and its stack frames were asked for
            return;
        }
        StackFrame topFrame = frames[0];
        DAPSuspendContext context = createSuspendContext(stopEpoch, threadRunStatesVersion, threads, stoppedThread, frames);
        var breakpointHandler = debugProcess.getBreakpointHandler();
        XBreakpoint<?> breakpoint = getStopBreakpoint(args, id -> debugProcess.findBreakpointById(server, id), () -> breakpointHandler.findBreakpoint(server, topFrame));
        if (breakpoint != null) {
            Boolean singleThread = singleThreadResumed ? Boolean.TRUE : null;
            boolean reverse = reverseResumed;
            // Set once the program is resumed at a breakpoint which doesn't suspend
            var resuming = new AtomicReference<@Nullable Resuming>();
            breakpointReached(getSession(), server, breakpoint, context, topFrame.getId(), stoppedThread.getId(),
                    singleThread, reverse, () -> resuming.set(beforeResuming(stoppedThread.getId(), singleThread)),
                    continued)
                    .exceptionally(error -> {
                        var refused = resuming.get();
                        if (refused == null) {
                            return reportBreakpointFailure(error);
                        }
                        // The debug adapter refused to resume the program, which is still stopped at the breakpoint:
                        // the session is suspended there, as at a breakpoint which suspends
                        runForwardRefused(refused, context, getCause(error), reverse ?
                                "dap.stepping.reverseContinue.failed" : "dap.stepping.continue.failed");
                        return null;
                    });
            if (breakpoint.getSuspendPolicy() != SuspendPolicy.NONE) {
                showDataBreakpointHint(getProject(), context, args, DAPClient::showInformationHint);
            }
            return;
        }
        positionReached(getSession(), context, args.getReason());
        if (resumeIfContinued(getSession(), context, continued)) {
            return;
        }
        if (StoppedEventArgumentsReason.EXCEPTION.equals(args.getReason())) {
            showExceptionHint(getProject(), context, server, getCapabilities(), args, DAPClient::showErrorHint);
        } else {
            showDataBreakpointHint(getProject(), context, args, DAPClient::showInformationHint);
        }
    }

    /**
     * Tells the session that the program stopped where the given context says. When the stopped event's reason says
     * that a breakpoint of any kind stopped the program, the session attracts the user as at an IntelliJ breakpoint:
     * it brings the Debug tool window to the front (when "Show debug window on breakpoint" is on) and selects the
     * Debugger tab (Variables in the classic UI). Without the internal API for that, the session selects the tab
     * only. Steps, pauses and other stops leave the tool window and its tabs as they are.
     *
     * @param session the debug session.
     * @param context the suspend context of the stop.
     * @param reason  the reason of the stopped event.
     */
    static void positionReached(@NotNull XDebugSession session,
                                @NotNull XSuspendContext context,
                                @Nullable String reason) {
        if (!isBreakpointReason(reason)) {
            session.positionReached(context);
            return;
        }
        if (BreakpointInternalSupport.positionReached(session, context, true)) {
            return;
        }
        session.positionReached(context);
        ApplicationManager.getApplication().invokeLater(() -> {
            var ui = session.getUI();
            if (ui != null && !session.isStopped()) {
                ui.attractBy(BREAKPOINT_CONDITION);
            }
        }, ModalityState.any(), session.getProject().getDisposed());
    }

    /**
     * Resumes the session suspended at the given suspend context of a stop, when the debug adapter said that the
     * stopped thread continued since it stopped: a 'continued' event which arrived while the session was being
     * suspended there found it not suspended yet, and left it as it was.
     *
     * @param session   the debug session.
     * @param context   the suspend context of the stop.
     * @param continued returns whether the debug adapter said that the stopped thread continued since it stopped.
     * @return true if the stopped thread continued, so that the stop is over, and false otherwise.
     */
    static boolean resumeIfContinued(@NotNull XDebugSession session,
                                     @NotNull XSuspendContext context,
                                     @NotNull BooleanSupplier continued) {
        if (!continued.getAsBoolean()) {
            return false;
        }
        // Unless the 'continued' event resumed it, or another stop suspended it since
        if (session.getSuspendContext() == context) {
            session.sessionResumed();
        }
        return true;
    }

    /**
     * The condition which selects the Debugger tab when a breakpoint stops the program
     * ({@code XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION}, which is internal).
     */
    static final String BREAKPOINT_CONDITION = "breakpoint";

    /**
     * Returns whether the reason of a stopped event says that a breakpoint of any kind stopped the program: a line,
     * exception, function, data or instruction breakpoint. A step, a pause, a stop on entry and a reason which DAP
     * doesn't define don't.
     *
     * @param reason the reason of the stopped event.
     * @return true if a breakpoint stopped the program and false otherwise.
     */
    static boolean isBreakpointReason(@Nullable String reason) {
        return StoppedEventArgumentsReason.BREAKPOINT.equals(reason)
                || StoppedEventArgumentsReason.EXCEPTION.equals(reason)
                || StoppedEventArgumentsReason.FUNCTION_BREAKPOINT.equals(reason)
                || StoppedEventArgumentsReason.DATA_BREAKPOINT.equals(reason)
                || StoppedEventArgumentsReason.INSTRUCTION_BREAKPOINT.equals(reason);
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
     * Returns a new suspend context of this client, with an execution stack per thread: the stopped thread's has the
     * frames it stopped in, and the others' are computed when shown. A stop while the session is suspended gets a new
     * context too, which replaces the session's: the threads are read again at each stop, and the sessions of a tree
     * ('startDebugging') share the debug session, so the context of another stop may be another client's. The stack
     * of a thread which leaves the stop after the given version of the run states of the threads, as when it continues
     * alone, shows no frames.
     */
    private @NotNull DAPSuspendContext createSuspendContext(int stopEpoch,
                                                           int threadRunStatesVersion,
                                                           org.eclipse.lsp4j.debug.Thread[] threads,
                                                           org.eclipse.lsp4j.debug.Thread stoppedThread,
                                                           StackFrame[] frames) {
        var context = new DAPSuspendContext(this, stopEpoch, stoppedThread.getId(), threadRunStatesVersion);
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
        // e.g. the session could not be told of the stop, before the program was resumed: say so instead of leaving it
        // stopped silently
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
     * @param resuming     runs right before the request which resumes the program is sent.
     * @param continued    returns whether the debug adapter said that the stopped thread continued since it stopped:
     *                     the stop is then over, and the session is neither told nor suspended there.
     * @return the future, completed once the session is told and the program resumed if it must be.
     */
    static CompletableFuture<@Nullable Void> breakpointReached(@NotNull XDebugSession session,
                                                               @NotNull IDebugProtocolServer server,
                                                               @NotNull XBreakpoint<?> breakpoint,
                                                               @NotNull XSuspendContext context,
                                                               int frameId,
                                                               int threadId,
                                                               @Nullable Boolean singleThread,
                                                               @NotNull Runnable resuming,
                                                               @NotNull BooleanSupplier continued) {
        return breakpointReached(session, server, breakpoint, context, frameId, threadId, singleThread, false, resuming,
                continued);
    }

    /**
     * Tells the session that the program stopped at the given breakpoint, as
     * {@link #breakpointReached(XDebugSession, IDebugProtocolServer, XBreakpoint, XSuspendContext, int, int, Boolean, Runnable, BooleanSupplier)}
     * does, and resumes the program in the direction it ran when it stopped there: backwards with 'reverseContinue'
     * when the last request that resumed it ran it backwards, as Reverse Continue does, and forward with 'continue'
     * otherwise.
     *
     * @param reverse true if the last request that resumed the program ran it backwards ('stepBack',
     *                'reverseContinue'), and false otherwise.
     * @see #breakpointReached(XDebugSession, IDebugProtocolServer, XBreakpoint, XSuspendContext, int, int, Boolean, Runnable, BooleanSupplier)
     */
    static CompletableFuture<@Nullable Void> breakpointReached(@NotNull XDebugSession session,
                                                               @NotNull IDebugProtocolServer server,
                                                               @NotNull XBreakpoint<?> breakpoint,
                                                               @NotNull XSuspendContext context,
                                                               int frameId,
                                                               int threadId,
                                                               @Nullable Boolean singleThread,
                                                               boolean reverse,
                                                               @NotNull Runnable resuming,
                                                               @NotNull BooleanSupplier continued) {
        return evaluateLogExpression(server, breakpoint, frameId)
                .thenCompose(logged -> {
                    if (continued.getAsBoolean()) {
                        // The thread continued while the log expression was evaluated
                        return CompletableFuture.completedFuture(null);
                    }
                    if (session.breakpointReached(breakpoint, logged, context)) {
                        resumeIfContinued(session, context, continued);
                        return CompletableFuture.completedFuture(null);
                    }
                    // The breakpoint doesn't suspend: resume the program, in the direction it ran
                    resuming.run();
                    if (reverse) {
                        return reverseContinue(server, threadId, singleThread);
                    }
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

    /**
     * Returns the debug session of this client, which the clients of a tree of DAP sessions ('startDebugging') share.
     *
     * @return the debug session of this client.
     */
    public XDebugSession getSession() {
        return debugProcess.getSession();
    }

    public CompletableFuture<ContinueResponse> continue_(int threadId) {
        return continue_(threadId, null);
    }

    /**
     * Resumes the program with 'continue', as Resume Program does, which IntelliJ resumes the session for before it asks
     * the debug process to. When the debug adapter refuses, the console says why, and the session is suspended again
     * at the given suspend context, where the program is still stopped (see {@link #runForward}).
     *
     * @param threadId the DAP thread of the stop which the program resumes from.
     * @param context  the suspend context which IntelliJ resumed the session from, or null to leave the session as it
     *                 is when the debug adapter refuses.
     * @return the future of the answer of the debug adapter, completed once a refusal is handled.
     */
    public CompletableFuture<ContinueResponse> continue_(int threadId, @Nullable XSuspendContext context) {
        var server = debugProtocolServer;
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }
        ContinueArguments args = new ContinueArguments();
        args.setThreadId(threadId);
        return runForward(threadId, null, context, () -> server.continue_(args), "dap.stepping.continue.failed");
    }

    /**
     * What a request of this client which resumes the program changed right before it was sent
     * ({@link #beforeResuming(int, Boolean)}), which {@link #runForwardRefused} undoes when the debug adapter refuses
     * the request.
     *
     * @param endedEpoch the execution epoch which ended.
     * @param before     the run states of the threads before the request.
     * @param resumed    the run states which show the threads that the request resumes as running.
     */
    private record Resuming(int endedEpoch, @NotNull DAPThreadRunStates before, @NotNull DAPThreadRunStates resumed) {
    }

    /**
     * Runs right before a request of this client which resumes the program is sent, such as Resume, a step, or the
     * resume of a breakpoint which doesn't suspend: ends the execution epoch, and shows the threads which the request
     * resumes as running. Done before the request, so that the 'stopped' event which follows it finds them running.
     *
     * @param threadId     the DAP thread which the request resumes.
     * @param singleThread true if the request resumes only that thread ('singleThread'), and null if it resumes every
     *                     thread.
     * @return what changed, which a refusal of the debug adapter undoes.
     */
    private @NotNull Resuming beforeResuming(int threadId, @Nullable Boolean singleThread) {
        int ended = executionEpoch.endBeforeResuming();
        DAPThreadRunStates before;
        DAPThreadRunStates resumed;
        do {
            before = threadRunStates.get();
            resumed = before.resumed(threadId, singleThread != null);
        } while (!threadRunStates.compareAndSet(before, resumed));
        debugProcess.refreshThreadRunStates(this);
        return new Resuming(ended, before, resumed);
    }

    /**
     * Sends a request which runs the program forward from a stop, Resume Program or a step, which IntelliJ resumes the
     * session for before it asks the debug process to: the session shows the program running while the request is
     * sent. The execution epoch ends, and the threads which the request resumes show running, before it is sent
     * ({@link #beforeResuming(int, Boolean)}). When the debug adapter refuses, the program is still stopped where it
     * was, and {@link #runForwardRefused} suspends the session there again and says why.
     *
     * @param threadId     the DAP thread which the request resumes.
     * @param singleThread the 'singleThread' of the request.
     * @param context      the suspend context which IntelliJ resumed the session from, or null to leave the session as
     *                     it is when the debug adapter refuses.
     * @param request      sends the request.
     * @param failedKey    the message which the console shows when the debug adapter refuses, with its reason.
     * @param <T>          the type of the answer.
     * @return the future of the answer of the debug adapter, completed once a refusal is handled.
     */
    private <T> CompletableFuture<T> runForward(int threadId,
                                                @Nullable Boolean singleThread,
                                                @Nullable XSuspendContext context,
                                                @NotNull Supplier<CompletableFuture<T>> request,
                                                @NotNull @PropertyKey(resourceBundle = DAPBundle.BUNDLE) String failedKey) {
        singleThreadResumed = singleThread != null;
        reverseResumed = false;
        var resuming = beforeResuming(threadId, singleThread);
        // Not async: a refusal is handled before the events which follow the answer, such as a 'stopped' event
        return request.get().whenComplete((answer, error) -> {
            if (error != null) {
                runForwardRefused(resuming, context, getCause(error), failedKey);
            }
        });
    }

    /**
     * Handles the refusal of the debug adapter to resume the program, with a request which
     * {@link #beforeResuming(int, Boolean)} prepared: one which runs it forward, or the 'reverseContinue' which resumes
     * it at a breakpoint which doesn't suspend once it ran backwards there. The program is still stopped where it was.
     * The threads which the request resumed get the run states of the stop back, and the execution epoch of the stop is
     * the current one again, so that the values of the stop can be set, unless a newer event changed them: the debug
     * adapter said meanwhile that the program stopped, continued, or that a thread exited. A Step Back, Reverse
     * Continue or Reset Frame which is still pending at the stop keeps its epoch ended until the debug adapter refuses
     * it too ({@link DAPExecutionEpoch#resumeRefused(int)}), and a newer resume which is still pending, as at another
     * breakpoint which doesn't suspend, keeps the threads which it resumed running until the debug adapter refuses it
     * too ({@link DAPThreadRunStates#resumeRefused}). Unless the debug adapter said that the program stopped or
     * continued, the session is suspended at the given suspend context, unless another stop suspended it meanwhile:
     * again, as IntelliJ resumed it before it asked for the request, or for the first time at a breakpoint which doesn't
     * suspend. The console first says why the debug adapter refused.
     *
     * @param resuming  what {@link #beforeResuming(int, Boolean)} changed before the request was sent.
     * @param context   the suspend context where the program is still stopped, or null to leave the session as it is.
     * @param error     the error which the debug adapter answered.
     * @param failedKey the message which the console shows, with the reason of the debug adapter.
     */
    private void runForwardRefused(@NotNull Resuming resuming,
                                   @Nullable XSuspendContext context,
                                   @NotNull Throwable error,
                                   @NotNull @PropertyKey(resourceBundle = DAPBundle.BUNDLE) String failedKey) {
        debugProcess.print(DAPBundle.message(failedKey, error.getMessage()), ConsoleViewContentType.ERROR_OUTPUT);
        updateThreadRunStates(states -> states.resumeRefused(resuming.before(), resuming.resumed()));
        if (executionEpoch.resumeRefused(resuming.endedEpoch()) && context != null) {
            // "Run to Cursor" ends at this stop, as at any other, rather than at a later one
            debugProcess.getRunToCursorBreakpoint().stopped();
            suspendAgain(getSession(), context);
        }
    }

    /**
     * Suspends the session at the given suspend context, where the program is still stopped since the debug adapter
     * refused to resume it: IntelliJ resumes the session before it asks for Resume Program or a step, and doesn't
     * suspend it at a breakpoint which doesn't suspend. Resume Program and the steps then work again, and the frames
     * and values of the stop are shown. A session which ended, or which another stop suspended meanwhile, is left as it
     * is.
     *
     * @param session the debug session.
     * @param context the suspend context of the stop where the program is.
     * @return true if the session is suspended at the given suspend context again, and false otherwise.
     */
    static boolean suspendAgain(@NotNull XDebugSession session, @NotNull XSuspendContext context) {
        if (session.isStopped() || session.getSuspendContext() != null) {
            return false;
        }
        session.positionReached(context);
        return true;
    }

    public void next(int threadId, @Nullable SteppingGranularity granularity) {
        next(threadId, granularity, null);
    }

    /**
     * Steps over with 'next' in the given thread, which IntelliJ resumes the session for before it asks the debug
     * process to. When the debug adapter refuses, the console says why, and the session is suspended again at the given
     * suspend context, where the program is still stopped (see {@link #runForward}).
     *
     * @param threadId    the thread which steps.
     * @param granularity the granularity of the step, or null for the DAP server's default.
     * @param context     the suspend context which IntelliJ resumed the session from, or null to leave the session as
     *                    it is when the debug adapter refuses.
     * @return the future of the answer of the debug adapter, completed once a refusal is handled.
     */
    public CompletableFuture<Void> next(int threadId,
                                        @Nullable SteppingGranularity granularity,
                                        @Nullable XSuspendContext context) {
        var server = debugProtocolServer;
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }
        var singleThread = singleThreadStep();
        return runForward(threadId, singleThread, context, () -> next(server, threadId, granularity, singleThread),
                "dap.stepping.next.failed");
    }

    /**
     * Sends 'next', Step Over, for the given thread.
     *
     * @param server       the DAP server.
     * @param threadId     the thread which steps.
     * @param granularity  the granularity of the step, or null for the DAP server's default.
     * @param singleThread true to resume only the thread which steps, and null to resume every thread.
     * @return the future of the answer of the DAP server.
     */
    static CompletableFuture<Void> next(@NotNull IDebugProtocolServer server,
                                        int threadId,
                                        @Nullable SteppingGranularity granularity,
                                        @Nullable Boolean singleThread) {
        NextArguments args = new NextArguments();
        args.setThreadId(threadId);
        args.setGranularity(granularity);
        args.setSingleThread(singleThread);
        return server.next(args);
    }

    public void stepOut(int threadId, SteppingGranularity granularity) {
        stepOut(threadId, granularity, null);
    }

    /**
     * Steps out with 'stepOut' in the given thread, which IntelliJ resumes the session for before it asks the debug
     * process to. When the debug adapter refuses, the console says why, and the session is suspended again at the given
     * suspend context, where the program is still stopped (see {@link #runForward}).
     *
     * @param threadId    the thread which steps.
     * @param granularity the granularity of the step, or null for the DAP server's default.
     * @param context     the suspend context which IntelliJ resumed the session from, or null to leave the session as
     *                    it is when the debug adapter refuses.
     * @return the future of the answer of the debug adapter, completed once a refusal is handled.
     */
    public CompletableFuture<Void> stepOut(int threadId,
                                           @Nullable SteppingGranularity granularity,
                                           @Nullable XSuspendContext context) {
        var server = debugProtocolServer;
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }
        var singleThread = singleThreadStep();
        return runForward(threadId, singleThread, context, () -> stepOut(server, threadId, granularity, singleThread),
                "dap.stepping.stepOut.failed");
    }

    /**
     * Sends 'stepOut', Step Out, for the given thread.
     *
     * @param server       the DAP server.
     * @param threadId     the thread which steps.
     * @param granularity  the granularity of the step, or null for the DAP server's default.
     * @param singleThread true to resume only the thread which steps, and null to resume every thread.
     * @return the future of the answer of the DAP server.
     */
    static CompletableFuture<Void> stepOut(@NotNull IDebugProtocolServer server,
                                           int threadId,
                                           @Nullable SteppingGranularity granularity,
                                           @Nullable Boolean singleThread) {
        StepOutArguments args = new StepOutArguments();
        args.setThreadId(threadId);
        args.setGranularity(granularity);
        args.setSingleThread(singleThread);
        return server.stepOut(args);
    }

    public void stepIn(int threadId, SteppingGranularity granularity) {
        stepIn(threadId, null, granularity);
    }

    public void stepIn(int threadId, @Nullable Integer targetId, @Nullable SteppingGranularity granularity) {
        stepIn(threadId, targetId, granularity, null);
    }

    /**
     * Steps into with 'stepIn' in the given thread, as Step Into does, or Smart Step Into with a target, which IntelliJ
     * resumes the session for before it asks the debug process to. When the debug adapter refuses, the console says
     * why, and the session is suspended again at the given suspend context, where the program is still stopped (see
     * {@link #runForward}).
     *
     * @param threadId    the thread which steps.
     * @param targetId    the target to step into, from 'stepInTargets', or null.
     * @param granularity the granularity of the step, or null for the DAP server's default.
     * @param context     the suspend context which IntelliJ resumed the session from, or null to leave the session as
     *                    it is when the debug adapter refuses.
     * @return the future of the answer of the debug adapter, completed once a refusal is handled.
     */
    public CompletableFuture<Void> stepIn(int threadId,
                                          @Nullable Integer targetId,
                                          @Nullable SteppingGranularity granularity,
                                          @Nullable XSuspendContext context) {
        var server = debugProtocolServer;
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }
        var singleThread = singleThreadStep();
        return runForward(threadId, singleThread, context,
                () -> stepIn(server, threadId, targetId, granularity, singleThread), "dap.stepping.stepIn.failed");
    }

    /**
     * Sends 'stepIn', Step Into, or Smart Step Into with a target, for the given thread.
     *
     * @param server       the DAP server.
     * @param threadId     the thread which steps.
     * @param targetId     the target to step into, from 'stepInTargets', or null.
     * @param granularity  the granularity of the step, or null for the DAP server's default.
     * @param singleThread true to resume only the thread which steps, and null to resume every thread.
     * @return the future of the answer of the DAP server.
     */
    static CompletableFuture<Void> stepIn(@NotNull IDebugProtocolServer server,
                                          int threadId,
                                          @Nullable Integer targetId,
                                          @Nullable SteppingGranularity granularity,
                                          @Nullable Boolean singleThread) {
        StepInArguments args = new StepInArguments();
        args.setThreadId(threadId);
        args.setTargetId(targetId);
        args.setGranularity(granularity);
        args.setSingleThread(singleThread);
        return server.stepIn(args);
    }

    /**
     * Steps the given thread back with 'stepBack', when the debug adapter supports it (supportsStepBack). Once the
     * debug adapter has answered, the session shows the program running, as after a step, until the 'stopped' event
     * which follows suspends it where the thread stepped back to. When the debug adapter refuses, as one which has
     * nothing recorded to step back in does, the console says why and the session stays suspended where it was.
     *
     * @param threadId    the thread which steps back.
     * @param granularity the granularity of the step, or null for the DAP server's default.
     * @return the future, completed once the debug adapter has answered.
     */
    public CompletableFuture<@Nullable Void> stepBack(int threadId, @Nullable SteppingGranularity granularity) {
        var server = debugProtocolServer;
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }
        var singleThread = singleThreadStep();
        return runBackwards(threadId, singleThread, () -> stepBack(server, threadId, granularity, singleThread),
                "dap.stepping.stepBack.failed");
    }

    /**
     * Sends 'stepBack', Step Back, for the given thread.
     *
     * @param server       the DAP server.
     * @param threadId     the thread which steps back.
     * @param granularity  the granularity of the step, or null for the DAP server's default.
     * @param singleThread true to resume only the thread which steps back, and null to resume every thread.
     * @return the future of the answer of the DAP server.
     */
    static CompletableFuture<Void> stepBack(@NotNull IDebugProtocolServer server,
                                            int threadId,
                                            @Nullable SteppingGranularity granularity,
                                            @Nullable Boolean singleThread) {
        StepBackArguments args = new StepBackArguments();
        args.setThreadId(threadId);
        args.setGranularity(granularity);
        args.setSingleThread(singleThread);
        return server.stepBack(args);
    }

    /**
     * Runs the program backwards with 'reverseContinue', when the debug adapter supports it (supportsStepBack), as
     * {@link #stepBack(int, SteppingGranularity)} steps back: the 'stopped' event which follows the answer suspends the
     * session where the program stopped, such as at a breakpoint, and the session stays suspended when the debug
     * adapter refuses.
     *
     * @param threadId the thread of the stop which the program runs backwards from.
     * @return the future, completed once the debug adapter has answered.
     */
    public CompletableFuture<@Nullable Void> reverseContinue(int threadId) {
        var server = debugProtocolServer;
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }
        var singleThread = singleThreadStep();
        return runBackwards(threadId, singleThread, () -> reverseContinue(server, threadId, singleThread),
                "dap.stepping.reverseContinue.failed");
    }

    /**
     * Sends 'reverseContinue', Reverse Continue, for the given thread.
     *
     * @param server       the DAP server.
     * @param threadId     the thread of the stop which the program runs backwards from.
     * @param singleThread true to resume only the given thread, and null to resume every thread.
     * @return the future of the answer of the DAP server.
     */
    static CompletableFuture<Void> reverseContinue(@NotNull IDebugProtocolServer server,
                                                   int threadId,
                                                   @Nullable Boolean singleThread) {
        ReverseContinueArguments args = new ReverseContinueArguments();
        args.setThreadId(threadId);
        args.setSingleThread(singleThread);
        return server.reverseContinue(args);
    }

    /**
     * Sends a request which runs the program backwards, which IntelliJ doesn't resume the session for as it does for
     * its own steps. The execution epoch ends before the request is sent, as for a step, and is the current one again
     * when the debug adapter refuses. The threads show running once the debug adapter has accepted the request.
     *
     * @param threadId     the thread of the stop which the program runs backwards from.
     * @param singleThread the 'singleThread' of the request.
     * @param request      sends the request.
     * @param failedKey    the message which the console shows when the debug adapter refuses, with its reason.
     * @return the future, completed once the debug adapter has answered.
     */
    private CompletableFuture<@Nullable Void> runBackwards(int threadId,
                                                           @Nullable Boolean singleThread,
                                                           @NotNull Supplier<CompletableFuture<Void>> request,
                                                           @NotNull @PropertyKey(resourceBundle = DAPBundle.BUNDLE) String failedKey) {
        var session = getSession();
        var context = session.getSuspendContext();
        singleThreadResumed = singleThread != null;
        reverseResumed = true;
        int ended = executionEpoch.endBeforeResuming();
        // The threads run once the debug adapter has accepted the request, right before the session resumes
        // (resumeWhenAnswered), which then no longer has this suspend context: a refused request leaves them stopped,
        // and a stop which came meanwhile is newer. The 'stopped' event, which a debug adapter may send before its
        // answer, ends the execution epoch as it arrives, before the stop has a suspend context
        var accepted = request.get().thenApply(response -> {
            if (session.getSuspendContext() == context && !executionEpoch.stoppedOrContinuedSince(ended)) {
                updateThreadRunStates(states -> states.resumed(threadId, singleThread != null));
            }
            return response;
        });
        return resumeWhenAnswered(accepted, session, context, error -> {
            executionEpoch.resumeRefused(ended);
            debugProcess.print(DAPBundle.message(failedKey, error.getMessage()), ConsoleViewContentType.ERROR_OUTPUT);
        });
    }

    /**
     * Shows the program running once the debug adapter has accepted a request which resumes it, and which IntelliJ
     * doesn't resume the session for, such as 'stepBack': {@link XDebugSession#sessionResumed()} clears the views of
     * the stop as a step does, and the 'stopped' event which follows the answer suspends the session again. When the
     * debug adapter refuses, the session stays suspended where it was.
     *
     * @param request the request which resumes the program.
     * @param session the debug session.
     * @param context the suspend context of the session when the request was sent, or null if it was not suspended.
     * @param refused reports why the debug adapter refused.
     * @return the future, completed once the debug adapter has answered.
     */
    static CompletableFuture<@Nullable Void> resumeWhenAnswered(@NotNull CompletableFuture<?> request,
                                                                @NotNull XDebugSession session,
                                                                @Nullable XSuspendContext context,
                                                                @NotNull Consumer<Throwable> refused) {
        // Not async: the session runs before the 'stopped' event which follows the answer is handled
        return request.handle((response, error) -> {
            if (error != null) {
                refused.accept(getCause(error));
            } else if (session.getSuspendContext() == context) {
                // Unless a 'continued' event resumed it, or a stop suspended it, meanwhile
                session.sessionResumed();
            }
            return null;
        });
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
     * Asks the DAP server where a location reference which it gave points to, with the 'locations' request, such as
     * the 'valueLocationReference' of a variable. No capability announces the request: the DAP server answers it for
     * the references which it gives. The request is written on the calling thread, as LSP4J writes it, so that
     * {@link #sendInExecutionEpoch(int, Supplier, String)} can send it: call it off the EDT.
     *
     * @param locationReference the location reference which the DAP server gave.
     * @return the future of the location, which is null when the DAP server answers without one or when the client is
     * not connected to the DAP server; it fails with the error of the DAP server.
     */
    public CompletableFuture<@Nullable LocationsResponse> locations(int locationReference) {
        var server = debugProtocolServer;
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }
        var args = new LocationsArguments();
        args.setLocationReference(locationReference);
        return server.locations(args);
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

    /**
     * Assigns a value to an expression with the 'setExpression' request.
     *
     * @param expression the expression which the value is assigned to, such as the expression of an Evaluate result
     *                   or the 'evaluateName' of a variable.
     * @param frameId    the stack frame in which the expression and the value are evaluated.
     * @param value      the expression of the value which is assigned.
     * @return the new value of the expression, or null when the client is not connected to the debug adapter.
     */
    public CompletableFuture<SetExpressionResponse> setExpression(@NotNull String expression,
                                                                  int frameId,
                                                                  @NotNull String value) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(null);
        }
        SetExpressionArguments args = new SetExpressionArguments();
        args.setExpression(expression);
        args.setValue(value);
        args.setFrameId(frameId);
        args.setFormat(getValueFormat());
        return debugProtocolServer.setExpression(args);
    }

    // Threads

    @Override
    public void continued(ContinuedEventArguments args) {
        // The program runs: the references of the frames of its last stop are out of date
        executionEpoch.end();
        // Recorded before the session is looked at: a stop which is handled meanwhile then sees it (see suspend)
        continuedEvents.continued(args);
        // The Threads tab shows the thread which continued, or all threads, as running. Before the session is looked
        // at: the Frames view then shows no frames for a thread which continued alone
        updateThreadRunStates(states -> states.continued(args));
        continued(getSession(), args, this::getStoppedThreadId, () -> {
            getThreads(true);
            // The Frames view asks again for the frames of the stack which it shows, and forgets those of the others,
            // such as the frames of the stop of the thread which continued
            getSession().rebuildViews();
            selectStoppedThread(getSession(), args.getThreadId());
        });
    }

    /**
     * Selects the top frame of the thread where the session stays suspended, when the current frame of the session is a
     * frame of the given thread, which continued alone ({@code allThreadsContinued: false}) while the session stays
     * suspended where another thread stopped: the Variables view and the execution point follow the stopped thread, and
     * Step and Resume, which act on the thread of the active stack of the suspend context, act on it instead of on a
     * thread which runs. A current frame of a thread which is still stopped stays selected.
     * <p>
     * The selection moves on the EDT, where the Frames view expects it, unless another stop suspended the session, or
     * another frame was selected, since the thread continued.
     *
     * @param session           the debug session, suspended at a stop of the client which received the event.
     * @param continuedThreadId the DAP thread which continued alone.
     */
    static void selectStoppedThread(@NotNull XDebugSession session, int continuedThreadId) {
        var currentFrame = session.getCurrentStackFrame();
        if (!(session.getSuspendContext() instanceof DAPSuspendContext context) || currentFrame == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (session.getSuspendContext() != context || session.getCurrentStackFrame() != currentFrame) {
                // Another stop suspended the session, or another frame was selected, since the thread continued
                return;
            }
            var continuedStack = context.getExecutionStack(continuedThreadId);
            if (continuedStack == null || !continuedStack.hasStackFrame(currentFrame)) {
                // The current frame is a frame of a thread which is still stopped
                return;
            }
            Integer stoppedThreadId = context.getStoppedThreadId();
            var stoppedStack = stoppedThreadId != null ? context.getExecutionStack(stoppedThreadId) : null;
            var topFrame = stoppedStack != null ? stoppedStack.getTopFrame() : null;
            if (topFrame == null) {
                return;
            }
            // Step and Resume act on the thread of the active stack
            context.setActiveExecutionStack(stoppedStack);
            session.setCurrentStackFrame(stoppedStack, topFrame, true);
        }, ModalityState.any(), session.getProject().getDisposed());
    }

    /**
     * Shows the program as running when the debug adapter says that it continued, as after a step typed in the Debug
     * console, which no request of the session caused. The session resumes when it is suspended at a stop of the
     * client which received the event, and the event continued the thread of that stop. A 'continued' event of another
     * thread ({@code allThreadsContinued: false}) leaves the session suspended, and shows the threads again, and the
     * views of the stop, where that thread has no frames any more.
     * <p>
     * The debug adapter isn't asked to resume the program: {@link XDebugSession#sessionResumed()} only clears the views
     * of the stop, and does nothing when the session isn't suspended, as after a resume or a step of the session itself.
     *
     * @param session        the debug session.
     * @param args           the 'continued' event.
     * @param stoppedThread  returns the DAP thread which stopped at a suspend context of the client which received the
     *                       event, and null for no suspend context or one of another client, such as the stop of
     *                       another session of a tree ('startDebugging').
     * @param refreshThreads shows the threads of the client again, and the views of the stop, where a selection of the
     *                       thread which continued moves to the thread of the stop.
     * @return true if the session resumed and false otherwise.
     */
    static boolean continued(@NotNull XDebugSession session,
                             @NotNull ContinuedEventArguments args,
                             @NotNull Function<@Nullable XSuspendContext, @Nullable Integer> stoppedThread,
                             @NotNull Runnable refreshThreads) {
        if (!session.isSuspended()) {
            return false;
        }
        Integer threadId = stoppedThread.apply(session.getSuspendContext());
        if (threadId == null) {
            // The session shows a stop of another client, which this event doesn't continue
            return false;
        }
        if (continues(args, threadId)) {
            session.sessionResumed();
            return true;
        }
        // Another thread continued, and the thread where the session is suspended stays stopped
        refreshThreads.run();
        return false;
    }

    /**
     * Returns whether the given 'continued' event continued the given thread: it continued all threads, unless it says
     * that only its own thread continued ({@code allThreadsContinued: false}).
     *
     * @param args     the 'continued' event.
     * @param threadId the DAP thread.
     * @return true if the event continued the thread and false otherwise.
     */
    static boolean continues(@NotNull ContinuedEventArguments args, int threadId) {
        return !Boolean.FALSE.equals(args.getAllThreadsContinued()) || args.getThreadId() == threadId;
    }

    /**
     * Returns the DAP thread which stopped at the given suspend context, when it is a suspend context of this client.
     *
     * @param context the suspend context of the session, or null.
     * @return the DAP thread which stopped at the given suspend context, and null when it is not a suspend context of
     * this client.
     */
    private @Nullable Integer getStoppedThreadId(@Nullable XSuspendContext context) {
        // The thread which stopped, not the one which the Frames view selects
        return context instanceof DAPSuspendContext dapContext && dapContext.getClient() == this
                ? dapContext.getStoppedThreadId()
                : null;
    }

    @Override
    public void thread(ThreadEventArguments args) {
        // A thread which exited, or started, which the Threads tab shows with the event
        threadRunStates.updateAndGet(states -> states.thread(args));
        debugProcess.refreshThread(this, args);
    }

    /**
     * Returns the version of the run states of the threads at the stop where the session is suspended, when it is a
     * stop of this client.
     *
     * @param otherwise the version to return when the session isn't suspended at a stop of this client.
     * @return the version of the run states at the stop where the session is suspended, or the given one.
     */
    private int getSuspendedStopVersion(int otherwise) {
        var session = getSession();
        return session != null && session.getSuspendContext() instanceof DAPSuspendContext context
                && context.getClient() == this ? context.getThreadRunStatesVersion() : otherwise;
    }

    /**
     * Returns whether each thread of the DAP session of this client runs, is stopped or has exited, as its debug
     * adapter and the requests of the session which resume the program said so far.
     *
     * @return the run states of the threads of the DAP session of this client.
     */
    public @NotNull DAPThreadRunStates getThreadRunStates() {
        return threadRunStates.get();
    }

    /**
     * Changes the run states of the threads, and shows them in the Threads tab.
     *
     * @param change returns the new run states from the current ones.
     * @return the new run states.
     */
    private @NotNull DAPThreadRunStates updateThreadRunStates(@NotNull UnaryOperator<DAPThreadRunStates> change) {
        var states = threadRunStates.updateAndGet(change);
        debugProcess.refreshThreadRunStates(this);
        return states;
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
        // The run states which the answer can see: a thread which changes after the request isn't forgotten
        int askedAtVersion = threadRunStates.get().getVersion();
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
                // The Threads tab shows these threads: the run states of the others, such as those which exited, go
                List<Integer> reported = threads != null ?
                        Arrays.stream(threads).map(org.eclipse.lsp4j.debug.Thread::getId).toList() : List.of();
                // The run states which changed since the stop where the session is suspended stay too, as that of a
                // thread which exited since: the stacks of that stop show them
                int keepChangedAfter = Math.min(askedAtVersion, getSuspendedStopVersion(askedAtVersion));
                threadRunStates.updateAndGet(states -> states.retain(reported, keepChangedAfter));
                debugProcess.refreshThreads(this, threads);
            });
        }
        return result;
    }

    /**
     * Terminates the threads with the given IDs, with the 'terminateThreads' request, when the DAP server supports it
     * (supportsTerminateThreadsRequest). The request is sent off the calling thread, so that the EDT doesn't wait on the
     * connection. The DAP server then reports the end of each thread with a 'thread' event. A failure is shown in the
     * console.
     *
     * @param threadIds the IDs of the threads to terminate, as this client's DAP server numbers them.
     * @return the future, completed once the DAP server has answered, or at once when nothing is sent.
     */
    public CompletableFuture<@Nullable Void> terminateThreads(@NotNull List<Integer> threadIds) {
        var server = debugProtocolServer;
        if (server == null || threadIds.isEmpty() || !isSupportsTerminateThreadsRequest()) {
            return CompletableFuture.completedFuture(null);
        }
        return terminateThreads(server, threadIds, AppExecutorUtil.getAppExecutorService())
                .exceptionally(error -> {
                    debugProcess.print(DAPBundle.message("dap.threads.terminate.failed", getCause(error).getMessage()),
                            ConsoleViewContentType.ERROR_OUTPUT);
                    return null;
                });
    }

    /**
     * Sends 'terminateThreads' for the given threads, from the given executor: LSP4J writes a request to the
     * connection on the thread which sends it.
     *
     * @param server    the DAP server.
     * @param threadIds the IDs of the threads to terminate.
     * @param executor  the executor which sends the request.
     * @return the future of the DAP server's answer.
     */
    static CompletableFuture<@Nullable Void> terminateThreads(@NotNull IDebugProtocolServer server,
                                                              @NotNull List<Integer> threadIds,
                                                              @NotNull Executor executor) {
        var args = new TerminateThreadsArguments();
        args.setThreadIds(threadIds.stream().mapToInt(Integer::intValue).toArray());
        return CompletableFuture.supplyAsync(() -> server.terminateThreads(args), executor)
                .thenCompose(Function.identity());
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
     * Returns true if the debug adapter supports the 'terminateThreads' request and false otherwise.
     *
     * @return true if the debug adapter supports the 'terminateThreads' request and false otherwise.
     */
    public boolean isSupportsTerminateThreadsRequest() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsTerminateThreadsRequest());
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
     * Returns the current execution epoch of the program, which ends whenever the program resumes or stops: the frames
     * of a stop have its epoch, as their ids, and the references of their values, are valid only until it ends.
     *
     * @return the current execution epoch of the program, 0 before it first stops.
     */
    public int getExecutionEpoch() {
        return executionEpoch.get();
    }

    /**
     * Sends a request which names references of the given execution epoch, such as the id of a stack frame or a
     * 'variablesReference', if the epoch is still the current one. The check and the sending are atomic against a
     * request of this client which resumes the program: the request is written before it, or not at all.
     *
     * @param epoch   the execution epoch of the references which the request names.
     * @param request sends the request: it must only write it, as the program can't resume meanwhile.
     * @param <T>     the type of the response.
     * @return the future of the response. When the epoch has ended, the request is not sent, and the future fails, after
     * this method returned, as an error of the debug adapter does, with the message which says that the program has run
     * since the value was shown.
     */
    public <T> @NotNull CompletableFuture<T> sendInExecutionEpoch(int epoch,
                                                                 @NotNull Supplier<CompletableFuture<T>> request) {
        return sendInExecutionEpoch(epoch, request, DAPBundle.message("dap.variables.setValue.stale"));
    }

    /**
     * Sends a request which names references of the given execution epoch, as
     * {@link #sendInExecutionEpoch(int, Supplier)} does, with the given message when the epoch has ended.
     *
     * @param epoch        the execution epoch of the references which the request names.
     * @param request      sends the request: it must only write it, as the program can't resume meanwhile.
     * @param endedMessage the message of the failure of the future when the epoch has ended, which says why the
     *                     request can't be sent.
     * @param <T>          the type of the response.
     * @return the future of the response. When the epoch has ended, the request is not sent, and the future fails, after
     * this method returned, as an error of the debug adapter does, with the given message.
     */
    public <T> @NotNull CompletableFuture<T> sendInExecutionEpoch(int epoch,
                                                                 @NotNull Supplier<CompletableFuture<T>> request,
                                                                 @NotNull @Nls String endedMessage) {
        var sent = executionEpoch.send(epoch, request);
        if (sent != null) {
            return sent;
        }
        return CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException(endedMessage);
        }, AppExecutorUtil.getAppExecutorService());
    }

    /**
     * Ends the current execution epoch before a request of this client which resumes the program is sent.
     *
     * @return the execution epoch which ended.
     */
    int endExecutionEpochBeforeResuming() {
        return executionEpoch.endBeforeResuming();
    }

    /**
     * Makes the execution epoch which {@link #endExecutionEpochBeforeResuming()} ended the current one again, when the
     * debug adapter refused to resume the program, unless the epoch has changed since.
     *
     * @param ended the execution epoch which {@link #endExecutionEpochBeforeResuming()} ended.
     */
    void resumeRefused(int ended) {
        executionEpoch.resumeRefused(ended);
    }

    /**
     * Returns true if the debug adapter supports setting a variable to a value and false otherwise.
     *
     * @return true if the debug adapter supports setting a variable to a value and false otherwise.
     */
    public boolean isSupportsSetVariable() {
        return isSupportsSetVariable(getCapabilities());
    }

    /**
     * Returns true if the given capabilities of a debug adapter support setting a variable to a value and false
     * otherwise.
     *
     * @param capabilities the capabilities of a debug adapter.
     * @return true if the given capabilities support setting a variable to a value and false otherwise.
     */
    public static boolean isSupportsSetVariable(@NotNull Capabilities capabilities) {
        return Boolean.TRUE.equals(capabilities.getSupportsSetVariable());
    }

    /**
     * Returns true if the debug adapter supports assigning a value to an expression with the 'setExpression' request
     * and false otherwise.
     *
     * @return true if the debug adapter supports the 'setExpression' request and false otherwise.
     */
    public boolean isSupportsSetExpression() {
        return isSupportsSetExpression(getCapabilities());
    }

    /**
     * Returns true if the given capabilities of a debug adapter support the 'setExpression' request and false
     * otherwise.
     *
     * @param capabilities the capabilities of a debug adapter.
     * @return true if the given capabilities support the 'setExpression' request and false otherwise.
     */
    public static boolean isSupportsSetExpression(@NotNull Capabilities capabilities) {
        return Boolean.TRUE.equals(capabilities.getSupportsSetExpression());
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
     * Returns true if the debug adapter supports running the program backwards, with the 'stepBack' and
     * 'reverseContinue' requests, and false otherwise.
     *
     * @return true if the debug adapter supports stepping back and false otherwise.
     */
    public boolean isSupportsStepBack() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsStepBack());
    }

    /**
     * Returns true if the debug adapter of this client, or that of a child session, supports running the program
     * backwards, and false otherwise.
     *
     * @return true if the debug adapter of this client, or that of a child session, supports stepping back, and false
     * otherwise.
     */
    public boolean isSupportsStepBackInSessions() {
        if (isSupportsStepBack()) {
            return true;
        }
        for (DAPClient child : List.copyOf(childrenClient)) {
            if (child.isSupportsStepBackInSessions()) {
                return true;
            }
        }
        return false;
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
