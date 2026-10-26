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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.console.explorer.TracingMessageConsumer;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.TransportStreams;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile;
import com.redhat.devtools.lsp4ij.dap.runInTerminal.RunInTerminalManager;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
                        // client sends zero or more setBreakpoints requests
                        monitor.checkCanceled();
                        monitor.setFraction(50d / 100d);
                        monitor.setText2("Sending breakpoints");
                        return debugProcess
                                .getBreakpointHandler()
                                .initialize(getDebugProtocolServer(), getCapabilities());
                    })
                    .thenCompose(v -> {
                        // client sends a setExceptionBreakpoints request
                        // if one or more exceptionBreakpointFilters have been defined
                        // (or if supportsConfigurationDoneRequest is not true)
                        monitor.checkCanceled();
                        monitor.setFraction(60d / 100d);
                        monitor.setText2("Sending exception breakpoints filters");

                        var filters = getCapabilities().getExceptionBreakpointFilters();
                        if (filters != null) {
                            return debugProcess
                                    .getBreakpointHandler()
                                    .sendExceptionBreakpointFilters(filters, getDebugProtocolServer());
                        }
                        return CompletableFuture.completedFuture(null);
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
                        return debugProcess
                                .getDisassemblyBreakpointHandler()
                                .initialize(getDebugProtocolServer(), getCapabilities());
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
     * return a default capabilities
     */
    @NotNull
    Capabilities getCapabilities() {
        return capabilitiesFuture.getNow(new Capabilities());
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
        if (!StringUtils.isEmpty(output)) {
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
    public CompletableFuture<RunInTerminalResponse> runInTerminal(@NotNull RunInTerminalRequestArguments args) {
        return RunInTerminalManager.getInstance(getProject()).runInTerminal(args, this);
    }

    @Override
    public void breakpoint(BreakpointEventArguments args) {
        if (BreakpointEventArgumentsReason.CHANGED.equals(args.getReason())) {
            var breakpoint = args.getBreakpoint();
            if (breakpoint != null) {
                // breakpoint with a given id changed (ex: verified changed)
                // update the Intellij Breakpoint with those information.
                debugProcess.getBreakpointHandler()
                        .updateBreakpoint(breakpoint, null);
            }
        }
    }

    @Override
    public void stopped(StoppedEventArguments args) {
        var server = getDebugProtocolServer();
        Integer threadId = args.getThreadId();
        if (server == null || threadId == null) {
            return;
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
     * there.
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
        XBreakpoint<?> breakpoint = isBreakpointStop(args) ? debugProcess.getBreakpointHandler().findBreakpoint(topFrame) : null;
        if (breakpoint != null) {
            breakpointReached(getSession(), server, breakpoint, context, topFrame.getId(), stoppedThread.getId())
                    .exceptionally(this::reportBreakpointFailure);
            return;
        }
        getSession().positionReached(context);
        if (StoppedEventArgumentsReason.EXCEPTION.equals(args.getReason())) {
            showExceptionHint(getProject(), context, server, getCapabilities(), args, DAPClient::showErrorHint);
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
                                                               @NotNull ErrorHintDisplay display) {
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
        return getExceptionHint(server, capabilities, args)
                .thenAccept(hint -> AppExecutorUtil.getAppScheduledExecutorService().schedule(
                        () -> ApplicationManager.getApplication().invokeLater(() -> display.show(editor, hint, offset)),
                        1, TimeUnit.SECONDS));
    }

    /**
     * Shows the text of an error hint in an editor, at an offset.
     */
    @FunctionalInterface
    interface ErrorHintDisplay {
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

    private static @NotNull Throwable getCause(@NotNull Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /**
     * Tells the session that the program stopped at the given breakpoint, with the value of the breakpoint's
     * "Evaluate and log" expression in the stopped frame, and resumes the program if the breakpoint doesn't suspend.
     * <p>
     * A breakpoint which doesn't suspend is sent as a logpoint, so a debug adapter which supports logpoints doesn't
     * stop at it; one which doesn't support them stops, and is resumed here once IntelliJ has logged.
     *
     * @param session    the debug session.
     * @param server     the debug adapter.
     * @param breakpoint the IntelliJ breakpoint where the program stopped.
     * @param context    the suspend context of the stop.
     * @param frameId    the DAP frame where the program stopped.
     * @param threadId   the DAP thread which stopped.
     * @return the future, completed once the session is told and the program resumed if it must be.
     */
    static CompletableFuture<@Nullable Void> breakpointReached(@NotNull XDebugSession session,
                                                               @NotNull IDebugProtocolServer server,
                                                               @NotNull XBreakpoint<?> breakpoint,
                                                               @NotNull XSuspendContext context,
                                                               int frameId,
                                                               int threadId) {
        return evaluateLogExpression(server, breakpoint, frameId)
                .thenCompose(logged -> {
                    if (session.breakpointReached(breakpoint, logged, context)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    // The breakpoint doesn't suspend: resume the program
                    ContinueArguments args = new ContinueArguments();
                    args.setThreadId(threadId);
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

    private static void doShowErrorHint(@NotNull Editor editor, @NotNull String hintText, int offset) {
        JComponent label = HintUtil.createErrorLabel(hintText);
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
        debugProcess.stop();
    }

    @Override
    public void dispose() {
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
        return debugProtocolServer
                .continue_(args);
    }

    public void next(int threadId, @Nullable SteppingGranularity granularity) {
        if (debugProtocolServer == null) {
            return;
        }
        NextArguments args = new NextArguments();
        args.setThreadId(threadId);
        args.setGranularity(granularity);
        debugProtocolServer.next(args);
    }

    public void stepOut(int threadId, SteppingGranularity granularity) {
        if (debugProtocolServer == null) {
            return;
        }
        StepOutArguments args = new StepOutArguments();
        args.setThreadId(threadId);
        args.setGranularity(granularity);
        debugProtocolServer.stepOut(args);
    }

    public void stepIn(int threadId, SteppingGranularity granularity) {
        stepIn(threadId, null, granularity);
    }

    public void stepIn(int threadId, @Nullable Integer targetId, @Nullable SteppingGranularity granularity) {
        if (debugProtocolServer == null) {
            return;
        }
        StepInArguments args = new StepInArguments();
        args.setThreadId(threadId);
        args.setTargetId(targetId);
        args.setGranularity(granularity);
        debugProtocolServer.stepIn(args);
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

    public CompletableFuture<EvaluateResponse> evaluate(@NotNull String expression,
                                                        @Nullable Integer frameId,
                                                        @NotNull String context) {
        if (debugProtocolServer == null) {
            return CompletableFuture.completedFuture(null);
        }
        EvaluateArguments args = new EvaluateArguments();
        args.setExpression(expression);
        args.setFrameId(frameId);
        args.setContext(context);
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

    // Threads

    @Override
    public void thread(ThreadEventArguments args) {
        debugProcess.refreshThread(args);
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

    public boolean canDisassemble() {
        return Boolean.TRUE.equals(getCapabilities().getSupportsDisassembleRequest());
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
