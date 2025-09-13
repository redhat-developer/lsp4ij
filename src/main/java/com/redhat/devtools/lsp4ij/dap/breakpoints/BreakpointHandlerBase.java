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
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for DAP/Disassembly breakpoint handler.
 *
 * @param <B> the breakpoint
 */
public abstract class BreakpointHandlerBase<B extends XBreakpoint<?>> extends XBreakpointHandler<B> implements Disposable {

    protected static final CompletableFuture<?>[] EMPTY_COMPLETABLE_FUTURES = new CompletableFuture[0];
    protected static final @NotNull Key<Integer> DAP_BREAKPOINT_ID = Key.create("dap.breakpoint.id");

    protected final @NotNull XDebugSession debugSession;
    protected final @NotNull DebugAdapterDescriptor debugAdapterDescriptor;
    protected final @NotNull Project project;
    protected final List<IDebugProtocolServer> debugProtocolServers = ContainerUtil.createConcurrentList();
    protected final List<B> breakpoints = ContainerUtil.createConcurrentList();

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
        return sendBreakpoints(debugProtocolServer, null);
    }

    @Override
    public void registerBreakpoint(@NotNull B breakpoint) {
        if (!(breakpoint.isEnabled() &&
                supportsBreakpoint(breakpoint))) {
            return;
        }

        breakpoints.add(breakpoint);
        sendBreakpoints(null, null);
    }

    @Override
    public void unregisterBreakpoint(@NotNull B breakpoint,
                                     boolean temporary) {
        var sourcePosition = breakpoint.getSourcePosition();
        if (!supportsBreakpoint(breakpoint) || sourcePosition == null) {
            return;
        }
        breakpoints.remove(breakpoint);
        sendBreakpoints(null,
                new TemporaryBreakpoint(sourcePosition, false));
    }

    @Override
    public void dispose() {
        debugProtocolServers.clear();
    }

    protected CompletableFuture<@Nullable Void> sendBreakpoints(@Nullable IDebugProtocolServer debugProtocolServer,
                                                                @Nullable TemporaryBreakpoint temporaryBreakpoint) {
        if (debugProtocolServers.isEmpty() || breakpoints.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return doSendBreakpoints(debugProtocolServer, temporaryBreakpoint);
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
    private B findBreakpointById(@Nullable Integer id) {
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

    protected abstract @NotNull CompletableFuture<@Nullable Void> doSendBreakpoints(@Nullable IDebugProtocolServer debugProtocolServer,
                                                                           @Nullable TemporaryBreakpoint temporaryBreakpoint);

    protected record TemporaryBreakpoint(@NotNull XSourcePosition sourcePosition, boolean add) {
    }
}
