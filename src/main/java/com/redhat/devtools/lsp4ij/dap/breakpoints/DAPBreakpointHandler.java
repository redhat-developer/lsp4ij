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
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Debug Adapter Protocol (DAP) breakpoint handler which is working with {@link DAPBreakpointType}.
 */
public class DAPBreakpointHandler extends DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>>  {

    public DAPBreakpointHandler(@NotNull XDebugSession debugSession, @NotNull DebugAdapterDescriptor debugAdapterDescriptor, @NotNull Project project) {
        super(DAPBreakpointType.class, debugSession, debugAdapterDescriptor, project);
    }
}
