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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
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
        return DebugAdapterManager.getInstance().isDebuggableFile(file, project);
    }

    @Override
    public @Nullable XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint<DAPBreakpointProperties> breakpoint,
                                                                 @NotNull Project project) {
        // Returns a non-null XDebuggerEditorsProvider to support Condition in the breakpoint type.
        FileType fileType = null;
        var file = LSPIJUtils.findResourceFor(breakpoint.getFileUrl());
        if (file != null) {
            if (!canSupportConditionBreakpoint(file, project)) {
                return null;
            }
            fileType = file.getFileType();
        }
        return new DAPDebuggerEditorsProvider(fileType, null);
    }

    private boolean canSupportConditionBreakpoint(VirtualFile file, @NotNull Project project) {
        // TODO: manage a settings to know if the given file which is associated to a DAP server
        // can support condition breakpoint point
        // At this step, the DAP server cannot be started, so we need to manage a DAP server settings for that.
        return true;
    }
}
