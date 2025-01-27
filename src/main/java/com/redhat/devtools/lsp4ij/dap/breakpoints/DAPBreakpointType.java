/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  Debug Adapter Protocol (DAP) breakpoint type.
 */
public class DAPBreakpointType extends XLineBreakpointType<DAPBreakpointProperties> {

    private static final String BREAKPOINT_ID = "dap-breakpoint";

    public DAPBreakpointType() {
        super(BREAKPOINT_ID, "DAP Breakpoint");
    }

    @Override
    @Nullable
    public DAPBreakpointProperties createBreakpointProperties(@NotNull final VirtualFile file, final int line) {
        return new DAPBreakpointProperties();
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file,
                            int line,
                            @NotNull Project project) {
        return DebugAdapterManager.getInstance().canDebug(file, project);
    }

}
