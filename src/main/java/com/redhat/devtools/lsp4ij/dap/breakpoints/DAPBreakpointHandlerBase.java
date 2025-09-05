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
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.*;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.*;

/**
 * Abstract Debug Adapter Protocol (DAP) breakpoint handler.
 */
public abstract class DAPBreakpointHandlerBase<B extends XBreakpoint<?>> extends BreakpointHandlerBase<B> {

    private static final SourceBreakpoint[] EMPTY_SOURCE_BREAKPOINTS = new SourceBreakpoint[0];
    private final @NotNull DAPExceptionBreakpointsPanel exceptionBreakpointsPanel;

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
                                            @NotNull Map<Source, List<SourceBreakpoint>> targetBreakpoints) {
        Source source = toDAPSource(sourcePosition);
        int lineNumber = getLineNumber(sourcePosition);
        SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
        sourceBreakpoint.setLine(lineNumber);
        if (conditionExpression != null) {
            // Breakpoint with condition
            sourceBreakpoint.setCondition(conditionExpression.getExpression());
        }
        List<SourceBreakpoint> sourceBreakpoints = targetBreakpoints
                .computeIfAbsent(source, s -> new ArrayList<>());
        sourceBreakpoints.add(sourceBreakpoint);
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
        for (B breakpoint : breakpoints) {
            var sourcePosition = breakpoint.getSourcePosition();
            if (sourcePosition != null) {
                addSourceBreakpoint(sourcePosition, breakpoint.getConditionExpression(), targetBreakpoints);
            }
        }

        if (temporaryBreakpoint != null) {
            if (temporaryBreakpoint.add()) {
                addSourceBreakpoint(temporaryBreakpoint.sourcePosition(), null, targetBreakpoints);
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

        final var setBreakpointsFutures = new ArrayList<CompletableFuture<Void>>();
        for (Iterator<Map.Entry<Source, List<SourceBreakpoint>>> iterator = targetBreakpoints.entrySet()
                .iterator(); iterator.hasNext(); ) {
            Map.Entry<Source, List<SourceBreakpoint>> entry = iterator.next();

            Source source = entry.getKey();
            List<SourceBreakpoint> sourceBreakpoints = entry.getValue();

            // Call 'setBreakpoints' DAP request for each Source keys.
            final var arguments = createSetBreakpointsArguments(source, sourceBreakpoints);
            if (debugProtocolServer != null) {
                // Call 'setBreakpoints' DAP request for each Source keys
                // and the given debug protocol server
                setBreakpointsFutures.add(setBreakpoints(debugProtocolServer, arguments));
            } else {
                // Call 'setBreakpoints' DAP request for each Source keys
                // for each registered debug protocol server
                for (var server : debugProtocolServers) {
                    setBreakpointsFutures.add(setBreakpoints(server, arguments));
                }
            }

            // Once we told adapter there are no breakpoints for a source file, we can stop
            // tracking that file
            if (sourceBreakpoints.isEmpty()) {
                iterator.remove();
            }
        }
        return CompletableFuture.allOf(setBreakpointsFutures.toArray(EMPTY_COMPLETABLE_FUTURES));
    }

    private CompletableFuture<Void> setBreakpoints(@NotNull IDebugProtocolServer debugProtocolServer,
                                                   @NotNull SetBreakpointsArguments arguments) {
        CompletableFuture<SetBreakpointsResponse> future = debugProtocolServer.setBreakpoints(arguments);
        return future
                .thenAccept(response -> {
                    var breakpoints = response.getBreakpoints();
                    if (breakpoints == null) {
                        return;
                    }
                    for (int i = 0; i < breakpoints.length; i++) {
                        Breakpoint breakpointResponse = breakpoints[i];
                        Source source = breakpointResponse.getSource();
                        if (source == null) {
                            source = arguments.getSource();
                        }
                        Integer line = breakpointResponse.getLine();
                        if (line == null && arguments.getBreakpoints().length > i) {
                            var sourceBreakpoint = arguments.getBreakpoints()[i];
                            line = sourceBreakpoint.getLine();
                        }
                        if (source != null && line != null) {
                            var ijBreakpoint = findBreakpoint(source, line);
                            if (ijBreakpoint != null) {
                                ijBreakpoint.putUserData(DAP_BREAKPOINT_ID, breakpointResponse.getId());
                                updateBreakpoint(breakpointResponse, ijBreakpoint);
                            }
                        }
                    }
                });
    }

    private <B extends XBreakpoint<?>> boolean supportsBreakpointType(B breakpoint) {
        return breakpoint.getType() instanceof DAPBreakpointTypeBase<?>;
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
        for (B breakpoint : breakpoints) {
            XSourcePosition breakpointPosition = breakpoint.getSourcePosition();
            if (breakpointPosition == null) {
                continue;
            }
            VirtualFile fileInBreakpoint = breakpointPosition.getFile();
            int line = breakpointPosition.getLine() + 1;
            if (fileInBreakpoint.toNioPath().equals(filePath) && line == lineNumber) {
                return breakpoint;
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
        SetExceptionBreakpointsArguments args = new SetExceptionBreakpointsArguments();
        args.setFilters(
                applicableFilters
                        .stream()
                        .map(ExceptionBreakpointsFilter::getFilter)
                        .toArray(String[]::new));
        return debugProtocolServer
                .setExceptionBreakpoints(args)
                .thenAccept(r -> {
                });
    }

}
