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
package com.redhat.devtools.lsp4ij.dap.definitions.userdefined;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DefaultDebugAdapterDescriptor;
import org.jetbrains.annotations.NotNull;

public class UserDefinedDebugAdapterDescriptor extends DefaultDebugAdapterDescriptor {

    public UserDefinedDebugAdapterDescriptor(@NotNull RunConfigurationOptions options, @NotNull ExecutionEnvironment environment, @NotNull DebugAdapterServerDefinition serverDefinition) {
        super(options, environment, serverDefinition);
    }

    @Override
    public boolean isShowInstallerTab() {
        return true;
    }
}
