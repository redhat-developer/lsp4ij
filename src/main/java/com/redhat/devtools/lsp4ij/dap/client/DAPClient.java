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

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.console.explorer.TracingMessageConsumer;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.TransportStreams;
import com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointProperties;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.jsonrpc.validation.ReflectiveMessageValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.UnaryOperator;

/**
 * Debug Protocol Adapter (DAP) client implementation.
 */
public class DAPClient implements IDebugProtocolClient, Disposable {

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
    private boolean isConnected;
    private Future<Void> debugProtocolFuture;
    private IDebugProtocolServer debugProtocolServer;
    private @Nullable Capabilities capabilities;
    private @NotNull
    final List<DAPClient> childrenClient;
    private boolean sentTerminateRequest;

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
        this.childrenClient = new ArrayList<>();
    }

    @NotNull
    public CompletableFuture<Void> connectToServer(@NotNull ProgressIndicator indicator) {
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
            if (true) {
                result = new ReflectiveMessageValidator(result);
            }
            return result;
        };

        Launcher<? extends IDebugProtocolServer> debugProtocolLauncher = createLauncher(wrapper,
                transportStreams.in,
                transportStreams.out,
                threadPool);

        debugProtocolFuture = debugProtocolLauncher.startListening();
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
                .thenAccept((Capabilities capabilities) -> {
                    monitor.checkCanceled();
                    monitor.setFraction(10d / 100d);
                    if (capabilities == null) {
                        debugProcess.print("Debug adapter unexpectedly returned 'null' Capabilities from initializeRequest. "
                                + "A default Capabilities will be used instead.", ConsoleViewContentType.LOG_WARNING_OUTPUT);
                        capabilities = new Capabilities();
                    }
                    capabilitiesFuture.complete(capabilities);
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
                        monitor.checkCanceled();
                        monitor.setFraction(60d / 100d);
                        monitor.setText2("Sending breakpoints");
                        return debugProcess
                                .getBreakpointHandler()
                                .initialize(getDebugProtocolServer(), getCapabilities());
                    });
        }
        configurationDoneFuture = configurationDoneFuture
                .thenCompose(v -> {
                    monitor.checkCanceled();
                    monitor.setFraction(70d / 100d);
                    monitor.setText2("Sending configuration done");
                    if (Boolean.TRUE.equals(getCapabilities().getSupportsConfigurationDoneRequest())) {
                        return getDebugProtocolServer().configurationDone(new ConfigurationDoneArguments());
                    }
                    return CompletableFuture.completedFuture(null);
                });
        return CompletableFuture.allOf(launchAttachFuture, configurationDoneFuture);
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
     * return @{code null}
     */
    @Nullable
    Capabilities getCapabilities() {
        return capabilitiesFuture.getNow(null);
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
    public void output(OutputEventArguments args) {
        String output = args.getOutput();
        if (StringUtils.isNotBlank(output)) {
            debugProcess.print(output, getContentType(args.getCategory()));
        }
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
    public void stopped(StoppedEventArguments args) {
        var server = getDebugProtocolServer();
        if (server == null) {
            return;
        }
        Integer threadId = args.getThreadId();
        if (threadId == null) {
            return;
        }
        server
                .threads()
                .thenAcceptAsync(threadsResponse -> {
                    var threadResult = Arrays.stream(threadsResponse.getThreads())
                            .filter(t -> threadId.equals(t.getId()))
                            .findFirst();
                    if (threadResult.isEmpty()) {
                        // The Thread doesn't exist
                        return;
                    }
                    var thread = threadResult.get();
                    // get the stack trace
                    StackTraceArguments stackTraceArgs = new StackTraceArguments();
                    stackTraceArgs.setThreadId(args.getThreadId());
                    server
                            .stackTrace(stackTraceArgs)
                            .thenAcceptAsync(stackTraceResponse -> {
                                StackFrame[] stackFrames = stackTraceResponse.getStackFrames();
                                if (stackFrames != null && stackFrames.length > 0) {
                                    XBreakpoint<DAPBreakpointProperties> breakpoint = debugProcess.getBreakpointHandler().findBreakpoint(stackFrames[0]);
                                    XSuspendContext context = getSession().getSuspendContext();
                                    if (context == null) {
                                        context = new DAPSuspendContext(this);
                                    }
                                    ((DAPSuspendContext) context).addToExecutionStack(thread, stackFrames);
                                    XDebugSession session = getSession();
                                    if (breakpoint == null) {
                                        session.positionReached(context);
                                    } else {
                                        session.breakpointReached(breakpoint, null, context);
                                    }
                                }
                            });
                });
    }

    @Override
    public void terminated(TerminatedEventArguments args) {
        // TODO : manage args.getRestart()
        debugProcess.stop();
    }

    @Override
    public void dispose() {
        if (debugProtocolFuture != null) {
            debugProtocolFuture.cancel(true);
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
        threadPool.shutdown();
        for (DAPClient child : childrenClient) {
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
        IDebugProtocolServer server = getDebugProtocolServer();
        if (server == null) {
            dispose();
            return;
        }

        boolean shouldSendTerminateRequest = !sentTerminateRequest
                && isSupportsTerminateRequest()
                && debugMode == DebugMode.LAUNCH;
        if (shouldSendTerminateRequest) {
            sentTerminateRequest = true;
            server.terminate(new TerminateArguments()).thenRunAsync(this::dispose);
        } else {
            final var arguments = new DisconnectArguments();
            arguments.setTerminateDebuggee(true);
            server.disconnect(arguments).thenRunAsync(this::dispose);
        }
    }

    XDebugSession getSession() {
        return debugProcess.getSession();
    }

    public void continue_(int threadId) {
        if (debugProtocolServer == null) {
            return;
        }
        ContinueArguments args = new ContinueArguments();
        args.setThreadId(threadId);
        debugProtocolServer.continue_(args);
    }

    public void next(int threadId) {
        if (debugProtocolServer == null) {
            return;
        }
        NextArguments args = new NextArguments();
        args.setThreadId(threadId);
        debugProtocolServer.next(args);
    }

    public void stepOut(int threadId) {
        if (debugProtocolServer == null) {
            return;
        }
        StepOutArguments args = new StepOutArguments();
        args.setThreadId(threadId);
        debugProtocolServer.stepOut(args);
    }

    public void stepIn(int threadId) {
        if (debugProtocolServer == null) {
            return;
        }
        StepInArguments args = new StepInArguments();
        args.setThreadId(threadId);
        debugProtocolServer.stepIn(args);
    }

    public CompletableFuture<EvaluateResponse> evaluate(String expression, int frameId) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(null);
        }
        EvaluateArguments args = new EvaluateArguments();
        args.setExpression(expression);
        args.setFrameId(frameId);
        args.setContext(EvaluateArgumentsContext.WATCH);
        return debugProtocolServer.evaluate(args);
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
        args.setFormat(new ValueFormat());
        return debugProtocolServer.setVariable(args);
    }

    // Capabilities


    /**
     * Returns true if the debug adapter supports the 'terminate' request and false otherwise.
     *
     * @return true if the debug adapter supports the 'terminate' request and false otherwise.
     */
    public boolean isSupportsTerminateRequest() {
        var capabilities = getCapabilities();
        return capabilities != null && Boolean.TRUE.equals(capabilities.getSupportsTerminateRequest());
    }

    /**
     * Returns true if the debug adapter supports the 'completions' request and false otherwise.
     *
     * @return true if the debug adapter supports the 'completions' request and false otherwise.
     */
    public boolean isSupportsCompletionsRequest() {
        var capabilities = getCapabilities();
        return capabilities != null && Boolean.TRUE.equals(capabilities.getSupportsCompletionsRequest());
    }

    /**
     * Returns true if the debug adapter supports setting a variable to a value and false otherwise.
     *
     * @return true if the debug adapter supports setting a variable to a value and false otherwise.
     */
    public boolean isSupportsSetVariable() {
        var capabilities = getCapabilities();
        return capabilities != null && Boolean.TRUE.equals(capabilities.getSupportsSetVariable());
    }

    @NotNull
    public DebugAdapterDescriptor getServerDescriptor() {
        return debugProcess.getServerDescriptor();
    }

    @NotNull
    public Project getProject() {
        return debugProcess.getSession().getProject();
    }
}
