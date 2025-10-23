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
package com.redhat.devtools.lsp4ij.dap.disassembly.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Default handler for disassembly breakpoints.
 * <p>
 * This class specializes {@link DisassemblyBreakpointHandlerBase} for line breakpoints
 * with {@link DisassemblyBreakpointProperties}. It is used to manage instruction breakpoints
 * in {@link com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile} instances.
 */
public class DisassemblyBreakpointHandler extends DisassemblyBreakpointHandlerBase<XLineBreakpoint<DisassemblyBreakpointProperties>> {

    /**
     * Constructs a new disassembly breakpoint handler.
     *
     * @param debugSession           the XDebugSession this handler is associated with
     * @param debugAdapterDescriptor the descriptor of the debug adapter
     * @param project                the current IntelliJ project
     */
    public DisassemblyBreakpointHandler(@NotNull XDebugSession debugSession,
                                        @NotNull DebugAdapterDescriptor debugAdapterDescriptor,
                                        @NotNull Project project) {
        super(DisassemblyBreakpointType.class, debugSession, debugAdapterDescriptor, project);
    }
}
