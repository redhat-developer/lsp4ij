/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link ProcessStreamConnectionProvider} implementation to start a language server with a
 * process command defined by the user.
 */
public class UserDefinedStreamConnectionProvider extends OSProcessStreamConnectionProvider {

    private final @NotNull UserDefinedLanguageServerDefinition serverDefinition;

    public UserDefinedStreamConnectionProvider(@NotNull String commandLine,
                                               @NotNull Map<String, String> userEnvironmentVariables,
                                               boolean includeSystemEnvironmentVariables,
                                               @NotNull UserDefinedLanguageServerDefinition serverDefinition,
                                               @NotNull Project project) {
        super(createCommandLine(commandLine, userEnvironmentVariables, includeSystemEnvironmentVariables));
        this.serverDefinition = serverDefinition;
    }

    @NotNull
    private static GeneralCommandLine createCommandLine(@NotNull String commandLine,
                                                        @NotNull Map<String, String> userEnvironmentVariables,
                                                        boolean includeSystemEnvironmentVariables) {
        Map<String, String> environmentVariables = new HashMap<>(userEnvironmentVariables);
        // Add System environment variables
        if (includeSystemEnvironmentVariables) {
            environmentVariables.putAll(EnvironmentUtil.getEnvironmentMap());
        }
        return new GeneralCommandLine(CommandUtils.createCommands(commandLine))
                .withEnvironment(environmentVariables);
    }

    @Override
    public Object getInitializationOptions(VirtualFile rootUri) {
        return serverDefinition.getLanguageServerInitializationOptions();
    }
}
