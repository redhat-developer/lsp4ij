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
 * Base implementation of {@link LanguageServerSettingsContributor} that provides
 * mutable backing fields for various server-related contributors such as
 * configuration, initialization options, and experimental capabilities.
 * <p>
 * This class can be extended or instantiated directly to manage and expose
 * default configuration content, initialization options, and experimental
 * data for a language server.
 * <p>
 * Typical usage involves setting one or more of the contained contributors to provide
 * default values or schemas used during language server initialization or
 * configuration update phases.
 */
public class LanguageServerSettingsContributorBase implements LanguageServerSettingsContributor {

    private @Nullable ServerConfigurationContributor serverConfigurationContributor;
    private @Nullable ServerInitializationOptionsContributor serverInitializationOptionsContributor;
    private @Nullable ServerExperimentalContributor serverExperimentalContributor;

    /**
     * Returns the current {@link ServerConfigurationContributor} instance, if any.
     *
     * @return the {@link ServerConfigurationContributor}, or {@code null} if none is set
     */
    @Override
    public @Nullable ServerConfigurationContributor getServerConfigurationContributor() {
        return serverConfigurationContributor;
    }

    /**
     * Returns the current {@link ServerInitializationOptionsContributor} instance, if any.
     *
     * @return the {@link ServerInitializationOptionsContributor}, or {@code null} if none is set
     */
    @Override
    public @Nullable ServerInitializationOptionsContributor getServerInitializationOptionsContributor() {
        return serverInitializationOptionsContributor;
    }

    /**
     * Returns the current {@link ServerExperimentalContributor} instance, if any.
     *
     * @return the {@link ServerExperimentalContributor}, or {@code null} if none is set
     */
    @Override
    public @Nullable ServerExperimentalContributor getServerExperimentalContributor() {
        return serverExperimentalContributor;
    }

    /**
     * Sets the {@link ServerConfigurationContributor} to be used by this contributor.
     *
     * @param serverConfigurationContributor the contributor to set, or {@code null} to unset it
     */
    public void setServerConfigurationContributor(@Nullable ServerConfigurationContributor serverConfigurationContributor) {
        this.serverConfigurationContributor = serverConfigurationContributor;
    }

    /**
     * Sets the {@link ServerInitializationOptionsContributor} to be used by this contributor.
     *
     * @param serverInitializationOptionsContributor the contributor to set, or {@code null} to unset it
     */
    public void setServerInitializationOptionsContributor(@Nullable ServerInitializationOptionsContributor serverInitializationOptionsContributor) {
        this.serverInitializationOptionsContributor = serverInitializationOptionsContributor;
    }

    /**
     * Sets the {@link ServerExperimentalContributor} to be used by this contributor.
     *
     * @param serverExperimentalContributor the contributor to set, or {@code null} to unset it
     */
    public void setServerExperimentalContributor(@Nullable ServerExperimentalContributor serverExperimentalContributor) {
        this.serverExperimentalContributor = serverExperimentalContributor;
    }
}
