/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.lang.LanguageFormatting;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.server.capabilities.DocumentFormattingCapabilityRegistry;
import com.redhat.devtools.lsp4ij.server.capabilities.DocumentRangeFormattingCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP formatting feature.
 * <p>
 * The following code snippet demonstrates how to use this class to allow a language server to override an existing
 * formatter service:
 * <pre>{@code
 * public class MyLSPFormattingFeature extends LSPFormattingFeature {
 *     @Override
 *     protected boolean isExistingFormatterOverrideable(@NotNull PsiFile file) {
 *         // returns true even if there is a custom formatter
 *         return true;
 *     }
 * }
 * }</pre>
 * See the documentation of {@link #isExistingFormatterOverrideable(PsiFile)} for more details.
 * <p>
 * Additional information is available on <a href="https://github.com/redhat-developer/lsp4ij/blob/main/docs/LSPApi.md#lsp-formatting-feature">GitHub</a>
 */
@ApiStatus.Experimental
public class LSPFormattingFeature extends AbstractLSPDocumentFeature {

    private DocumentFormattingCapabilityRegistry formattingCapabilityRegistry;

    private DocumentRangeFormattingCapabilityRegistry rangeFormattingCapabilityRegistry;

    @Override
    public boolean isEnabled(@NotNull PsiFile file) {
        if (!isExistingFormatterOverrideable(file) && LanguageFormatting.INSTANCE.forContext(file) != null) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isFormattingSupported(file);
    }

    /**
     * This specifies whether the language server should override a formatting service registered for languages in a file.
     * <p>
     * If <code>true</code>, then the language server will be used for the <code>Reformat Code</code> action.
     * <p>
     * If <code>false</code>, then formatters registered for the language will be preferred, but the language server may still be
     * used if there is no registered formatter available.
     *
     * @return true to use the language server for code formatting and false to use plugin-provided/built-in formatters.
     *
     * @apiNote This method will only be called with files that contain a language supported by this language server.
     */
    protected boolean isExistingFormatterOverrideable(@NotNull PsiFile file) {
        return false;
    }

    /**
     * Returns true if the file associated with a language server can support formatting and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support formatting and false otherwise.
     */
    public boolean isFormattingSupported(@NotNull PsiFile file) {
        return getFormattingCapabilityRegistry().isFormattingSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support range formatting and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support range formatting and false otherwise.
     */
    public boolean isRangeFormattingSupported(@NotNull PsiFile file) {
        return getRangeFormattingCapabilityRegistry().isRangeFormattingSupported(file);
    }

    public DocumentFormattingCapabilityRegistry getFormattingCapabilityRegistry() {
        if (formattingCapabilityRegistry == null) {
            initDocumentFormattingCapabilityRegistry();
        }
        return formattingCapabilityRegistry;
    }

    private synchronized void initDocumentFormattingCapabilityRegistry() {
        if (formattingCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        formattingCapabilityRegistry = new DocumentFormattingCapabilityRegistry(clientFeatures);
        formattingCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    public DocumentRangeFormattingCapabilityRegistry getRangeFormattingCapabilityRegistry() {
        if (rangeFormattingCapabilityRegistry == null) {
            initDocumentRangeFormattingCapabilityRegistry();
        }
        return rangeFormattingCapabilityRegistry;
    }

    private synchronized void initDocumentRangeFormattingCapabilityRegistry() {
        if (rangeFormattingCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        rangeFormattingCapabilityRegistry = new DocumentRangeFormattingCapabilityRegistry(clientFeatures);
        rangeFormattingCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (formattingCapabilityRegistry != null) {
            formattingCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
        if (rangeFormattingCapabilityRegistry != null) {
            rangeFormattingCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    // Server-side on-type formatting

    /**
     * Whether or not server-side on-type formatting is enabled if <code>textDocument/onTypeFormatting</code> is
     * supported by the language server. Defaults to true.
     *
     * @param file the file
     * @return true if server-side on-type formatting should be enabled for the file; otherwise false
     */
    public boolean isOnTypeFormattingEnabled(@NotNull PsiFile file) {
        // Default to enabled
        return true;
    }

    // Client-side on-type formatting

    /**
     * Supported formatting scopes.
     */
    public enum FormattingScope {
        /**
         * The current statement if one can be identified.
         */
        STATEMENT,
        /**
         * The current code block if one can be identified.
         */
        CODE_BLOCK,
        /**
         * The current file.
         */
        FILE
    }

    /**
     * Whether or not to format on close brace using client-side on-type formatting. Defaults to false.
     *
     * @param file the file
     * @return true if the file should be formatted when close braces are typed; otherwise false
     */
    public boolean isFormatOnCloseBrace(@NotNull PsiFile file) {
        // Default to disabled
        return false;
    }

    /**
     * The specific close brace characters that should trigger client-side on-type formatting.
     *
     * @param file the file
     * @return the close brace characters that should trigger on-type formatting or null if the language's standard
     * close brace characters should be used
     */
    @Nullable
    public String getFormatOnCloseBraceCharacters(@NotNull PsiFile file) {
        // Default to the language's standard close brace characters
        return null;
    }

    /**
     * The scope that should be formatted using client-side on-type formatting when a close brace is typed. Allowed
     * values are {@link FormattingScope#CODE_BLOCK CODE_BLOCK} and {@link FormattingScope#FILE FILE}. Defaults to
     * {@link FormattingScope#CODE_BLOCK CODE_BLOCK}.
     *
     * @param file the file
     * @return the format scope
     */
    @NotNull
    public FormattingScope getFormatOnCloseBraceScope(@NotNull PsiFile file) {
        // Default to CODE_BLOCK
        return FormattingScope.CODE_BLOCK;
    }

    /**
     * Whether or not to format on statement terminator using client-side on-type formatting. Defaults to false.
     *
     * @param file the file
     * @return true if the file should be formatted when statement terminators are typed; otherwise false
     */
    public boolean isFormatOnStatementTerminator(@NotNull PsiFile file) {
        // Default to disabled
        return false;
    }

    /**
     * The specific statement terminator characters that should trigger client-side on-type formatting.
     *
     * @param file the file
     * @return the statement terminator characters that should trigger on-type formatting
     */
    @Nullable
    public String getFormatOnStatementTerminatorCharacters(@NotNull PsiFile file) {
        // Default to none
        return null;
    }

    /**
     * The scope that should be formatted using client-side on-type formatting when a statement terminator is typed.
     * Allowed values are {@link FormattingScope#STATEMENT STATEMENT}, {@link FormattingScope#CODE_BLOCK CODE_BLOCK},
     * and {@link FormattingScope#FILE FILE}. Defaults to {@link FormattingScope#STATEMENT STATEMENT}.
     *
     * @param file the file
     * @return the format scope
     */
    @NotNull
    public FormattingScope getFormatOnStatementTerminatorScope(@NotNull PsiFile file) {
        // Default to STATEMENT
        return FormattingScope.STATEMENT;
    }

    /**
     * Whether or not to format using client-side on-type formatting on completion trigger. Defaults to false.
     *
     * @param file the file
     * @return true if the file should be formatted when completion triggers are typed; otherwise false
     */
    public boolean isFormatOnCompletionTrigger(@NotNull PsiFile file) {
        // Default to disabled
        return false;
    }

    /**
     * The specific completion trigger characters that should trigger client-side on-type formatting.
     *
     * @param file the file
     * @return the completion trigger characters that should trigger on-type formatting or null if the language's
     * standard completion trigger characters should be used
     */
    @Nullable
    public String getFormatOnCompletionTriggerCharacters(@NotNull PsiFile file) {
        // Default to the language's standard completion trigger characters
        return null;
    }
}
