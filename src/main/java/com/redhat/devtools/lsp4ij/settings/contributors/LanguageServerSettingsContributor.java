/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.contributors;

import org.jetbrains.annotations.Nullable;

/**
 * Contributes language server-specific settings for use within the IDE.
 * <p>
 * Implementations of this interface can optionally provide a {@link ServerConfigurationContributor}
 * to supply default configuration values and schema definitions for a language server.
 */
public interface LanguageServerSettingsContributor {

    /**
     * Returns a {@link ServerConfigurationContributor} that provides the default configuration
     * and schema for the language server, or {@code null} if no such contributor is defined.
     *
     * @return the {@link ServerConfigurationContributor}, or {@code null} if not available
     */
    @Nullable
    ServerConfigurationContributor getServerConfigurationContributor();

    /**
     * Returns a {@link ServerInitializationOptionsContributor} that provides the default initializationOptions
     * for the language server, or {@code null} if no such contributor is defined.
     *
     * @return the {@link ServerInitializationOptionsContributor}, or {@code null} if not available
     */
    @Nullable
    default ServerInitializationOptionsContributor getServerInitializationOptionsContributor() {
        return null;
    }

    /**
     * Returns a {@link ServerExperimentalContributor} that provides the default experimental content
     * for the language server, or {@code null} if no such contributor is defined.
     *
     * @return the {@link ServerExperimentalContributor}, or {@code null} if not available
     */
    @Nullable
    default ServerExperimentalContributor getServerExperimentalContributor() {
        return null;
    }
}
