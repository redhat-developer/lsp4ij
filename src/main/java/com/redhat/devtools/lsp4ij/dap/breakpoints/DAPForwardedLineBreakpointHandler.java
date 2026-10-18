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
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Forwards a language's <b>native</b> line breakpoints (e.g. JavaScript, Python) to a running DAP
 * session.
 * <p>
 * It is registered for a single native {@link com.intellij.xdebugger.breakpoints.XLineBreakpointType}
 * and forwards every breakpoint whose file is mapped to this DAP server (see
 * {@link DebugAdapterDescriptor#isDebuggableFile}) to the shared {@link DAPBreakpointHandlerBase},
 * which sends it via {@code setBreakpoints}.
 * <p>
 * The breakpoint itself keeps its native type, so it continues to work normally with the language's
 * own debugger in other debug constellations. Because this handler is created per
 * {@link com.redhat.devtools.lsp4ij.dap.DAPDebugProcess} (i.e. per DAP debug session), the
 * forwarding only happens <em>while a DAP session runs</em> — it never affects native debug sessions.
 */
public class DAPForwardedLineBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<?>> {

    private final @NotNull DAPBreakpointHandlerBase<?> sharedHandler;
    private final @NotNull DebugAdapterDescriptor debugAdapterDescriptor;
    private final @NotNull Project project;

    public DAPForwardedLineBreakpointHandler(@NotNull Class<? extends XBreakpointType<XLineBreakpoint<?>, ?>> nativeTypeClass,
                                             @NotNull DAPBreakpointHandlerBase<?> sharedHandler,
                                             @NotNull DebugAdapterDescriptor debugAdapterDescriptor,
                                             @NotNull Project project) {
        super(nativeTypeClass);
        this.sharedHandler = sharedHandler;
        this.debugAdapterDescriptor = debugAdapterDescriptor;
        this.project = project;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerBreakpoint(@NotNull XLineBreakpoint<?> breakpoint) {
        if (shouldForward(breakpoint)) {
            ((BreakpointHandlerBase) sharedHandler).registerBreakpoint(breakpoint);
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void unregisterBreakpoint(@NotNull XLineBreakpoint<?> breakpoint, boolean temporary) {
        // No shouldForward() guard here: the shared handler removes by breakpoint identity and
        // applies the same supportsBreakpoint()/source-position guard itself, so delegating
        // unconditionally is a no-op for breakpoints it never tracked and avoids a stale entry
        // when an edited breakpoint is unregistered with a state that no longer passes shouldForward().
        ((BreakpointHandlerBase) sharedHandler).unregisterBreakpoint(breakpoint, temporary);
    }

    /**
     * A breakpoint is forwarded when it is NOT a DAP breakpoint (those are handled by the DAP handler
     * itself) and its file is mapped to this DAP server.
     */
    private boolean shouldForward(@NotNull XLineBreakpoint<?> breakpoint) {
        if (breakpoint.getType() instanceof DAPBreakpointTypeBase<?>) {
            return false;
        }
        return debugAdapterDescriptor.isDebuggableLineBreakpoint(breakpoint, project);
    }
}
