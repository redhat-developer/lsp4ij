/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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

import com.redhat.devtools.lsp4ij.settings.contributors.LanguageServerSettingsContributorBase;
import org.jetbrains.annotations.NotNull;

/**
 * A language server settings contributor implementation that delegates
 * configuration, initialization options, and experimental content
 * to a user-defined language server definition.
 * <p>
 * This class acts as a simple adapter that forwards calls to
 * the provided {@link UserDefinedLanguageServerDefinition} instance,
 * which must implement the respective contributor interfaces.
 */
public class UserDefinedLanguageServerSettingsContributor extends LanguageServerSettingsContributorBase {

    /**
     * Constructs a new contributor based on the given user-defined server definition.
     *
     * @param serverDefinition the user-defined language server definition providing
     *                         configuration, initialization options, and experimental data
     */
    public UserDefinedLanguageServerSettingsContributor(@NotNull UserDefinedLanguageServerDefinition serverDefinition) {
        super.setServerConfigurationContributor(serverDefinition);
        super.setServerInitializationOptionsContributor(serverDefinition);
        super.setServerExperimentalContributor(serverDefinition);
    }
}
