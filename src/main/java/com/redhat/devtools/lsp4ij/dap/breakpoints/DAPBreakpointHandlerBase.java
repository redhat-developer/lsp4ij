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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.*;

/**
 * Abstract Debug Adapter Protocol (DAP) breakpoint handler.
 */
public abstract class DAPBreakpointHandlerBase<B extends XBreakpoint<?>> extends BreakpointHandlerBase<B> {

    private static final SourceBreakpoint[] EMPTY_SOURCE_BREAKPOINTS = new SourceBreakpoint[0];

    private final @NotNull DAPExceptionBreakpointsPanel exceptionBreakpointsPanel;
    private final Set<B> shadowedBreakpoints = ConcurrentHashMap.newKeySet();
    // The sources which have breakpoints in the debug adapter, with a token of their latest 'setBreakpoints' request.
    // A cleared source stays until its clear succeeded, so that a failed clear is sent again.
    private final Map<Source, Object> sentSources = new ConcurrentHashMap<>();

    public DAPBreakpointHandlerBase(@NotNull Class<? extends XBreakpointType<B, ?>> breakpointTypeClass,
                                    @NotNull XDebugSession debugSession,
                                    @NotNull DebugAdapterDescriptor debugAdapterDescriptor,
                                    @NotNull Project project) {
        super(breakpointTypeClass, debugSession, debugAdapterDescriptor, project);
        this.exceptionBreakpointsPanel = new DAPExceptionBreakpointsPanel(this);
    }

    /**
     * Returns whether this target can install the given breakpoint.
     *
     * @param breakpoint breakpoint to consider
     * @return whether this target can install the given breakpoint
     */
    @Override
    public boolean supportsBreakpoint(B breakpoint) {
        if (!supportsBreakpointType(breakpoint)) {
            return false;
        }

        XSourcePosition sourcePosition = breakpoint.getSourcePosition();
        if (sourcePosition == null) {
            return false;
        }
        return debugAdapterDescriptor.isDebuggableFile(sourcePosition.getFile(), project);
    }

    private static @NotNull SetBreakpointsArguments createSetBreakpointsArguments(@NotNull Source source,
                                                                                  @NotNull List<SourceBreakpoint> sourceBreakpoints) {
        int[] lines = sourceBreakpoints
                .stream()
                .mapToInt(SourceBreakpoint::getLine)
                .toArray();

        final var arguments = new SetBreakpointsArguments();
        arguments.setSource(source);
        arguments.setLines(lines);
        arguments.setBreakpoints(sourceBreakpoints.toArray(EMPTY_SOURCE_BREAKPOINTS));
        arguments.setSourceModified(false);
        return arguments;
    }

    private static void addSourceBreakpoint(@NotNull XSourcePosition sourcePosition,
                                            @Nullable XExpression conditionExpression,
                                            @Nullable String logMessage,
                                            @NotNull Map<Source, List<SourceBreakpoint>> targetBreakpoints) {
        Source source = toDAPSource(sourcePosition);
        int lineNumber = getLineNumber(sourcePosition);
        SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
        sourceBreakpoint.setLine(lineNumber);
        if (conditionExpression != null) {
            // Breakpoint with condition
            sourceBreakpoint.setCondition(conditionExpression.getExpression());
        }
        if (logMessage != null) {
            // Logpoint: the debug adapter logs the message instead of stopping
            sourceBreakpoint.setLogMessage(logMessage);
        }
        List<SourceBreakpoint> sourceBreakpoints = targetBreakpoints
                .computeIfAbsent(source, s -> new ArrayList<>());
        sourceBreakpoints.add(sourceBreakpoint);
    }

