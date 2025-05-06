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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ExceptionBreakpointsFilter;
import org.eclipse.lsp4j.debug.ExceptionFilterOptions;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public class DAPExceptionBreakpointHandler extends XBreakpointHandler<XBreakpoint<DAPExceptionBreakpointProperties>> {

    private final Set<ExceptionFilterOptions> exceptionFilters;
    private final @NotNull XDebugSession debugSession;

    public DAPExceptionBreakpointHandler(@NotNull XDebugSession debugSession,
                                         @NotNull DebugAdapterDescriptor serverDescriptor,
                                         @NotNull Project project) {
        super(DAPExceptionBreakpointType.class);
        this.debugSession = debugSession;
        exceptionFilters = ContainerUtil.newConcurrentSet();
    }

    @Override
    public void registerBreakpoint(@NotNull XBreakpoint<DAPExceptionBreakpointProperties> breakpoint) {
        if (breakpoint.getProperties().getExceptionFilterId() != null) {
            ExceptionFilterOptions options = new ExceptionFilterOptions();
            options.setFilterId(breakpoint.getProperties().getExceptionFilterId());
            exceptionFilters.add(options);
            updateRemoteBreakpoints();
        }
    }

    @Override
    public void unregisterBreakpoint(@NotNull XBreakpoint<DAPExceptionBreakpointProperties> breakpoint, boolean b1) {
        if (breakpoint.getProperties().getExceptionFilterId() != null) {
            ExceptionFilterOptions options = new ExceptionFilterOptions();
            options.setFilterId(breakpoint.getProperties().getExceptionFilterId());
            exceptionFilters.remove(options);
            updateRemoteBreakpoints();
        }
    }

    private void updateRemoteBreakpoints() {
        SetExceptionBreakpointsArguments args = new SetExceptionBreakpointsArguments();
        ExceptionFilterOptions[] options = exceptionFilters.toArray(new ExceptionFilterOptions[0]);
        String[] ids = Arrays.stream(options)
                .map(ExceptionFilterOptions::getFilterId)
                .toArray(String[]::new);
        args.setFilters(ids);
        args.setFilterOptions(options);
        /*serverConnection.sendRequest(remoteProxy -> {
            remoteProxy.setExceptionBreakpoints(args);
        });*/
    }

    public void createOrRemoveBreakpoints(Capabilities capabilities) {
        if (capabilities.getExceptionBreakpointFilters() == null) {
            return;
        }
        XBreakpointManager manager =
                XDebuggerManager.getInstance(debugSession.getProject()).getBreakpointManager();
        DAPExceptionBreakpointType breakpointType =
                XDebuggerUtil.getInstance().findBreakpointType(DAPExceptionBreakpointType.class);

        Collection<? extends XBreakpoint<DAPExceptionBreakpointProperties>> breakpoints =
                ApplicationManager.getApplication().runReadAction(
                        (Computable<? extends Collection<? extends XBreakpoint<DAPExceptionBreakpointProperties>>>)
                                () -> manager.getBreakpoints(breakpointType));
        // create necessary breakpoints
        for (ExceptionBreakpointsFilter dapFilter : capabilities.getExceptionBreakpointFilters()) {
            if (breakpoints.stream().anyMatch(
                    b ->
                            b.getProperties() != null &&
                                    dapFilter.getFilter().equals(b.getProperties().getExceptionFilterId()))) {
                continue;
            }
            DAPExceptionBreakpointProperties props = new DAPExceptionBreakpointProperties();
            props.setExceptionFilterId(dapFilter.getFilter());
            props.setExceptionFilterLabel(dapFilter.getLabel());
            props.setByFilter(dapFilter);
            ApplicationManager.getApplication().invokeAndWait(() -> {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    manager.addBreakpoint(
                            breakpointType,
                            props);
                });
            });
        }

        // Remove unnecessary breakpoints
        for (XBreakpoint<DAPExceptionBreakpointProperties> breakpoint : breakpoints) {
            if (breakpoint.getProperties() == null ||
                    breakpoint.getProperties().getExceptionFilterId() == null) {
                continue;
            }
            if (Arrays.stream(capabilities.getExceptionBreakpointFilters()).noneMatch(
                    f -> f.getFilter().equals(breakpoint.getProperties().getExceptionFilterId()))) {
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        manager.removeBreakpoint(breakpoint);
                    });
                });
            }
        }
    }

    public Optional<XBreakpoint<DAPExceptionBreakpointProperties>> findBreakpointByFilterId(@NotNull String filterId) {
        XBreakpointManager manager = XDebuggerManager.getInstance(debugSession.getProject()).getBreakpointManager();
        DAPExceptionBreakpointType breakpointType = XDebuggerUtil.getInstance().findBreakpointType(DAPExceptionBreakpointType.class);
        return ApplicationManager.getApplication().runReadAction(
                (Computable<Optional<XBreakpoint<DAPExceptionBreakpointProperties>>>) () -> {
                    Collection<? extends XBreakpoint<DAPExceptionBreakpointProperties>> breakpoints = manager.getBreakpoints(breakpointType);
                    return breakpoints.stream()
                            .filter(b -> b.getProperties() != null &&
                                    filterId.equals(b.getProperties().getExceptionFilterId()))
                            .map(b -> (XBreakpoint<DAPExceptionBreakpointProperties>) b)
                            .findFirst();
                });
    }
}
