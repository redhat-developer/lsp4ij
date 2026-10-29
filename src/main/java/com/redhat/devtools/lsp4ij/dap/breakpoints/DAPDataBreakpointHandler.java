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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.DataBreakpoint;
import org.eclipse.lsp4j.debug.DataBreakpointAccessType;
import org.eclipse.lsp4j.debug.DataBreakpointInfoArguments;
import org.eclipse.lsp4j.debug.DataBreakpointInfoResponse;
import org.eclipse.lsp4j.debug.SetDataBreakpointsArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Debug Adapter Protocol (DAP) data breakpoint handler which is working with {@link DAPDataBreakpointType}.
 * <p>
 * A data breakpoint is added with {@link #addDataBreakpoint}, on the data which a DAP server of the session names for a
 * variable ('dataBreakpointInfo'), and is bound to that DAP server. The enabled data breakpoints of each DAP server
 * which supports data breakpoints ('supportsDataBreakpoints') are sent to it with 'setDataBreakpoints'. The request
 * replaces all the data breakpoints of the DAP server, so each change sends all of them, and the removal of the last
 * one sends none.
 * <p>
 * A data id which the DAP server says can't persist ('canPersist') is valid only in this session: its data breakpoint
 * is removed when the session ends, as IntelliJ removes a temporary breakpoint, so that it is neither saved with the
 * project nor sent to a later session.
 */
public class DAPDataBreakpointHandler extends BreakpointHandlerBase<XBreakpoint<DAPDataBreakpointProperties>> {

    // The number of the last 'setDataBreakpoints' request sent to each DAP server: the answer to an older one, which
    // can come after the answer to a newer one, is out of date
    private final Map<IDebugProtocolServer, Long> lastRequests = Collections.synchronizedMap(new IdentityHashMap<>());
    // The DAP servers of the session, which its data breakpoints are bound to. Unlike debugProtocolServers, they are
    // kept once the session is disposed, until the session ends its data breakpoints
    private final Set<IDebugProtocolServer> sessionServers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    public DAPDataBreakpointHandler(@NotNull XDebugSession debugSession,
                                    @NotNull DebugAdapterDescriptor debugAdapterDescriptor,
                                    @NotNull Project project) {
        super(DAPDataBreakpointType.class, debugSession, debugAdapterDescriptor, project);
        debugSession.addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionStopped() {
                endSession();
            }
        });
    }

    @Override
    public CompletableFuture<@Nullable Void> initialize(@NotNull IDebugProtocolServer debugProtocolServer,
                                                        @NotNull Capabilities capabilities) {
        sessionServers.add(debugProtocolServer);
        return super.initialize(debugProtocolServer, capabilities);
    }

    /**
     * Ends the data breakpoints of this session: those whose data id can't persist are removed from the project, and
     * the others are no longer bound to the DAP servers of this session.
     */
    void endSession() {
        DAPDataBreakpointType.endSession(project, properties -> isSessionServer(properties.getServer()));
        sessionServers.clear();
    }

    private boolean isSessionServer(@Nullable IDebugProtocolServer debugProtocolServer) {
        return debugProtocolServer != null && sessionServers.contains(debugProtocolServer);
    }

    @Override
    protected boolean supportsBreakpoint(XBreakpoint<DAPDataBreakpointProperties> breakpoint) {
        var properties = breakpoint.getProperties();
        if (properties.getServer() != null) {
            // The data of a DAP server of this session, or of another session, which sends it
            return isSessionServer(properties.getServer());
        }
        // A data breakpoint kept from an earlier session, whose data id can persist, is sent to the DAP servers of its
        // configuration
        return properties.isCanPersist() && properties.getServerId().equals(getServerId());
    }

    @Override
    public void registerBreakpoint(@NotNull XBreakpoint<DAPDataBreakpointProperties> breakpoint) {
        var properties = breakpoint.getProperties();
        if (properties.getServer() == null && !properties.isCanPersist()) {
            // Left over by a session which didn't end it, as when the IDE stopped: its data id is no longer valid
            ApplicationManager.getApplication().invokeLater(
                    () -> XDebuggerUtil.getInstance().removeBreakpoint(project, breakpoint),
                    ModalityState.any(),
                    project.getDisposed());
            return;
        }
        if (supportsBreakpoint(breakpoint)) {
            super.registerBreakpoint(breakpoint);
        }
    }

    @Override
    public void unregisterBreakpoint(@NotNull XBreakpoint<DAPDataBreakpointProperties> breakpoint,
                                     boolean temporary) {
        // A data breakpoint has no source position, which the base class needs to remove a line breakpoint
        if (breakpoints.remove(breakpoint)) {
            sendDataBreakpoints();
        }
    }

    // Takes turns with sendBreakpoints, which is synchronized too, so the DAP server gets the requests in the order
    // in which they read the breakpoints. Unlike sendBreakpoints, it sends a request when none are left, which
    // removes the last one from the DAP server.
    private synchronized CompletableFuture<@Nullable Void> sendDataBreakpoints() {
        return doSendBreakpoints(null, null);
    }

    @Override
    protected @NotNull CompletableFuture<@Nullable Void> doSendBreakpoints(@Nullable IDebugProtocolServer debugProtocolServer,
                                                                           @Nullable TemporaryBreakpoint temporaryBreakpoint) {
        List<XBreakpoint<DAPDataBreakpointProperties>> registered = List.copyOf(breakpoints);
        List<IDebugProtocolServer> servers = debugProtocolServer != null ? List.of(debugProtocolServer) : debugProtocolServers;
        final var setDataBreakpointsFutures = new ArrayList<CompletableFuture<Void>>();
        for (var server : servers) {
            if (isSupportsDataBreakpoints(getCapabilities(server))) {
                // The IJ breakpoint of each data breakpoint of the request, in the same order: those of the data of
                // this DAP server
                List<XBreakpoint<DAPDataBreakpointProperties>> ijBreakpoints = registered.stream()
                        .filter(breakpoint -> isSentTo(breakpoint, server))
                        .toList();
                long request = lastRequests.merge(server, 1L, Long::sum);
                setDataBreakpointsFutures.add(setDataBreakpoints(server, createSetDataBreakpointsArguments(ijBreakpoints), ijBreakpoints, request));
            }
        }
        return CompletableFuture.allOf(setDataBreakpointsFutures.toArray(EMPTY_COMPLETABLE_FUTURES));
    }

    private static boolean isSentTo(@NotNull XBreakpoint<DAPDataBreakpointProperties> breakpoint,
                                    @NotNull IDebugProtocolServer debugProtocolServer) {
        // One bound to a DAP server watches the data of that DAP server only
        var server = breakpoint.getProperties().getServer();
        return server == null || server == debugProtocolServer;
    }

    /**
     * Sends the 'setDataBreakpoints' request, and gives each IJ breakpoint the id and the status of its DAP
     * breakpoint. When the request fails, each IJ breakpoint is shown invalid with the error, and the session goes on.
     * The answer is ignored once a newer request has been sent to the DAP server.
     *
     * @param debugProtocolServer the DAP server.
     * @param arguments           the 'setDataBreakpoints' arguments.
     * @param ijBreakpoints       the IJ breakpoint of each data breakpoint of the arguments, in the same order.
     * @param request             the number of the request.
     * @return the future.
     */
    private CompletableFuture<Void> setDataBreakpoints(@NotNull IDebugProtocolServer debugProtocolServer,
                                                       @NotNull SetDataBreakpointsArguments arguments,
                                                       @NotNull List<XBreakpoint<DAPDataBreakpointProperties>> ijBreakpoints,
                                                       long request) {
        return debugProtocolServer.setDataBreakpoints(arguments)
                .handle((response, error) -> {
                    if (!Objects.equals(lastRequests.get(debugProtocolServer), request)) {
                        // A newer request replaced these breakpoints: its answer gives their ids and status
                        return null;
                    }
                    if (error != null) {
                        ijBreakpoints.forEach(ijBreakpoint -> setBreakpointInvalid(ijBreakpoint, getCause(error).getMessage()));
                        return null;
                    }
                    var breakpoints = response != null ? response.getBreakpoints() : null;
                    if (breakpoints == null) {
                        return null;
                    }
                    // The response has a breakpoint per data breakpoint of the request, in the same order
                    for (int i = 0; i < Math.min(breakpoints.length, ijBreakpoints.size()); i++) {
                        var ijBreakpoint = ijBreakpoints.get(i);
                        ijBreakpoint.putUserData(DAP_BREAKPOINT_ID, breakpoints[i].getId());
                        updateBreakpoint(breakpoints[i], ijBreakpoint);
                    }
                    return null;
                });
    }

    /**
     * Returns the 'setDataBreakpoints' arguments for the given IJ breakpoints, in the same order: each has the id of
     * its data, its access type if it has one, its condition if it is enabled, and its hit condition if it has one.
     *
     * @param ijBreakpoints the IJ data breakpoints.
     * @return the 'setDataBreakpoints' arguments.
     */
    static @NotNull SetDataBreakpointsArguments createSetDataBreakpointsArguments(@NotNull List<? extends XBreakpoint<DAPDataBreakpointProperties>> ijBreakpoints) {
        var arguments = new SetDataBreakpointsArguments();
        arguments.setBreakpoints(ijBreakpoints.stream()
                .map(DAPDataBreakpointHandler::toDataBreakpoint)
                .toArray(DataBreakpoint[]::new));
        return arguments;
    }

    private static @NotNull DataBreakpoint toDataBreakpoint(@NotNull XBreakpoint<DAPDataBreakpointProperties> ijBreakpoint) {
        var properties = ijBreakpoint.getProperties();
        var dataBreakpoint = new DataBreakpoint();
        dataBreakpoint.setDataId(properties.getDataId());
        dataBreakpoint.setAccessType(properties.getAccessType());
        XExpression condition = ijBreakpoint.getConditionExpression();
        if (condition != null && StringUtils.isNotBlank(condition.getExpression())) {
            dataBreakpoint.setCondition(condition.getExpression());
        }
        if (StringUtils.isNotBlank(properties.getHitCondition())) {
            dataBreakpoint.setHitCondition(properties.getHitCondition());
        }
        return dataBreakpoint;
    }

    /**
     * Returns true if the DAP server supports data breakpoints and false otherwise.
     *
     * @param capabilities the DAP server capabilities.
     * @return true if the DAP server supports data breakpoints and false otherwise.
     */
    public static boolean isSupportsDataBreakpoints(@NotNull Capabilities capabilities) {
        return Boolean.TRUE.equals(capabilities.getSupportsDataBreakpoints());
    }

    /**
     * Returns the 'dataBreakpointInfo' arguments which name a variable: its name in its container, or, for a value with
     * no container such as a watch, its expression, which is evaluated in the given frame.
     *
     * @param variablesReference the variables reference of the container of the variable, and null if it has none.
     * @param name               the name of the variable, or the expression of a value with no container.
     * @param frameId            the stack frame of the variable.
     * @return the 'dataBreakpointInfo' arguments.
     */
    public static @NotNull DataBreakpointInfoArguments createDataBreakpointInfoArguments(@Nullable Integer variablesReference,
                                                                                        @NotNull String name,
                                                                                        int frameId) {
        var arguments = new DataBreakpointInfoArguments();
        arguments.setVariablesReference(variablesReference);
        arguments.setName(name);
        // the frame id is always sent: the DAP server ignores it when the variable has a container
        arguments.setFrameId(frameId);
        return arguments;
    }

    /**
     * Asks the given DAP server which data a data breakpoint on a variable would watch ('dataBreakpointInfo'), and adds
     * a data breakpoint on it to the project, bound to that DAP server, unless one is already there. When the DAP server
     * can't watch the variable, nothing is added, and the reason which it gives is reported.
     *
     * @param debugProtocolServer the DAP server of the variable.
     * @param arguments           the 'dataBreakpointInfo' arguments, which name the variable.
     * @return the future of the data breakpoint, and of null if none was added.
     */
    public @NotNull CompletableFuture<@Nullable XBreakpoint<DAPDataBreakpointProperties>> addDataBreakpoint(@NotNull IDebugProtocolServer debugProtocolServer,
                                                                                                         @NotNull DataBreakpointInfoArguments arguments) {
        String name = Objects.toString(arguments.getName(), "");
        return debugProtocolServer.dataBreakpointInfo(arguments)
                .handle((info, error) -> {
                    if (error != null) {
                        reportRefusal(DAPBundle.message("dap.breakpoints.data.failed", name, getCause(error).getMessage()));
                        return null;
                    }
                    if (info == null || info.getDataId() == null) {
                        // The DAP server says why the variable can't be watched
                        String reason = info != null ? info.getDescription() : null;
                        reportRefusal(StringUtils.isNotBlank(reason) ? reason : DAPBundle.message("dap.breakpoints.data.refused", name));
                        return null;
                    }
                    return createProperties(info, name, debugProtocolServer);
                })
                .thenCompose(properties -> properties != null ? addBreakpoint(properties) : CompletableFuture.completedFuture(null));
    }

    private @NotNull DAPDataBreakpointProperties createProperties(@NotNull DataBreakpointInfoResponse info,
                                                                  @NotNull String name,
                                                                  @NotNull IDebugProtocolServer debugProtocolServer) {
        String description = StringUtils.isNotBlank(info.getDescription()) ? info.getDescription() : name;
        var properties = new DAPDataBreakpointProperties(info.getDataId(),
                description,
                getAccessType(info.getAccessTypes()),
                Boolean.TRUE.equals(info.getCanPersist()),
                getServerId());
        properties.setServer(debugProtocolServer);
        return properties;
    }

    /**
     * Returns the access type of a data breakpoint, among those which the DAP server offers: a write, which changes
     * the value, if it offers it, and the first one otherwise; null, for the DAP server's own, if it offers none.
     *
     * @param accessTypes the access types which the DAP server offers, or null.
     * @return the access type of a data breakpoint, and null for the DAP server's own.
     */
    static @Nullable DataBreakpointAccessType getAccessType(DataBreakpointAccessType @Nullable [] accessTypes) {
        if (accessTypes == null) {
            return null;
        }
        if (Arrays.asList(accessTypes).contains(DataBreakpointAccessType.WRITE)) {
            return DataBreakpointAccessType.WRITE;
        }
        return Arrays.stream(accessTypes).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /**
     * Adds a data breakpoint with the given properties to the project, unless one on the same data of the same DAP
     * server is there, which is enabled instead. Once the session has ended, its data ids are no longer valid, and
     * nothing is added.
     */
    private @NotNull CompletableFuture<@Nullable XBreakpoint<DAPDataBreakpointProperties>> addBreakpoint(@NotNull DAPDataBreakpointProperties properties) {
        var added = new CompletableFuture<@Nullable XBreakpoint<DAPDataBreakpointProperties>>();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || !isSessionServer(properties.getServer())) {
                added.complete(null);
                return;
            }
            try {
                added.complete(WriteAction.compute(() -> {
                    var breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
                    for (var breakpoint : breakpointManager.getBreakpoints(DAPDataBreakpointType.class)) {
                        var existing = breakpoint.getProperties();
                        if (existing.getServer() == properties.getServer() && existing.getDataId().equals(properties.getDataId())) {
                            breakpoint.setEnabled(true);
                            return breakpoint;
                        }
                    }
                    var type = XDebuggerUtil.getInstance().findBreakpointType(DAPDataBreakpointType.class);
                    return breakpointManager.addBreakpoint(type, properties);
                }));
            } catch (RuntimeException e) {
                added.completeExceptionally(e);
            }
        }, ModalityState.defaultModalityState());
        return added;
    }

    /**
     * Reports why no data breakpoint was added, in the debug tool window.
     *
     * @param reason the reason, as the DAP server gives it.
     */
    protected void reportRefusal(@NotNull String reason) {
        debugSession.reportMessage(reason, MessageType.WARNING);
    }

    private @NotNull String getServerId() {
        return Objects.toString(debugAdapterDescriptor.getId(), "");
    }

    private static @NotNull Throwable getCause(@NotNull Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /**
     * Shows the given data breakpoint as verified in the session.
     *
     * @param ijBreakpoint the IJ data breakpoint.
     */
    @Override
    protected void setBreakpointVerified(@NotNull XBreakpoint<DAPDataBreakpointProperties> ijBreakpoint) {
        Icon icon = ijBreakpoint.getSuspendPolicy() == SuspendPolicy.NONE
                ? AllIcons.Debugger.Db_verified_no_suspend_field_breakpoint
                : AllIcons.Debugger.Db_verified_field_breakpoint;
        updateBreakpointPresentation(ijBreakpoint, icon, null);
    }

    /**
     * Shows the given data breakpoint as invalid in the session, with the given message.
     *
     * @param ijBreakpoint the IJ data breakpoint.
     * @param message      the message of the DAP server, or null.
     */
    @Override
    protected void setBreakpointInvalid(@NotNull XBreakpoint<DAPDataBreakpointProperties> ijBreakpoint,
                                        @Nullable String message) {
        updateBreakpointPresentation(ijBreakpoint, AllIcons.Debugger.Db_invalid_breakpoint, message);
    }
}