    /**
     * Returns the DAP log message of a breakpoint which doesn't suspend, and null otherwise.
     * <p>
     * For such a breakpoint, IntelliJ logs "Breakpoint reached" and the value of the "Evaluate and log" expression.
     * As a DAP logpoint, the debug adapter logs them without stopping, with the expression in braces to be
     * interpolated. A debug adapter which doesn't support logpoints ignores the message and stops: the client then
     * evaluates the expression, logs and resumes.
     *
     * @param breakpoint the IntelliJ breakpoint.
     * @return the DAP log message of a breakpoint which doesn't suspend, and null otherwise.
     */
    static @Nullable String getLogMessage(@NotNull XBreakpoint<?> breakpoint) {
        if (breakpoint.getSuspendPolicy() != SuspendPolicy.NONE) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        XSourcePosition sourcePosition = breakpoint.getSourcePosition();
        if (breakpoint.isLogMessage() && sourcePosition != null) {
            parts.add("Breakpoint reached at " + sourcePosition.getFile().getName() + ":" + getLineNumber(sourcePosition));
        }
        XExpression logExpression = breakpoint.getLogExpressionObject();
        if (logExpression != null && StringUtils.isNotBlank(logExpression.getExpression())) {
            parts.add("{" + logExpression.getExpression() + "}");
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private static @NotNull Source toDAPSource(@NotNull XSourcePosition sourcePosition) {
        Source source = new Source();
        source.setPath(getFilePath(sourcePosition.getFile()));
        source.setName(getFileName(sourcePosition.getFile()));
        return source;
    }

    private static int getLineNumber(@NotNull XSourcePosition sourcePosition) {
        return sourcePosition.getLine() + 1;
    }

    @Override
    protected @NotNull CompletableFuture<@Nullable Void> doSendBreakpoints(@Nullable IDebugProtocolServer debugProtocolServer,
                                                                           @Nullable TemporaryBreakpoint temporaryBreakpoint) {
        // Convert list of IJ XBreakpoint -> LSP SourceBreakpoint
        Map<Source, List<SourceBreakpoint>> targetBreakpoints = new HashMap<>();
        // The IJ breakpoint of each source breakpoint, in the same order, and null for the temporary breakpoint
        Map<Source, List<@Nullable B>> targetIJBreakpoints = new HashMap<>();
        Set<LineLocation> dapBreakpointLines = getDAPBreakpointLines();
        for (B breakpoint : breakpoints) {
            var sourcePosition = breakpoint.getSourcePosition();
            if (sourcePosition != null && !isShadowed(breakpoint, dapBreakpointLines)) {
                addSourceBreakpoint(sourcePosition, breakpoint.getConditionExpression(), getLogMessage(breakpoint), targetBreakpoints);
                targetIJBreakpoints.computeIfAbsent(toDAPSource(sourcePosition), s -> new ArrayList<>()).add(breakpoint);
            }
        }

        // The registered temporary breakpoint, as of "Run to Cursor", stays in each request for its source
        XSourcePosition registered = getRegisteredTemporaryBreakpoint();
        if (registered != null) {
            addSourceBreakpoint(registered, null, null, targetBreakpoints);
            targetIJBreakpoints.computeIfAbsent(toDAPSource(registered), s -> new ArrayList<>()).add(null);
        }

        if (temporaryBreakpoint != null) {
            if (temporaryBreakpoint.add()) {
                addSourceBreakpoint(temporaryBreakpoint.sourcePosition(), null, null, targetBreakpoints);
                targetIJBreakpoints.computeIfAbsent(toDAPSource(temporaryBreakpoint.sourcePosition()), s -> new ArrayList<>()).add(null);
            } else {
                // Removed IntelliJ breakpoint
                // Create a Source with empty source breakpoints if it doesn't exist,
                // in order to disable removed breakpoint
                // if the source contained only this breakpoint.
                Source source = toDAPSource(temporaryBreakpoint.sourcePosition());
                targetBreakpoints
                        .computeIfAbsent(source, s -> new ArrayList<>());
            }
        }

        // Also send the sources which had breakpoints and have none anymore, so that they are cleared even when
        // the removed breakpoint doesn't know its old source (ex: its file was moved or renamed)
        for (Source source : sentSources.keySet()) {
            targetBreakpoints.computeIfAbsent(source, s -> new ArrayList<>());
        }

        final var setBreakpointsFutures = new ArrayList<CompletableFuture<Void>>();
        for (Iterator<Map.Entry<Source, List<SourceBreakpoint>>> iterator = targetBreakpoints.entrySet()
                .iterator(); iterator.hasNext(); ) {
            Map.Entry<Source, List<SourceBreakpoint>> entry = iterator.next();

            Source source = entry.getKey();
            List<SourceBreakpoint> sourceBreakpoints = entry.getValue();

            // Call 'setBreakpoints' DAP request for each Source keys.
            final var arguments = createSetBreakpointsArguments(source, sourceBreakpoints);
            List<@Nullable B> ijBreakpoints = targetIJBreakpoints.getOrDefault(source, List.of());
            // A later request for this source replaces the token, so that the response of an earlier request
            // is not applied anymore, and the response of an earlier clear cannot stop tracking it
            final var token = new Object();
            sentSources.put(source, token);
            final var sourceFutures = new ArrayList<CompletableFuture<Void>>();
            if (debugProtocolServer != null) {
                // Call 'setBreakpoints' DAP request for each Source keys
                // and the given debug protocol server
                sourceFutures.add(setBreakpoints(debugProtocolServer, arguments, ijBreakpoints, token));
            } else {
                // Call 'setBreakpoints' DAP request for each Source keys
                // for each registered debug protocol server
                for (var server : debugProtocolServers) {
                    sourceFutures.add(setBreakpoints(server, arguments, ijBreakpoints, token));
                }
            }
            setBreakpointsFutures.addAll(sourceFutures);

            if (sourceBreakpoints.isEmpty()) {
                iterator.remove();
                // Once the adapter accepted that there are no breakpoints for a source file, we can stop
                // tracking that file
                CompletableFuture.allOf(sourceFutures.toArray(EMPTY_COMPLETABLE_FUTURES))
                        .thenRun(() -> sentSources.remove(source, token));
            }
        }
        return CompletableFuture.allOf(setBreakpointsFutures.toArray(EMPTY_COMPLETABLE_FUTURES));
    }

    /**
     * Sends the 'setBreakpoints' request, and gives each IJ breakpoint the id of its DAP breakpoint.
     *
     * @param debugProtocolServer the DAP server.
     * @param arguments           the 'setBreakpoints' arguments.
     * @param ijBreakpoints       the IJ breakpoint of each source breakpoint of the arguments, in the same order, and
     *                            null for the temporary breakpoint.
     * @param token               the token of the request in {@link #sentSources}.
     * @return the future.
     */
    private CompletableFuture<Void> setBreakpoints(@NotNull IDebugProtocolServer debugProtocolServer,
                                                   @NotNull SetBreakpointsArguments arguments,
                                                   @NotNull List<@Nullable B> ijBreakpoints,
                                                   @NotNull Object token) {
        CompletableFuture<SetBreakpointsResponse> future = debugProtocolServer.setBreakpoints(arguments);
        return future
                .thenAccept(response -> {
                    var breakpoints = response.getBreakpoints();
                    if (breakpoints == null) {
                        return;
                    }
                    // A later request for this source replaced this one: its breakpoints may have changed or moved
                    // to another source since, so this response must not override the state of the later one
                    if (sentSources.get(arguments.getSource()) != token) {
                        return;
                    }
                    for (int i = 0; i < breakpoints.length; i++) {
                        Breakpoint breakpointResponse = breakpoints[i];
                        B ijBreakpoint;
                        if (breakpoints.length == ijBreakpoints.size()) {
                            // The response has a breakpoint per source breakpoint, in the same order: when two are on
                            // the same line, such as a breakpoint and the temporary breakpoint, each keeps its own id
                            ijBreakpoint = ijBreakpoints.get(i);
                        } else {
                            ijBreakpoint = findBreakpoint(breakpointResponse, arguments, i);
                        }
                        // A native breakpoint shadowed since the request was sent is not sent anymore:
                        // a late response must not give it back the DAP id it had
                        if (ijBreakpoint != null && !shadowedBreakpoints.contains(ijBreakpoint)) {
                            ijBreakpoint.putUserData(DAP_BREAKPOINT_ID, breakpointResponse.getId());
                            updateBreakpoint(breakpointResponse, ijBreakpoint);
                        }
                    }
                });
    }

    /**
     * A forwarded native line breakpoint and a DAP breakpoint can be on the same line. Only the DAP
     * breakpoint is sent: two breakpoints at one location with different settings (condition, log,
     * suspend policy) cannot be merged without silently dropping one of them, and the DAP protocol
     * leaves it to each debug adapter how it handles duplicates. The native breakpoint is shown as
     * inactive for this debug session only, with the icon IntelliJ uses for a breakpoint which is
     * inactive because of another breakpoint; it stays enabled and keeps working with the language's
     * own debugger.
     */
    @Override
    protected void onBreakpointsChanged(@NotNull B breakpoint) {
        // Check all breakpoints, not only the changed line: when a breakpoint moves to another line,
        // IntelliJ unregisters it with its new position, so the old line is not known anymore.
        Set<LineLocation> dapBreakpointLines = getDAPBreakpointLines();
        for (B candidate : breakpoints) {
            updateShadowedPresentation(candidate, isShadowed(candidate, dapBreakpointLines));
        }
        if (!breakpoints.contains(breakpoint)) {
            updateShadowedPresentation(breakpoint, false);
        }
    }

    private void updateShadowedPresentation(@NotNull B breakpoint, boolean shadowed) {
        if (!(breakpoint instanceof XLineBreakpoint<?> lineBreakpoint)) {
            return;
        }
        if (shadowed) {
            if (shadowedBreakpoints.add(breakpoint)) {
                // The breakpoint is not sent anymore: forget the DAP id it had before
                breakpoint.putUserData(DAP_BREAKPOINT_ID, null);
                // Not an error: the inactive dependent icon instead of the invalid one
                debugSession.updateBreakpointPresentation(lineBreakpoint,
                        lineBreakpoint.getType().getInactiveDependentIcon(), DAPBundle.message("dap.breakpoints.shadowed"));
            }
        } else if (shadowedBreakpoints.remove(breakpoint)) {
            // Reset the presentation; the response of the debug adapter updates it when the breakpoint is sent
            debugSession.updateBreakpointPresentation(lineBreakpoint, null, null);
        }
    }

    /**
     * Returns true if the given breakpoint is a native line breakpoint with a DAP breakpoint on the same line.
     *
     * @param breakpoint the breakpoint.
     * @return true if the given breakpoint is a native line breakpoint with a DAP breakpoint on the same line.
     */
    boolean isShadowed(@NotNull B breakpoint) {
        return isShadowed(breakpoint, getDAPBreakpointLines());
    }

    /**
     * Returns true if the given breakpoint is a native line breakpoint on one of the given DAP breakpoint lines.
     * <p>
     * The DAP breakpoint lines are collected once by the caller (see {@link #getDAPBreakpointLines()}), so that
     * checking all breakpoints stays linear instead of scanning all breakpoints for each of them.
     *
     * @param breakpoint         the breakpoint.
     * @param dapBreakpointLines the lines of the DAP breakpoints.
     * @return true if the given breakpoint is a native line breakpoint on one of the given DAP breakpoint lines.
     */
    private static boolean isShadowed(@NotNull XBreakpoint<?> breakpoint,
                                      @NotNull Set<LineLocation> dapBreakpointLines) {
        if (breakpoint.getType() instanceof DAPBreakpointTypeBase<?> || dapBreakpointLines.isEmpty()) {
            return false;
        }
        XSourcePosition position = breakpoint.getSourcePosition();
        return position != null && dapBreakpointLines.contains(new LineLocation(position.getFile(), position.getLine()));
    }

    /**
     * Returns the file and line of each DAP breakpoint.
     *
     * @return the file and line of each DAP breakpoint.
     */
    private @NotNull Set<LineLocation> getDAPBreakpointLines() {
        Set<LineLocation> lines = new HashSet<>();
        for (B breakpoint : breakpoints) {
            if (breakpoint.getType() instanceof DAPBreakpointTypeBase<?>) {
                XSourcePosition position = breakpoint.getSourcePosition();
                if (position != null) {
                    lines.add(new LineLocation(position.getFile(), position.getLine()));
                }
            }
        }
        return lines;
    }

    /**
     * A line (0-based) of a file.
     */
    private record LineLocation(@NotNull VirtualFile file, int line) {
    }

    @Override
    protected boolean hasSentBreakpoints() {
        return !sentSources.isEmpty();
    }

    @Override
    public void dispose() {
        super.dispose();
        shadowedBreakpoints.clear();
        sentSources.clear();
    }

    private @Nullable B findBreakpoint(@NotNull Breakpoint breakpointResponse,
                                       @NotNull SetBreakpointsArguments arguments,
                                       int index) {
        Source source = breakpointResponse.getSource();
        if (source == null) {
            source = arguments.getSource();
        }
        Integer line = breakpointResponse.getLine();
        if (line == null && arguments.getBreakpoints().length > index) {
            var sourceBreakpoint = arguments.getBreakpoints()[index];
            line = sourceBreakpoint.getLine();
        }
        return source != null && line != null ? findBreakpoint(source, line) : null;
    }

    private <B extends XBreakpoint<?>> boolean supportsBreakpointType(B breakpoint) {
        // A DAP breakpoint is always supported. A language's NATIVE line breakpoint is supported when
        // its file is mapped to this DAP server, so that while a DAP session runs it can be forwarded
        // (by DAPForwardedLineBreakpointHandler) while keeping its native type for other sessions.
        if (breakpoint.getType() instanceof DAPBreakpointTypeBase<?>) {
            return true;
        }
        return debugAdapterDescriptor.isDebuggableLineBreakpoint(breakpoint, project);
    }

    /**
     * Returns the IntelliJ breakpoint from the DAP stack frame and null otherwise.
     *
     * @param stackFrame the DAP stack frame.
     * @return the IntelliJ breakpoint from the DAP stack frame and null otherwise.
     */
    @Nullable
    public B findBreakpoint(@NotNull StackFrame stackFrame) {
        return findBreakpoint(stackFrame.getSource(), stackFrame.getLine());
    }

    /**
     * Returns the IntelliJ breakpoint from the DAP source and given line and null otherwise.
     *
     * @param source     the DAP source.
     * @param lineNumber the line number.
     * @return the IntelliJ breakpoint from the DAP source and given line and null otherwise.
     */
    @Nullable
    private B findBreakpoint(@Nullable Source source,
                             int lineNumber) {
        if (source == null) {
            return null;
        }
        Path filePath = getValidFilePath(source);
        if (filePath == null) {
            return null;
        }
        Set<LineLocation> dapBreakpointLines = getDAPBreakpointLines();
        for (B breakpoint : breakpoints) {
            XSourcePosition breakpointPosition = breakpoint.getSourcePosition();
            if (breakpointPosition == null || isShadowed(breakpoint, dapBreakpointLines)) {
                // A shadowed breakpoint is not sent: the DAP breakpoint on the same line is the one reached
                continue;
            }
            VirtualFile fileInBreakpoint = breakpointPosition.getFile();
            int line = breakpointPosition.getLine() + 1;
            if (fileInBreakpoint.isInLocalFileSystem()) {
                if (fileInBreakpoint.toNioPath().equals(filePath) && line == lineNumber) {
                    return breakpoint;
                }
            } else {
                // Virtual files (e.g. DAPFile backed by LightVirtualFile) don't support toNioPath().
                // Fall back to comparing the VirtualFile path as a string. See #1447.
                if (fileInBreakpoint.getPath().equals(filePath.toString()) && line == lineNumber) {
                    return breakpoint;
                }
            }
        }
        return null;
    }

    /**
     * Returns the exception breakpoints panel which host exception breakpoint filter
     * get during the initialization of the DAP server.
     *
     * @return the exception breakpoints panel which host exception breakpoint filter
     * * get during the initialization of the DAP server.
     */
    public @NotNull DAPExceptionBreakpointsPanel getExceptionBreakpointsPanel() {
        return exceptionBreakpointsPanel;
    }

    /**
     * Returns the DAP server descriptor.
     *
     * @return the DAP server descriptor.
     */
    public @NotNull DebugAdapterDescriptor getDebugAdapterDescriptor() {
        return debugAdapterDescriptor;
    }

    /**
     * Send exception breakpoint filters to the all initialized DAP servers.
     */
    public void sendExceptionBreakpointFilters() {
        var applicableFilters = getExceptionBreakpointsPanel().getApplicableFilters();
        for (var server : debugProtocolServers) {
            sendExceptionBreakpointFilters(applicableFilters, server);
        }
    }

    /**
     * Send exception breakpoint filters to the given server.
     *
     * @param filters             all filters (enabled/disabled) coming from the DAP server.
     * @param debugProtocolServer the DAP server.
     * @return the future.
     */
    public CompletableFuture<Void> sendExceptionBreakpointFilters(ExceptionBreakpointsFilter[] filters,
                                                                  IDebugProtocolServer debugProtocolServer) {
        var applicableFilters = getExceptionBreakpointsPanel()
                .refresh(filters);
        return sendExceptionBreakpointFilters(
                applicableFilters, debugProtocolServer);
    }

    /**
     * Send exception breakpoint filters to the given server.
     *
     * @param applicableFilters   the applicable exception breakpoint filters.
     * @param debugProtocolServer the DAP server.
     * @return the future.
     */
    private CompletableFuture<Void> sendExceptionBreakpointFilters(Collection<ExceptionBreakpointsFilter> applicableFilters,
                                                                   IDebugProtocolServer debugProtocolServer) {
        boolean supportsFilterOptions = Boolean.TRUE.equals(getCapabilities(debugProtocolServer).getSupportsExceptionFilterOptions());
        SetExceptionBreakpointsArguments args = createSetExceptionBreakpointsArguments(applicableFilters,
                getExceptionBreakpointsPanel()::getCondition,
                supportsFilterOptions);
        return debugProtocolServer
                .setExceptionBreakpoints(args)
                .thenAccept(r -> {
                });
    }

    /**
     * Returns the 'setExceptionBreakpoints' arguments for the given enabled filters. A filter which supports a
     * condition and has one is sent with it in the filter options, when the DAP server supports them; any other
     * filter is sent by its id.
     *
     * @param applicableFilters     the enabled exception breakpoint filters.
     * @param conditions            the condition of a filter id, or null.
     * @param supportsFilterOptions whether the DAP server supports exception filter options.
     * @return the 'setExceptionBreakpoints' arguments.
     */
    static @NotNull SetExceptionBreakpointsArguments createSetExceptionBreakpointsArguments(@NotNull Collection<ExceptionBreakpointsFilter> applicableFilters,
                                                                                           @NotNull Function<String, @Nullable String> conditions,
                                                                                           boolean supportsFilterOptions) {
        List<String> filters = new ArrayList<>();
        List<ExceptionFilterOptions> filterOptions = new ArrayList<>();
        for (var filter : applicableFilters) {
            String condition = conditions.apply(filter.getFilter());
            if (supportsFilterOptions && Boolean.TRUE.equals(filter.getSupportsCondition()) && StringUtils.isNotBlank(condition)) {
                ExceptionFilterOptions options = new ExceptionFilterOptions();
                options.setFilterId(filter.getFilter());
                options.setCondition(condition);
                filterOptions.add(options);
            } else {
                filters.add(filter.getFilter());
            }
        }
        SetExceptionBreakpointsArguments args = new SetExceptionBreakpointsArguments();
        args.setFilters(filters.toArray(String[]::new));
        if (!filterOptions.isEmpty()) {
            args.setFilterOptions(filterOptions.toArray(ExceptionFilterOptions[]::new));
        }
        return args;
    }

}
