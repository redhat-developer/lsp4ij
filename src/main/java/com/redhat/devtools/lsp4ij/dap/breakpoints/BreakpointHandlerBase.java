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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.lang.reflect.Method;
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
    private static final @Nullable Method EMIT_BREAKPOINT_CHANGED = findEmitBreakpointChanged();

    protected final @NotNull XDebugSession debugSession;
    protected final @NotNull DebugAdapterDescriptor debugAdapterDescriptor;
    protected final @NotNull Project project;
    protected final List<IDebugProtocolServer> debugProtocolServers = ContainerUtil.createConcurrentList();
    protected final List<B> breakpoints = ContainerUtil.createConcurrentList();
    private final Map<IDebugProtocolServer, Capabilities> capabilities = Collections.synchronizedMap(new IdentityHashMap<>());
    // The temporary breakpoint, as of "Run to Cursor", from when it is registered until it is unregistered: each
    // 'setBreakpoints' request for its source keeps it, as when the user adds a breakpoint to the source meanwhile
    private @Nullable XSourcePosition registeredTemporaryBreakpoint;

    public BreakpointHandlerBase(@NotNull Class<? extends XBreakpointType<B, ?>> breakpointTypeClass,
                                 @NotNull XDebugSession debugSession,
                                 @NotNull DebugAdapterDescriptor debugAdapterDescriptor,
                                 @NotNull Project project) {
        super(breakpointTypeClass);
        this.debugSession = debugSession;
        this.debugAdapterDescriptor = debugAdapterDescriptor;
        this.project = project;
        debugSession.addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionStopped() {
                // The breakpoints keep the icons of this session until they are computed again: IntelliJ does that for
                // line breakpoints only (see updateBreakpointPresentation)
                breakpoints.stream()
                        .filter(breakpoint -> !(breakpoint instanceof XLineBreakpoint<?>))
                        .forEach(BreakpointHandlerBase::clearIcon);
            }
        });
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
            if (breakpoint instanceof XLineBreakpoint<?>) {
                String message = sourcePosition == null
                    ? DAPBundle.message("dap.breakpoints.location.not.available")
                    : DAPBundle.message("dap.breakpoints.file.not.debuggable");
                setBreakpointInvalid(breakpoint, message);
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

    // Sends take turns, so the debug adapter gets them in the order in which they read the breakpoints: a request
    // which still has the temporary breakpoint cannot follow the one which removes it
    protected synchronized CompletableFuture<@Nullable Void> sendBreakpoints(@Nullable IDebugProtocolServer debugProtocolServer,
                                                                             @Nullable TemporaryBreakpoint temporaryBreakpoint) {
        if (breakpoints.isEmpty() && temporaryBreakpoint == null && registeredTemporaryBreakpoint == null && !hasSentBreakpoints()) {
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
        if (ijBreakpoint == null) {
            return;
        }
        // Update the IntelliJ breakpoint according the DAP breakpoint 'verified' flag.
        if (breakpoint.isVerified()) {
            setBreakpointVerified(ijBreakpoint);
        } else {
            setBreakpointInvalid(ijBreakpoint, breakpoint.getMessage());
        }
    }

    /**
     * Shows the given breakpoint as verified in the session.
     * <p>
     * The session shows a line breakpoint. Handlers of other breakpoints override this method and show the
     * breakpoint with {@link #updateBreakpointPresentation}.
     *
     * @param ijBreakpoint the IJ breakpoint.
     */
    protected void setBreakpointVerified(@NotNull B ijBreakpoint) {
        if (ijBreakpoint instanceof XLineBreakpoint<?> lineBreakpoint) {
            debugSession.setBreakpointVerified(lineBreakpoint);
            refreshTooltip(ijBreakpoint);
        }
    }

    /**
     * Shows the given breakpoint as invalid in the session, with the given message.
     * <p>
     * The session shows a line breakpoint. Handlers of other breakpoints override this method and show the
     * breakpoint with {@link #updateBreakpointPresentation}.
     *
     * @param ijBreakpoint the IJ breakpoint.
     * @param message      the message, or null.
     */
    protected void setBreakpointInvalid(@NotNull B ijBreakpoint, @Nullable String message) {
        if (ijBreakpoint instanceof XLineBreakpoint<?> lineBreakpoint) {
            debugSession.setBreakpointInvalid(lineBreakpoint, message);
            refreshTooltip(ijBreakpoint);
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
     * Register the given source position as temporary breakpoint. It stays in the 'setBreakpoints' requests for its
     * source until it is unregistered. One is kept at a time: unregister the one registered before first.
     *
     * @param sourcePosition the temporary breakpoint.
     * @return the completable future.
     */
    public synchronized CompletableFuture<@Nullable Void> registerTemporaryBreakpoint(@NotNull XSourcePosition sourcePosition) {
        registeredTemporaryBreakpoint = sourcePosition;
        return sendBreakpoints(null, null);
    }

    /**
     * Un-register the given source position as temporary breakpoint.
     *
     * @param sourcePosition the temporary breakpoint, as given to {@link #registerTemporaryBreakpoint(XSourcePosition)}.
     * @return the completable future.
     */
    public synchronized CompletableFuture<@Nullable Void> unregisterTemporaryBreakpoint(@NotNull XSourcePosition sourcePosition) {
        if (registeredTemporaryBreakpoint == sourcePosition) {
            registeredTemporaryBreakpoint = null;
        }
        return sendBreakpoints(null, new TemporaryBreakpoint(sourcePosition, false));
    }

    /**
     * Returns the registered temporary breakpoint, and null if there is none.
     *
     * @return the registered temporary breakpoint, and null if there is none.
     */
    protected synchronized @Nullable XSourcePosition getRegisteredTemporaryBreakpoint() {
        return registeredTemporaryBreakpoint;
    }

    /**
     * Shows the given breakpoint with the given icon and error message in the session.
     * <p>
     * A line breakpoint is shown with {@link XDebugSession#updateBreakpointPresentation}. IntelliJ 2024.2 takes only
     * line breakpoints there, so for other breakpoints the presentation which the session keeps for each of its
     * breakpoints is set here.
     *
     * @param ijBreakpoint the IJ breakpoint.
     * @param icon         the icon, or null.
     * @param errorMessage the error message, or null.
     */
    protected void updateBreakpointPresentation(@NotNull XBreakpoint<?> ijBreakpoint,
                                                @Nullable Icon icon,
                                                @Nullable String errorMessage) {
        if (ijBreakpoint instanceof XLineBreakpoint<?> lineBreakpoint) {
            debugSession.updateBreakpointPresentation(lineBreakpoint, icon, errorMessage);
            refreshTooltip(ijBreakpoint);
            return;
        }
        if (!(debugSession instanceof XDebugSessionImpl session)) {
            return;
        }
        var presentation = session.getBreakpointPresentation(ijBreakpoint);
        if (presentation == null) {
            // The breakpoint is no longer registered in the session
            return;
        }
        if (Comparing.equal(presentation.getIcon(), icon) && Comparing.strEqual(presentation.getErrorMessage(), errorMessage)) {
            return;
        }
        presentation.setIcon(icon);
        presentation.setErrorMessage(errorMessage);
        // The breakpoint keeps its icon until it is computed again with the presentation
        clearIcon(ijBreakpoint);
        refreshTooltip(ijBreakpoint);
        if (XDebuggerManager.getInstance(project).getBreakpointManager() instanceof XBreakpointManagerImpl breakpointManager) {
            ApplicationManager.getApplication().invokeLater(
                    () -> breakpointManager.fireBreakpointPresentationUpdated(ijBreakpoint, session),
                    ModalityState.any(),
                    project.getDisposed());
        }
    }

    /**
     * Refreshes the tooltip of the given breakpoint after its presentation in the session has changed.
     * <p>
     * {@link #setBreakpointVerified}, {@link #setBreakpointInvalid} and {@link #updateBreakpointPresentation} call it,
     * so handlers which show a breakpoint with these methods don't call it themselves.
     * <p>
     * Since IntelliJ 2025.2 the editor shows the breakpoint state (with its tooltip) which the backend
     * emits when {@link XBreakpointBase#breakpointChangedFlow()} emits. Updating the presentation of a
     * breakpoint in the session only updates its icon, so the tooltip kept the previous error message until
     * the breakpoint was changed (ex: disabled and enabled again). {@code emitBreakpointChanged()} emits the
     * state again without notifying the breakpoint listeners, so the breakpoint is not registered again in
     * the session.
     * <p>
     * The method is called by reflection because it doesn't exist in IntelliJ 2024.2, the version LSP4IJ is
     * compiled against. Nothing needs to be refreshed there: the tooltip reads the error message of the
     * breakpoint each time it is shown.
     * <p>
     * Tests override this method to check that the tooltip is refreshed.
     *
     * @param ijBreakpoint the IJ breakpoint.
     */
    protected void refreshTooltip(@NotNull XBreakpoint<?> ijBreakpoint) {
        if (EMIT_BREAKPOINT_CHANGED != null && ijBreakpoint instanceof XBreakpointBase<?, ?, ?> breakpointBase) {
            try {
                EMIT_BREAKPOINT_CHANGED.invoke(breakpointBase);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Cannot refresh the tooltip of the breakpoint", e);
            }
        }
    }

    private static @Nullable Method findEmitBreakpointChanged() {
        try {
            return XBreakpointBase.class.getMethod("emitBreakpointChanged");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Clears the icon of the given breakpoint, which keeps the icon of its session until it is computed again.
     *
     * @param ijBreakpoint the IJ breakpoint.
     */
    protected static void clearIcon(@NotNull XBreakpoint<?> ijBreakpoint) {
        if (ijBreakpoint instanceof XBreakpointBase<?, ?, ?> breakpointBase) {
            breakpointBase.clearIcon();
        }
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
