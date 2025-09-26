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

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.DebugServerWaitStrategy;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.installation.ServerInstaller;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

import static com.redhat.devtools.lsp4ij.dap.LaunchConfiguration.findAttachConfiguration;
import static com.redhat.devtools.lsp4ij.dap.LaunchConfiguration.findLaunchConfiguration;

/**
 * User defined {@link DebugAdapterDescriptorFactory}.
 */
public class UserDefinedDebugAdapterDescriptorFactory extends DebugAdapterDescriptorFactory {

    @Override
    public DebugAdapterDescriptor createDebugAdapterDescriptor(@NotNull RunConfigurationOptions options, @NotNull ExecutionEnvironment environment) {
        return new UserDefinedDebugAdapterDescriptor(options, environment, getServerDefinition());
    }

    @Override
    public boolean prepareConfiguration(@NotNull RunConfiguration configuration,
                                        @NotNull VirtualFile file,
                                        @NotNull Project project) {
        if (super.prepareConfiguration(configuration, file, project)) {
            if (configuration instanceof DAPRunConfiguration dapConfiguration) {
                // Configuration
                var launchConfiguration = findLaunchConfiguration(getLaunchConfigurations());
                dapConfiguration.setLaunchConfiguration(launchConfiguration != null ? launchConfiguration.getContent() : "");
                var attachConfiguration = findAttachConfiguration(getLaunchConfigurations());
                dapConfiguration.setAttachConfiguration(attachConfiguration != null ? attachConfiguration.getContent() : "");

                var serverDefinition = getServerDefinition();
                // Mappings
                dapConfiguration.setServerMappings(Stream.concat(serverDefinition.getLanguageMappings().stream(),
                                serverDefinition.getFileTypeMappings().stream())
                        .toList());

                // Server
                dapConfiguration.setCommand(serverDefinition.getCommandLine());
                DebugServerWaitStrategy debugServerWaitStrategy = DebugServerWaitStrategy.TIMEOUT;
                int connectTimeout = serverDefinition.getConnectTimeout();
                if (connectTimeout > 0) {
                    dapConfiguration.setConnectTimeout(connectTimeout);
                } else {
                    String trackTrace = serverDefinition.getDebugServerReadyPattern();
                    if (StringUtils.isNotBlank(trackTrace)) {
                        debugServerWaitStrategy = DebugServerWaitStrategy.TRACE;
                        dapConfiguration.setDebugServerReadyPattern(trackTrace);
                    }
                }
                dapConfiguration.setDebugServerWaitStrategy(debugServerWaitStrategy);
                dapConfiguration.setAttachAddress(serverDefinition.getAttachAddress());
                dapConfiguration.setAttachPort(serverDefinition.getAttachPort());
            }
            return true;
        }
        return false;
    }

    @Override
    public @NotNull UserDefinedDebugAdapterServerDefinition getServerDefinition() {
        return (UserDefinedDebugAdapterServerDefinition) super.getServerDefinition();
    }

    @Override
    public @Nullable ServerInstaller createServerInstaller() {
        return new UserDefinedDebugAdapterServerInstaller(getServerDefinition());
    }
}
