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
import com.intellij.xdebugger.XDebugSessionListener;
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

import javax.swing.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Base class for DAP/Disassembly breakpoint handler.
 *
 * @param <B> the breakpoint
 */
public abstract class BreakpointHandlerBase<B extends XBreakpoint<?>> extends XBreakpointHandler<B> implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BreakpointHandlerBase.class);

    protected static final CompletableFuture<?>[] EMPTY_COMPLETABLE_FUTURES = new CompletableFuture<?>[0];

    /**
     * The id which the last session that answered gave a breakpoint, or null when no session has an id for it.
     *
     * @deprecated each session of a session tree numbers its breakpoints itself, so a breakpoint has an id per session,
     * and two breakpoints can have the same id in two sessions: use
     * {@link #findBreakpointById(IDebugProtocolServer, Integer)}. The key is still set, as for a single session.
     */
    @Deprecated(since = "0.30.0", forRemoval = true)
    protected static final @NotNull Key<Integer> DAP_BREAKPOINT_ID = Key.create("dap.breakpoint.id");

    protected final @NotNull XDebugSession debugSession;
    protected final @NotNull DebugAdapterDescriptor debugAdapterDescriptor;
    protected final @NotNull Project project;
    protected final List<IDebugProtocolServer> debugProtocolServers = ContainerUtil.createConcurrentList();
    protected final List<B> breakpoints = ContainerUtil.createConcurrentList();
    // The current capabilities of each DAP server, which its 'capabilities' events can change while the session runs
    private final Map<IDebugProtocolServer, Supplier<Capabilities>> capabilities = Collections.synchronizedMap(new IdentityHashMap<>());
    // The sessions which report each breakpoint as bound: in a session tree only one session can bind it
    private final Map<B, Set<Object>> verifiedBy = new ConcurrentHashMap<>();
    // The id which each session gave each breakpoint: each session of a tree numbers its breakpoints itself, as
    // vscode-js-debug does, so a breakpoint has an id per session, and two breakpoints can have the same id in two
    // sessions
    private final Map<B, Map<Object, Integer>> breakpointIds = new ConcurrentHashMap<>();
    // The sessions which have terminated: a late answer of one of them must not count
    private final Set<Object> endedSessions = ConcurrentHashMap.newKeySet();
    private static final Object UNKNOWN_SESSION = new Object();
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
                clearIconsOfNonLineBreakpoints();
            }
        });
    }

    /**
     * Clears the icons of the breakpoints which are not line breakpoints: they keep the icons of this session until
     * they are computed again, and IntelliJ does that for line breakpoints only (see
     * {@link #updateBreakpointPresentation}).
     */
    private void clearIconsOfNonLineBreakpoints() {
        breakpoints.stream()
                .filter(breakpoint -> !(breakpoint instanceof XLineBreakpoint<?>))
                .forEach(this::clearIcon);
    }

    /**
     * Sends the breakpoints to the given DAP server, and to it again on each change of them, with the given
     * capabilities.
     * <p>
     * {@link #initialize(IDebugProtocolServer, Supplier)} calls it with the current capabilities, and then lets the
     * handler read the current capabilities each time it sends the breakpoints, so that a subclass which overrides
     * this method keeps working.
     *
     * @param debugProtocolServer the DAP server.
     * @param capabilities        the capabilities of the DAP server.
     * @return the future of the sent breakpoints.
     */
    public CompletableFuture<@Nullable Void> initialize(@NotNull IDebugProtocolServer debugProtocolServer,
                                                        @NotNull Capabilities capabilities) {
        this.debugProtocolServers.add(debugProtocolServer);
        this.capabilities.put(debugProtocolServer, () -> capabilities);
        return sendBreakpoints(debugProtocolServer, null);
    }

    /**
     * Sends the breakpoints to the given DAP server, and to it again on each change of them, with its current
     * capabilities, which its 'capabilities' events can change while the session runs.
     *
     * @param debugProtocolServer the DAP server.
     * @param capabilities        the current capabilities of the DAP server, read each time the breakpoints are sent.
     * @return the future of the sent breakpoints.
     */
    public CompletableFuture<@Nullable Void> initialize(@NotNull IDebugProtocolServer debugProtocolServer,
                                                        @NotNull Supplier<Capabilities> capabilities) {
        var sent = initialize(debugProtocolServer, capabilities.get());
        // After the method above, which keeps the capabilities it is given
        this.capabilities.put(debugProtocolServer, capabilities);
        return sent;
    }

    /**
     * Sends the breakpoints to the given initialized DAP server again, with its current capabilities, as when a DAP
     * 'capabilities' event changed what it supports. Nothing is sent to a DAP server which the handler was not
     * initialized for.
     *
     * @param debugProtocolServer the DAP server.
     * @return the future of the sent breakpoints.
     */
    public CompletableFuture<@Nullable Void> sendBreakpoints(@NotNull IDebugProtocolServer debugProtocolServer) {
        if (!capabilities.containsKey(debugProtocolServer)) {
            // Not initialized for this DAP server, or disposed: its initialization sends the breakpoints
            return CompletableFuture.completedFuture(null);
        }
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
        return serverCapabilities != null ? serverCapabilities.get() : new Capabilities();
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
        if (!removeBreakpoint(breakpoint)) {
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

    /**
     * Removes the given breakpoint from the registered ones, and forgets which DAP servers reported it bound and the
     * ids they gave it: a late 'breakpoint' event must not reach it once it is registered again.
     *
     * @param breakpoint the IJ breakpoint.
     * @return true if the breakpoint was registered, and false otherwise.
     */
    protected boolean removeBreakpoint(@NotNull B breakpoint) {
        if (!breakpoints.remove(breakpoint)) {
            return false;
        }
        verifiedBy.remove(breakpoint);
        breakpointIds.remove(breakpoint);
        updateDeprecatedBreakpointId(breakpoint, null);
        return true;
    }

    @Override
    public void dispose() {
        debugProtocolServers.clear();
        capabilities.clear();
        verifiedBy.clear();
        breakpointIds.keySet().forEach(breakpoint -> breakpoint.putUserData(DAP_BREAKPOINT_ID, null));
        breakpointIds.clear();
        endedSessions.clear();
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
        updateBreakpoint(null, breakpoint, ijBreakpoint);
    }

    /**
     * Update IntelliJ breakpoint with the given DAP breakpoint information, as answered by the given DAP server.
     * <p>
     * The breakpoint is verified while at least one server reports it bound, and invalid once none does: in a tree of
     * sessions, such as vscode-js-debug starts, the sessions which do not own the target answer 'verified=false'.
     *
     * @param debugProtocolServer the DAP server which answered, or null when it is not known.
     * @param breakpoint          the DAP breakpoint information.
     * @param ijBreakpoint        the IJ Breakpoint and null otherwise.
     */
    public void updateBreakpoint(@Nullable IDebugProtocolServer debugProtocolServer,
                                 @NotNull Breakpoint breakpoint,
                                 @Nullable B ijBreakpoint) {
        if (ijBreakpoint == null) {
            // Retrieve the IntelliJ breakpoint by the id which this DAP server gave it.
            ijBreakpoint = findBreakpointById(debugProtocolServer, breakpoint.getId());
        }
        if (ijBreakpoint == null || !breakpoints.contains(ijBreakpoint)) {
            // A late answer for an unregistered breakpoint must not keep its verdicts
            return;
        }
        Object session = session(debugProtocolServer);
        Set<Object> sessions = verifiedBy.computeIfAbsent(ijBreakpoint, key -> ConcurrentHashMap.newKeySet());
        // The answers of the sessions arrive on their own threads: the answer, the check and the presentation of
        // one session must not interleave with those of another
        synchronized (sessions) {
            if (endedSessions.contains(session)) {
                // The answer of a session which has terminated since: forgetSession() has dropped its answers
                return;
            }
            if (breakpoint.isVerified()) {
                sessions.add(session);
            } else {
                sessions.remove(session);
            }
            if (!sessions.isEmpty()) {
                setBreakpointVerified(ijBreakpoint);
            } else {
                setBreakpointInvalid(ijBreakpoint, breakpoint.getMessage());
            }
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
     * Takes a failed request of the given DAP server for the given breakpoints as its answer that it binds none of
     * them: a breakpoint which another server has bound stays verified, and the others are shown as invalid with the
     * given message.
     *
     * @param debugProtocolServer the DAP server whose request failed.
     * @param ijBreakpoints       the IJ breakpoints of the request.
     * @param message             the message of the failure, or null.
     */
    protected void updateBreakpointsOfFailedRequest(@NotNull IDebugProtocolServer debugProtocolServer,
                                                    @NotNull List<B> ijBreakpoints,
                                                    @Nullable String message) {
        for (B ijBreakpoint : ijBreakpoints) {
            var unbound = new Breakpoint();
            unbound.setVerified(false);
            unbound.setMessage(message);
            updateBreakpoint(debugProtocolServer, unbound, ijBreakpoint);
        }
    }

    /**
     * Forgets what the given DAP server answered: which breakpoints it reported bound, and the ids it gave them. Shows
     * as invalid each breakpoint which no other server has bound. Call it when its session terminates.
     *
     * @param debugProtocolServer the DAP server of the terminated session.
     */
    public void forgetSession(@NotNull IDebugProtocolServer debugProtocolServer) {
        // Before its answers are dropped, so that an answer which arrives meanwhile is either dropped or ignored
        endedSessions.add(debugProtocolServer);
        for (var entry : verifiedBy.entrySet()) {
            Set<Object> sessions = entry.getValue();
            synchronized (sessions) {
                if (sessions.remove(debugProtocolServer) && sessions.isEmpty()) {
                    setBreakpointInvalid(entry.getKey(), DAPBundle.message("dap.breakpoints.session.ended"));
                }
            }
        }
        breakpointIds.forEach((breakpoint, ids) -> {
            if (ids.remove(debugProtocolServer) != null) {
                updateDeprecatedBreakpointId(breakpoint, null);
            }
        });
    }

    /**
     * Keeps the id which the given DAP server gave the given breakpoint, or forgets it when the id is null.
     *
     * @param debugProtocolServer the DAP server which answered, or null when it is not known.
     * @param ijBreakpoint        the IJ breakpoint.
     * @param id                  the DAP breakpoint id, or null.
     */
    protected void setBreakpointId(@Nullable IDebugProtocolServer debugProtocolServer,
                                   @NotNull B ijBreakpoint,
                                   @Nullable Integer id) {
        Object session = session(debugProtocolServer);
        if (id == null) {
            var ids = breakpointIds.get(ijBreakpoint);
            if (ids != null) {
                ids.remove(session);
            }
            updateDeprecatedBreakpointId(ijBreakpoint, null);
            return;
        }
        breakpointIds.computeIfAbsent(ijBreakpoint, key -> new ConcurrentHashMap<>()).put(session, id);
        // A late answer for a breakpoint which was unregistered since, or of a session which has terminated since,
        // must not keep its id. Checked after the put, so that an answer which races with the removal is dropped too.
        if (!breakpoints.contains(ijBreakpoint) || endedSessions.contains(session)) {
            var ids = breakpointIds.get(ijBreakpoint);
            if (ids != null) {
                ids.remove(session, id);
            }
            updateDeprecatedBreakpointId(ijBreakpoint, null);
            return;
        }
        updateDeprecatedBreakpointId(ijBreakpoint, id);
    }

    /**
     * Forgets the ids which the DAP servers gave the given breakpoint, as when it is not sent anymore.
     *
     * @param ijBreakpoint the IJ breakpoint.
     */
    protected void forgetBreakpointIds(@NotNull B ijBreakpoint) {
        breakpointIds.remove(ijBreakpoint);
        updateDeprecatedBreakpointId(ijBreakpoint, null);
    }

    /**
     * Sets the deprecated {@link #DAP_BREAKPOINT_ID} of the given breakpoint: to the id which a session just gave it,
     * or, when a session's id is forgotten, to the id it holds if a session still has it, else to an id which another
     * session gave it, else to null.
     *
     * @param ijBreakpoint the IJ breakpoint.
     * @param given        the id which a session just gave it, or null when one was forgotten.
     */
    private void updateDeprecatedBreakpointId(@NotNull B ijBreakpoint, @Nullable Integer given) {
        Integer id = given;
        if (id == null) {
            var ids = breakpointIds.get(ijBreakpoint);
            Integer current = ijBreakpoint.getUserData(DAP_BREAKPOINT_ID);
            if (ids != null) {
                // The map takes no null, and another thread can empty it meanwhile
                id = current != null && ids.containsValue(current) ? current
                        : ids.values().stream().findFirst().orElse(null);
            }
        }
        ijBreakpoint.putUserData(DAP_BREAKPOINT_ID, id);
    }

    /**
     * Returns the id which the given DAP server gave the given breakpoint, and null otherwise.
     *
     * @param debugProtocolServer the DAP server.
     * @param ijBreakpoint        the IJ breakpoint.
     * @return the id which the given DAP server gave the given breakpoint, and null otherwise.
     */
    @Nullable
    Integer getBreakpointId(@NotNull IDebugProtocolServer debugProtocolServer, @NotNull XBreakpoint<?> ijBreakpoint) {
        var ids = breakpointIds.get(ijBreakpoint);
        return ids != null ? ids.get(debugProtocolServer) : null;
    }

    /**
     * Returns the IntelliJ breakpoint from the DAP breakpoint id which one of the DAP servers gave it, and null
     * otherwise. Prefer {@link #findBreakpointById(IDebugProtocolServer, Integer)}: two sessions can give two
     * breakpoints the same id.
     *
     * @param id the DAP breakpoint id.
     * @return the IntelliJ breakpoint from the DAP breakpoint id and null otherwise.
     */
    @Nullable
    public B findBreakpointById(@Nullable Integer id) {
        return findBreakpointById(null, id);
    }

    /**
     * Returns the IntelliJ breakpoint from the DAP breakpoint id which the given DAP server gave it, and null
     * otherwise.
     *
     * @param debugProtocolServer the DAP server which names the breakpoint, or null to match the id which any DAP
     *                            server gave it.
     * @param id                  the DAP breakpoint id.
     * @return the IntelliJ breakpoint from the DAP breakpoint id and null otherwise.
     */
    @Nullable
    public B findBreakpointById(@Nullable IDebugProtocolServer debugProtocolServer, @Nullable Integer id) {
        if (id == null) {
            return null;
        }
        for (var breakpoint : breakpoints) {
            var ids = breakpointIds.get(breakpoint);
            if (ids != null && (debugProtocolServer != null ? id.equals(ids.get(debugProtocolServer)) : ids.containsValue(id))) {
                return breakpoint;
            }
        }
        return null;
    }

    /**
     * Returns the key of the given DAP server in the answers of the sessions.
     */
    private static @NotNull Object session(@Nullable IDebugProtocolServer debugProtocolServer) {
        return debugProtocolServer != null ? debugProtocolServer : UNKNOWN_SESSION;
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
        if (!BreakpointInternalSupport.updatePresentation(debugSession, ijBreakpoint, icon, errorMessage)) {
            return;
        }
        clearIcon(ijBreakpoint);
        refreshTooltip(ijBreakpoint);
        BreakpointInternalSupport.fireBreakpointPresentationUpdated(project, ijBreakpoint, debugSession);
    }

    /**
     * Refreshes the tooltip of the given breakpoint after its presentation in the session has changed.
     * <p>
     * {@link #setBreakpointVerified}, {@link #setBreakpointInvalid} and {@link #updateBreakpointPresentation} call it,
     * so handlers which show a breakpoint with these methods don't call it themselves.
     * <p>
     * Since IntelliJ 2025.2 the editor shows the breakpoint state (with its tooltip) which the backend
     * emits when {@code XBreakpointBase.breakpointChangedFlow()} emits. Updating the presentation of a
     * breakpoint in the session only updates its icon, so the tooltip kept the previous error message until
     * the breakpoint was changed (ex: disabled and enabled again). {@code emitBreakpointChanged()} emits the
     * state again without notifying the breakpoint listeners, so the breakpoint is not registered again in
     * the session.
     * <p>
     * The method is called by reflection because it doesn't exist in IntelliJ 2024.2, the version LSP4IJ is
     * compiled against. Nothing needs to be refreshed there: the tooltip reads the error message of the
     * breakpoint each time it is shown.
     * <p>
     * Remove this workaround once the minimum IntelliJ version supported by LSP4IJ contains the fix for
     * <a href="https://youtrack.jetbrains.com/issue/IJPL-259978">IJPL-259978</a>, where IntelliJ emits the state
     * itself after a presentation update.
     * <p>
     * Tests override this method to check that the tooltip is refreshed.
     *
     * @param ijBreakpoint the IJ breakpoint.
     */
    protected void refreshTooltip(@NotNull XBreakpoint<?> ijBreakpoint) {
        BreakpointInternalSupport.emitBreakpointChanged(ijBreakpoint);
    }

    /**
     * Clears the icon of the given breakpoint, which keeps the icon of its session until it is computed again.
     * <p>
     * Tests override this method to check that the icon is cleared.
     *
     * @param ijBreakpoint the IJ breakpoint.
     */
    protected void clearIcon(@NotNull XBreakpoint<?> ijBreakpoint) {
        BreakpointInternalSupport.clearIcon(ijBreakpoint);
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
