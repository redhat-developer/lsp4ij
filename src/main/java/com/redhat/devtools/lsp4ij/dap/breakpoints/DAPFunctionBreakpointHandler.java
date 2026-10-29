/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.FunctionBreakpoint;
import org.eclipse.lsp4j.debug.SetFunctionBreakpointsArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Debug Adapter Protocol (DAP) function breakpoint handler which is working with {@link DAPFunctionBreakpointType}.
 * <p>
 * The enabled function breakpoints are sent with 'setFunctionBreakpoints' to each DAP server of the session which
 * supports function breakpoints ('supportsFunctionBreakpoints'). The request replaces all the function breakpoints of
 * the DAP server, so each change sends all of them, and the removal of the last one sends none.
 */
public class DAPFunctionBreakpointHandler extends BreakpointHandlerBase<XBreakpoint<DAPFunctionBreakpointProperties>> {

    // The number of the last 'setFunctionBreakpoints' request sent to each DAP server: the answer to an older one, which
    // can come after the answer to a newer one, is out of date
    private final Map<IDebugProtocolServer, Long> lastRequests = Collections.synchronizedMap(new IdentityHashMap<>());

    public DAPFunctionBreakpointHandler(@NotNull XDebugSession debugSession,
                                        @NotNull DebugAdapterDescriptor debugAdapterDescriptor,
                                        @NotNull Project project) {
        super(DAPFunctionBreakpointType.class, debugSession, debugAdapterDescriptor, project);
        debugSession.addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionStopped() {
                // The breakpoints keep the icons of this session until they are computed again: IntelliJ does that for
                // line breakpoints only
                breakpoints.forEach(DAPFunctionBreakpointHandler::clearIcon);
            }
        });
    }

    @Override
    protected boolean supportsBreakpoint(XBreakpoint<DAPFunctionBreakpointProperties> breakpoint) {
        return breakpoint.getType() instanceof DAPFunctionBreakpointType;
    }

    @Override
    public void unregisterBreakpoint(@NotNull XBreakpoint<DAPFunctionBreakpointProperties> breakpoint,
                                     boolean temporary) {
        // A function breakpoint has no source position, which the base class needs to remove a line breakpoint
        if (breakpoints.remove(breakpoint)) {
            sendFunctionBreakpoints();
        }
    }

    // Takes turns with sendBreakpoints, which is synchronized too, so the DAP server gets the requests in the order
    // in which they read the breakpoints. Unlike sendBreakpoints, it sends a request when none are left, which
    // removes the last one from the DAP server.
    private synchronized CompletableFuture<@Nullable Void> sendFunctionBreakpoints() {
        return doSendBreakpoints(null, null);
    }

    @Override
    protected @NotNull CompletableFuture<@Nullable Void> doSendBreakpoints(@Nullable IDebugProtocolServer debugProtocolServer,
                                                                           @Nullable TemporaryBreakpoint temporaryBreakpoint) {
        // The IJ breakpoint of each function breakpoint of the request, in the same order
        List<XBreakpoint<DAPFunctionBreakpointProperties>> ijBreakpoints = List.copyOf(breakpoints);
        SetFunctionBreakpointsArguments arguments = createSetFunctionBreakpointsArguments(ijBreakpoints);
        List<IDebugProtocolServer> servers = debugProtocolServer != null ? List.of(debugProtocolServer) : debugProtocolServers;
        final var setFunctionBreakpointsFutures = new ArrayList<CompletableFuture<Void>>();
        for (var server : servers) {
            if (isSupportsFunctionBreakpoints(getCapabilities(server))) {
                long request = lastRequests.merge(server, 1L, Long::sum);
                setFunctionBreakpointsFutures.add(setFunctionBreakpoints(server, arguments, ijBreakpoints, request));
            }
        }
        return CompletableFuture.allOf(setFunctionBreakpointsFutures.toArray(EMPTY_COMPLETABLE_FUTURES));
    }

    /**
     * Sends the 'setFunctionBreakpoints' request, and gives each IJ breakpoint the id and the status of its DAP
     * breakpoint. When the request fails, each IJ breakpoint is shown invalid with the error, and the session goes on.
     * The answer is ignored once a newer request has been sent to the DAP server.
     *
     * @param debugProtocolServer the DAP server.
     * @param arguments           the 'setFunctionBreakpoints' arguments.
     * @param ijBreakpoints       the IJ breakpoint of each function breakpoint of the arguments, in the same order.
     * @param request             the number of the request.
     * @return the future.
     */
    private CompletableFuture<Void> setFunctionBreakpoints(@NotNull IDebugProtocolServer debugProtocolServer,
                                                           @NotNull SetFunctionBreakpointsArguments arguments,
                                                           @NotNull List<XBreakpoint<DAPFunctionBreakpointProperties>> ijBreakpoints,
                                                           long request) {
        return debugProtocolServer.setFunctionBreakpoints(arguments)
                .handle((response, error) -> {
                    if (!Objects.equals(lastRequests.get(debugProtocolServer), request)) {
                        // A newer request replaced these breakpoints: its answer gives their ids and status
                        return null;
                    }
                    if (error != null) {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                        String message = cause.getMessage();
                        ijBreakpoints.forEach(ijBreakpoint -> setBreakpointInvalid(ijBreakpoint, message));
                        return null;
                    }
                    var breakpoints = response != null ? response.getBreakpoints() : null;
                    if (breakpoints == null) {
                        return null;
                    }
                    // The response has a breakpoint per function breakpoint of the request, in the same order
                    for (int i = 0; i < Math.min(breakpoints.length, ijBreakpoints.size()); i++) {
                        var ijBreakpoint = ijBreakpoints.get(i);
                        ijBreakpoint.putUserData(DAP_BREAKPOINT_ID, breakpoints[i].getId());
                        updateBreakpoint(breakpoints[i], ijBreakpoint);
                    }
                    return null;
                });
    }

    /**
     * Returns the 'setFunctionBreakpoints' arguments for the given IJ breakpoints, in the same order: each has the name
     * of its function, its condition if it is enabled, and its hit condition if it has one.
     *
     * @param ijBreakpoints the IJ function breakpoints.
     * @return the 'setFunctionBreakpoints' arguments.
     */
    static @NotNull SetFunctionBreakpointsArguments createSetFunctionBreakpointsArguments(@NotNull List<? extends XBreakpoint<DAPFunctionBreakpointProperties>> ijBreakpoints) {
        var arguments = new SetFunctionBreakpointsArguments();
        arguments.setBreakpoints(ijBreakpoints.stream()
                .map(DAPFunctionBreakpointHandler::toFunctionBreakpoint)
                .toArray(FunctionBreakpoint[]::new));
        return arguments;
    }

    private static @NotNull FunctionBreakpoint toFunctionBreakpoint(@NotNull XBreakpoint<DAPFunctionBreakpointProperties> ijBreakpoint) {
        var properties = ijBreakpoint.getProperties();
        var functionBreakpoint = new FunctionBreakpoint();
        functionBreakpoint.setName(properties.getName());
        XExpression condition = ijBreakpoint.getConditionExpression();
        if (condition != null && StringUtils.isNotBlank(condition.getExpression())) {
            functionBreakpoint.setCondition(condition.getExpression());
        }
        if (StringUtils.isNotBlank(properties.getHitCondition())) {
            functionBreakpoint.setHitCondition(properties.getHitCondition());
        }
        return functionBreakpoint;
    }

    /**
     * Returns true if the DAP server supports function breakpoints and false otherwise.
     *
     * @param capabilities the DAP server capabilities.
     * @return true if the DAP server supports function breakpoints and false otherwise.
     */
    static boolean isSupportsFunctionBreakpoints(@NotNull Capabilities capabilities) {
        return Boolean.TRUE.equals(capabilities.getSupportsFunctionBreakpoints());
    }

    /**
     * Shows the given function breakpoint as verified in the session.
     *
     * @param ijBreakpoint the IJ function breakpoint.
     */
    @Override
    protected void setBreakpointVerified(@NotNull XBreakpoint<DAPFunctionBreakpointProperties> ijBreakpoint) {
        Icon icon = ijBreakpoint.getSuspendPolicy() == SuspendPolicy.NONE
                ? AllIcons.Debugger.Db_verified_no_suspend_method_breakpoint
                : AllIcons.Debugger.Db_verified_method_breakpoint;
        updateBreakpointPresentation(ijBreakpoint, icon, null);
    }

    /**
     * Shows the given function breakpoint as invalid in the session, with the given message.
     *
     * @param ijBreakpoint the IJ function breakpoint.
     * @param message      the message of the DAP server, or null.
     */
    @Override
    protected void setBreakpointInvalid(@NotNull XBreakpoint<DAPFunctionBreakpointProperties> ijBreakpoint,
                                        @Nullable String message) {
        updateBreakpointPresentation(ijBreakpoint, AllIcons.Debugger.Db_invalid_breakpoint, message);
    }
}
