/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.google.gson.JsonElement;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.server.SocketStreamConnectionProvider;
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

/**
 * {@link SocketStreamConnectionProvider} of a user-defined language server, which LSP4IJ reaches over a socket. It
 * gives the language server the initialization options and experimental settings of the user, as
 * {@link UserDefinedStreamConnectionProvider} does. This API is experimental: it may change in a future release.
 */
@ApiStatus.Experimental
public class UserDefinedSocketStreamConnectionProvider extends SocketStreamConnectionProvider {

    private final @NotNull UserDefinedLanguageServerDefinition serverDefinition;
    private final @NotNull Project project;

    public UserDefinedSocketStreamConnectionProvider(@NotNull Direction direction,
                                                     @NotNull String host,
                                                     int port,
                                                     @Nullable IntFunction<GeneralCommandLine> commandLine,
                                                     @NotNull UserDefinedLanguageServerDefinition serverDefinition,
                                                     @NotNull Project project) {
        super(direction, host, port, commandLine);
        this.serverDefinition = serverDefinition;
        this.project = project;
    }

    @Override
    public @Nullable JsonElement getInitializationOptions(@Nullable VirtualFile rootUri) {
        // The settings parse the user's JSON into a JsonElement, or have none
        var settings = settings();
        return settings != null && settings.getLanguageServerInitializationOptions(project) instanceof JsonElement options
                ? options : null;
    }

    @Override
    public @Nullable JsonElement getExperimentalFeaturesPOJO() {
        var settings = settings();
        return settings != null && settings.getLanguageServerExperimental(project) instanceof JsonElement experimental
                ? experimental : null;
    }

    private @Nullable LanguageServerSettings.LanguageServerDefinitionSettings settings() {
        return GlobalLanguageServerSettings.getInstance().getLanguageServerSettings(serverDefinition.getId());
    }
}
