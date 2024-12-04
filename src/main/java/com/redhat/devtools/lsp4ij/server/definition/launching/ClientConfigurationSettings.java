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

import org.jetbrains.annotations.NotNull;

/**
 * Client-side settings for a user-defined language server configuration.
 */
public class ClientConfigurationSettings {
    /**
     * Client-side code completion settings.
     */
    public static class ClientConfigurationCompletionSettings {
        /**
         * Whether or not completions should be offered as case-sensitive. Defaults to false.
         */
        public boolean caseSensitive = false;
    }

    /**
     * Client-side code workspace symbol settings.
     */
    public static class ClientConfigurationWorkspaceSymbolSettings {
        /**
         * Whether or not completions should be offered as case-sensitive. Defaults to false.
         */
        public boolean supportsGotoClass = false;
    }

    /**
     * Client-side code completion settings
     */
    public @NotNull ClientConfigurationCompletionSettings completions = new ClientConfigurationCompletionSettings();

    /**
     * Client-side code workspace symbol settings
     */
    public @NotNull ClientConfigurationWorkspaceSymbolSettings workspaceSymbols = new ClientConfigurationWorkspaceSymbolSettings();
}
