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
package com.redhat.devtools.lsp4ij.dap.descriptors;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPSettingsEditor;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Debug Adapter Protocol (DAP) server descriptor factory.
 */
@ApiStatus.Experimental
public abstract class DebugAdapterDescriptorFactory {

    public static final DebugAdapterDescriptorFactory NONE = new UserDefinedDebugAdapterDescriptorFactory("none", "NONE","", Collections.emptyList(), Collections.emptyList());

    private ServerTrace serverTrace;

    @NotNull
    public abstract String getId();

    @NotNull
    public abstract String getName();

    public DebugAdapterDescriptor createDebugAdapterDescriptor(@NotNull DAPRunConfigurationOptions options,
                                                                        @NotNull ExecutionEnvironment environment) {
        return new DebugAdapterDescriptor(options, environment, this);
    }

    /**
     * Set the server trace.
     *
     * @param serverTrace the server trace.
     */
    public void setServerTrace(@NotNull ServerTrace serverTrace) {
        this.serverTrace = serverTrace;
    }

    /**
     * Returns the server trace.
     *
     * @return the server trace.
     */
    @NotNull
    public ServerTrace getServerTrace() {
        return serverTrace != null ? serverTrace : ServerTrace.off;
    }

    /**
     * Returns true if the given file can be debugged (to add/remove breakpoints) and false otherwise.
     *
     * @param file the file to debug.
     * @param project the project.
     * @return true if the given file can be debugged (to add/remove breakpoints) and false otherwise.
     */
    public abstract boolean canDebug(@NotNull VirtualFile file,
                                     @NotNull Project project);

    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor(@NotNull Project project) {
        return new DAPSettingsEditor(project);
    }

    public boolean canRun(@NotNull String executorId) {
        return true;
    }
}
