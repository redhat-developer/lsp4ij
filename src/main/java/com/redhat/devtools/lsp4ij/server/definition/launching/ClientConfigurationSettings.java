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

import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature.FormattingScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
     * Client-side breadcrumbs settings.
     */
    public static class ClientConfigurationBreadcrumbsSettings {
        /**
         * Whether or not the document symbols-based breadcrumbs info provider is enabled. Defaults to true.
         */
        public boolean enabled = true;
    }

    /**
     * Client-side editor behavior settings.
     */
    public static class ClientConfigurationEditorSettings {
        /**
         * Whether or not editor improvements for string literals are enabled. Defaults to true.
         */
        public boolean enableStringLiteralImprovements = true;

        /**
         * Whether or not editor improvements for statement terminators are enabled. Defaults to true. Also requires
         * {@link ClientConfigurationSettings#statementTerminatorCharacters} to be set properly for the language.
         */
        public boolean enableStatementTerminatorImprovements = true;

        /**
         * Whether or not the fix for <a href="https://youtrack.jetbrains.com/issue/IJPL-159454">IJPL-159454</a> is
         * enabled. Defaults to true.
         */
        public boolean enableEnterBetweenBracesFix = true;

        /**
         * Whether or not editor improvements for nested braces/brackets/parentheses in TextMate files are enabled.
         * Defaults to true.
         */
        public boolean enableTextMateNestedBracesImprovements = true;

        /**
         * Whether or not the semantic tokens-based file view provider is enabled. Defaults to true.
         */
        public boolean enableSemanticTokensFileViewProvider = true;
    }

    /**
     * Server-side (LSP) <code>textDocument/onTypeFormatting</code> feature settings.
     */
    public static class ServerSideOnTypeFormattingSettings {
        /**
         * Whether or not server-side on-type formatting is enabled if <code>textDocument/onTypeFormatting</code> is
         * supported by the language server. Defaults to true.
         */
        public boolean enabled = true;
    }

    /**
     * Client-side on-type formatting feature settings.
     */
    public static class ClientSideOnTypeFormattingSettings {
        /**
         * Whether or not to format on close brace using client-side on-type formatting. Defaults to false.
         */
        public boolean formatOnCloseBrace = false;

        /**
         * The specific close brace characters that should trigger client-side on-type formatting. Defaults to the
         * language's close brace characters.
         */
        public String formatOnCloseBraceCharacters = "";

        /**
         * The scope that should be formatted using client-side on-type formatting when a close brace is typed. Allowed
         * values are {@link FormattingScope#CODE_BLOCK CODE_BLOCK} and {@link FormattingScope#FILE FILE}. Defaults to
         * {@link FormattingScope#CODE_BLOCK CODE_BLOCK}.
         */
        public FormattingScope formatOnCloseBraceScope = FormattingScope.CODE_BLOCK;

        /**
         * Whether or not to format on statement terminator using client-side on-type formatting. Defaults to false.
         */
        public boolean formatOnStatementTerminator = false;

        /**
         * The specific statement terminator characters that should trigger client-side on-type formatting. Defaults to
         * the {@link ClientConfigurationSettings#statementTerminatorCharacters}.
         */
        public @NotNull String formatOnStatementTerminatorCharacters = "";

        /**
         * The scope that should be formatted using client-side on-type formatting when a statement terminator is typed.
         * Allowed values are {@link FormattingScope#STATEMENT STATEMENT}, {@link FormattingScope#CODE_BLOCK CODE_BLOCK},
         * and {@link FormattingScope#FILE FILE}. Defaults to {@link FormattingScope#STATEMENT STATEMENT}.
         */
        public FormattingScope formatOnStatementTerminatorScope = FormattingScope.STATEMENT;

        /**
         * Whether or not to format using client-side on-type formatting on completion trigger. Defaults to false.
         */
        public boolean formatOnCompletionTrigger = false;

        /**
         * The specific completion trigger characters that should trigger client-side on-type formatting. Defaults to
         * the language's completion trigger characters.
         */
        public String formatOnCompletionTriggerCharacters = "";
    }

    /**
     * On-type formatting settings
     */
    public static class OnTypeFormattingSettings {
        /**
         * Server-side (LSP) <code>textDocument/onTypeFormatting</code> feature settings.
         */
        public @NotNull ServerSideOnTypeFormattingSettings serverSide = new ServerSideOnTypeFormattingSettings();

        /**
         * Client-side on-type formatting feature settings.
         */
        public @NotNull ClientSideOnTypeFormattingSettings clientSide = new ClientSideOnTypeFormattingSettings();
    }

    /**
     * Client-side formatter settings.
     */
    public static class ClientConfigurationFormatSettings {
        /**
         * On-type formatting settings
         */
        public @NotNull OnTypeFormattingSettings onTypeFormatting = new OnTypeFormattingSettings();
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
     * The line comment prefix in the language grammar. Defaults to none.
     */
    public @Nullable String lineCommentPrefix = null;

    /**
     * The block comment prefix in the language grammar. Defaults to none.
     */
    public @Nullable String blockCommentPrefix = null;

    /**
     * The block comment suffix in the language grammar. Defaults to none.
     */
    public @Nullable String blockCommentSuffix = null;

    /**
     * The statement terminator characters in the language grammar. Defaults to none.
     */
    public @NotNull String statementTerminatorCharacters = "";

    /**
     * Client-side code completion settings.
     */
    public @NotNull ClientConfigurationCompletionSettings completion = new ClientConfigurationCompletionSettings();

    /**
     * Client-side breadcrumbs settings.
     */
    public @NotNull ClientConfigurationBreadcrumbsSettings breadcrumbs = new ClientConfigurationBreadcrumbsSettings();

    /**
     * Client-side editor behavior settings.
     */
    public @NotNull ClientConfigurationEditorSettings editor = new ClientConfigurationEditorSettings();

    /**
     * Client-side format settings.
     */
    public @NotNull ClientConfigurationFormatSettings format = new ClientConfigurationFormatSettings();

    /**
     * Client-side workspace symbol settings.
     */
    public @NotNull ClientConfigurationWorkspaceSymbolSettings workspaceSymbol = new ClientConfigurationWorkspaceSymbolSettings();
}