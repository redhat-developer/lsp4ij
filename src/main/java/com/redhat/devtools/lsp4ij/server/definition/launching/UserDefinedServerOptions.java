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
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the initialization options and experimental capabilities of a user-defined language server from its settings,
 * for the connection providers of every transport, so that they send the same.
 */
final class UserDefinedServerOptions {

    private UserDefinedServerOptions() {
    }

    /**
     * Returns the initialization options of the given language server, or null if its settings have none.
     */
    static @Nullable JsonElement initializationOptions(@NotNull UserDefinedLanguageServerDefinition definition,
                                                       @NotNull Project project) {
        var settings = settings(definition);
        // The settings parse the user's JSON into a JsonElement, or have none
        return settings != null && settings.getLanguageServerInitializationOptions(project) instanceof JsonElement options
                ? options : null;
    }

    /**
     * Returns the experimental capabilities of the given language server, or null if its settings have none.
     */
    static @Nullable JsonElement experimental(@NotNull UserDefinedLanguageServerDefinition definition,
                                              @NotNull Project project) {
        var settings = settings(definition);
        return settings != null && settings.getLanguageServerExperimental(project) instanceof JsonElement experimental
                ? experimental : null;
    }

    private static @Nullable LanguageServerSettings.LanguageServerDefinitionSettings settings(
            @NotNull UserDefinedLanguageServerDefinition definition) {
        return GlobalLanguageServerSettings.getInstance().getLanguageServerSettings(definition.getId());
    }
}
