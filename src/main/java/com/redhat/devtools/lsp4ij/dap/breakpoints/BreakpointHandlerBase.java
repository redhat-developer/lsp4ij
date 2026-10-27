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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for DAP/Disassembly breakpoint handler.
 *
 * @param <B> the breakpoint
 */
public abstract class BreakpointHandlerBase<B extends XBreakpoint<?>> extends XBreakpointHandler<B> implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BreakpointHandlerBase.class);

    protected static final CompletableFuture<?>[] EMPTY_COMPLETABLE_FUTURES = new CompletableFuture[0];
    protected static final @NotNull Key<Integer> DAP_BREAKPOINT_ID = Key.create("dap.breakpoint.id");

    protected final @NotNull XDebugSession debugSession;
    protected final @NotNull DebugAdapterDescriptor debugAdapterDescriptor;
    protected final @NotNull Project project;
    protected final List<IDebugProtocolServer> debugProtocolServers = ContainerUtil.createConcurrentList();
    protected final List<B> breakpoints = ContainerUtil.createConcurrentList();
    private final Map<IDebugProtocolServer, Capabilities> capabilities = Collections.synchronizedMap(new IdentityHashMap<>());

    public BreakpointHandlerBase(@NotNull Class<? extends XBreakpointType<B, ?>> breakpointTypeClass,
                                 @NotNull XDebugSession debugSession,
                                 @NotNull DebugAdapterDescriptor debugAdapterDescriptor,
                                 @NotNull Project project) {
        super(breakpointTypeClass);
        this.debugSession = debugSession;
        this.debugAdapterDescriptor = debugAdapterDescriptor;
        this.project = project;
    }

    public CompletableFuture<@Nullable Void> initialize(@NotNull IDebugProtocolServer debugProtocolServer,
                                                        @NotNull Capabilities capabilities) {
        this.debugProtocolServers.add(debugProtocolServer);
        this.capabilities.put(debugProtocolServer, capabilities);
        return sendBreakpoints(debugProtocolServer, null);
    }

    /**
     * Returns the capabilities of the given initialized DAP server, and empty capabilities otherwise.
     *
     * @param debugProtocolServer the DAP server.
     * @return the capabilities of the given initialized DAP server, and empty capabilities otherwise.
     */
    protected @NotNull Capabilities getCapabilities(@NotNull IDebugProtocolServer debugProtocolServer) {
        var serverCapabilities = capabilities.get(debugProtocolServer);
        return serverCapabilities != null ? serverCapabilities : new Capabilities();
    }

    @Override
    public void registerBreakpoint(@NotNull B breakpoint) {
        if (!breakpoint.isEnabled()) {
            return;
        }

        if (!supportsBreakpoint(breakpoint)) {
            // Log why the breakpoint is not supported to help diagnose issues like #1433
            var sourcePosition = breakpoint.getSourcePosition();
            LOGGER.warn("Breakpoint not supported - enabled={}, sourcePosition={}, type={}",
                       breakpoint.isEnabled(),
                       sourcePosition,
                       breakpoint.getType().getClass().getSimpleName());

            // Mark the breakpoint as invalid so the user sees visual feedback
            if (breakpoint instanceof XLineBreakpoint<?> lineBreakpoint) {
                String message = sourcePosition == null
                    ? DAPBundle.message("dap.breakpoints.location.not.available")
                    : DAPBundle.message("dap.breakpoints.file.not.debuggable");
                debugSession.setBreakpointInvalid(lineBreakpoint, message);
            }
            return;
        }

        breakpoints.add(breakpoint);
        onBreakpointsChanged(breakpoint);
        sendBreakpoints(null, null);
    }

    @Override
    public void unregisterBreakpoint(@NotNull B breakpoint,
                                     boolean temporary) {
        // Remove the breakpoint by identity, without checking supportsBreakpoint(): IntelliJ unregisters
        // a changed breakpoint with its new location, which may not be supported anymore (ex: its file
        // was moved out of the mapped files). Checking it kept the breakpoint, which was then sent again
        // at its new location, while its old source was never cleared in the debug adapter.
        if (!breakpoints.remove(breakpoint)) {
            // Not registered (ex: not supported when it was registered)
            return;
        }
        onBreakpointsChanged(breakpoint);
        // DAP replaces breakpoints per source, not per session. Include the removed
        // source even when other files still have breakpoints, so its last breakpoint
        // is cleared by an empty setBreakpoints request.
        var sourcePosition = breakpoint.getSourcePosition();
        sendBreakpoints(null, sourcePosition != null ? new TemporaryBreakpoint(sourcePosition, false) : null);
    }

    @Override
    public void dispose() {
        debugProtocolServers.clear();
        capabilities.clear();
    }

    protected CompletableFuture<@Nullable Void> sendBreakpoints(@Nullable IDebugProtocolServer debugProtocolServer,
                                                                @Nullable TemporaryBreakpoint temporaryBreakpoint) {
        if (breakpoints.isEmpty() && temporaryBreakpoint == null && !hasSentBreakpoints()) {
            // No breakpoints to send, and none to clear
            return CompletableFuture.completedFuture(null);
        }
        if (debugProtocolServers.isEmpty()) {
            // DAP server not yet initialized - breakpoints are stored and will be sent
            // when initialize() is called (which calls sendBreakpoints with the server)
            return CompletableFuture.completedFuture(null);
        }
        return doSendBreakpoints(debugProtocolServer, temporaryBreakpoint);
    }

    /**
     * Returns true if breakpoints sent to the debug adapter may still have to be cleared, and false otherwise.
     *
     * @return true if breakpoints sent to the debug adapter may still have to be cleared, and false otherwise.
     */
    protected boolean hasSentBreakpoints() {
        return false;
    }

    /**
     * Update IntelliJ breakpoint with the given DAP breakpoint information.
     *
     * @param breakpoint   the DAP breakpoint information.
     * @param ijBreakpoint the IJ Breakpoint and null otherwise.
     */
    public void updateBreakpoint(@NotNull Breakpoint breakpoint,
                                 @Nullable B ijBreakpoint) {
        if (ijBreakpoint == null) {
            // Retrieve the IntelliJ breakpoint by the id.
            ijBreakpoint = findBreakpointById(breakpoint.getId());
        }
        if (ijBreakpoint instanceof XLineBreakpoint<?> lineBreakpoint) {
            // Update the IntelliJ breakpoint according the DAP breakpoint 'verified' flag.
            if (breakpoint.isVerified()) {
                debugSession.setBreakpointVerified(lineBreakpoint);
            } else {
                debugSession.setBreakpointInvalid(lineBreakpoint, breakpoint.getMessage());
            }
        }
    }


    /**
     * Returns the IntelliJ breakpoint from the DAP breakpoint id and null otherwise.
     *
     * @param id the DAP breakpoint id.
     * @return the IntelliJ breakpoint from the DAP breakpoint id and null otherwise.
     */
    @Nullable
    public B findBreakpointById(@Nullable Integer id) {
        if (id == null) {
            return null;
        }
        for (var breakpoint : breakpoints) {
            Integer breakpointId = breakpoint.getUserData(DAP_BREAKPOINT_ID);
            if (id.equals(breakpointId)) {
                return breakpoint;
            }
        }
        return null;
    }

    /**
     * Register the given source position as temporary breakpoint.
     *
     * @param sourcePosition the temporary breakpoint.
     * @return the completable future.
     */
    public CompletableFuture<@Nullable Void> registerTemporaryBreakpoint(@NotNull XSourcePosition sourcePosition) {
        return sendBreakpoints(null, new TemporaryBreakpoint(sourcePosition, true));
    }

    /**
     * Un-register the given source position as temporary breakpoint.
     *
     * @param sourcePosition the temporary breakpoint.
     * @return the completable future.
     */
    public CompletableFuture<@Nullable Void> unregisterTemporaryBreakpoint(@NotNull XSourcePosition sourcePosition) {
        return sendBreakpoints(null, new TemporaryBreakpoint(sourcePosition, false));
    }

    protected abstract boolean supportsBreakpoint(B breakpoint);

    /**
     * Called when the given breakpoint has been added to or removed from the registered breakpoints,
     * before the breakpoints are sent to the debug adapter.
     *
     * @param breakpoint the added or removed breakpoint.
     */
    protected void onBreakpointsChanged(@NotNull B breakpoint) {
    }

    protected abstract @NotNull CompletableFuture<@Nullable Void> doSendBreakpoints(@Nullable IDebugProtocolServer debugProtocolServer,
                                                                           @Nullable TemporaryBreakpoint temporaryBreakpoint);

    protected record TemporaryBreakpoint(@NotNull XSourcePosition sourcePosition, boolean add) {
    }
}
