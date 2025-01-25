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
         * Whether or not client-side context-aware completion sorting should be used. Defaults to false.
         */
        public boolean useContextAwareSorting = false;
        /**
         * Whether or not an editor template should be used for invocation-only snippets. Defaults to true.
         */
        public boolean useTemplateForInvocationOnlySnippet = true;
    }

    /**
     * Client-side format settings.
     */
    public static class ClientConfigurationFormatSettings {
        /**
         * Whether or not server-side on-type formatting is enabled if <code>textDocument/onTypeFormatting</code> is
         * supported by the language server. Defaults to true.
         */
        public boolean textDocumentOnTypeFormattingEnabled = true;
    }

    /**
     * Client-side workspace symbol settings.
     */
    public static class ClientConfigurationWorkspaceSymbolSettings {
        /**
         * Whether or not the language server can support the IDE's Go To Class action efficiently. Defaults to false.
         */
        public boolean supportsGotoClass = false;
    }

    /**
     * Whether or not the language grammar is case-sensitive. Defaults to false.
     */
    public boolean caseSensitive = false;

    /**
     * Client-side code completion settings
     */
    public @NotNull ClientConfigurationCompletionSettings completion = new ClientConfigurationCompletionSettings();

    /**
     * Client-side format settings.
     */
    public @NotNull ClientConfigurationFormatSettings format = new ClientConfigurationFormatSettings();

    /**
     * Client-side workspace symbol settings
     */
    public @NotNull ClientConfigurationWorkspaceSymbolSettings workspaceSymbol = new ClientConfigurationWorkspaceSymbolSettings();
}