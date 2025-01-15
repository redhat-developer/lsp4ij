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

package com.redhat.devtools.lsp4ij.server.definition;

import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Common interface for language server definitions that support client configuration.
 */
public interface ClientConfigurableLanguageServerDefinition {
    /**
     * Returns the language server client configuration settings if available.
     *
     * @return the client configuration settings, or <code>null</code> if settings are not found/supported
     */
    @Nullable
    ClientConfigurationSettings getLanguageServerClientConfiguration();
}
