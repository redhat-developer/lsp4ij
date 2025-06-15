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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.createCommandLine;

/**
 * {@link ProcessStreamConnectionProvider} implementation to start a language server with a
 * process command defined by the user.
 */
public class UserDefinedStreamConnectionProvider extends OSProcessStreamConnectionProvider {

    private final @NotNull UserDefinedLanguageServerDefinition serverDefinition;
    private final @NotNull Project project;

    public UserDefinedStreamConnectionProvider(@NotNull String commandLine,
                                               @NotNull Map<String, String> userEnvironmentVariables,
                                               boolean includeSystemEnvironmentVariables,
                                               @NotNull UserDefinedLanguageServerDefinition serverDefinition,
                                               @NotNull Project project) {
        super(createCommandLine(commandLine, userEnvironmentVariables, includeSystemEnvironmentVariables));
        this.project = project;
        this.serverDefinition = serverDefinition;
    }

    @Override
    public Object getInitializationOptions(@Nullable VirtualFile rootUri) {
        var settings = GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(serverDefinition.getId());
        return settings != null ? settings.getLanguageServerInitializationOptions(project) : null;
    }

    @Override
    public Object getExperimentalFeaturesPOJO() {
        var settings = GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(serverDefinition.getId());
        return settings != null ? settings.getLanguageServerExperimental(project) : null;
    }
}
