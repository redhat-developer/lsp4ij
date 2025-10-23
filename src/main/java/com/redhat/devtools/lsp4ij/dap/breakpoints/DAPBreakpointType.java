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

import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Default Debug Adapter Protocol (DAP) breakpoint type.
 */
public class DAPBreakpointType extends DAPBreakpointTypeBase<DAPBreakpointProperties> {

    private static final String BREAKPOINT_ID = "dap-breakpoint";

    public DAPBreakpointType() {
        super(BREAKPOINT_ID, DAPBundle.message("dap.breakpoints.title"));
    }

    @Override
    @Nullable
    public DAPBreakpointProperties createBreakpointProperties(@NotNull final VirtualFile file, final int line) {
        return new DAPBreakpointProperties();
    }

}
