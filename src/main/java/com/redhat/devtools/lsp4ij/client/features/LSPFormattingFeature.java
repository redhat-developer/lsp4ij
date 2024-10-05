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
            var clientFeatures = getClientFeatures();
            formattingCapabilityRegistry = new DocumentFormattingCapabilityRegistry(clientFeatures);
            formattingCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return formattingCapabilityRegistry;
    }

    public DocumentRangeFormattingCapabilityRegistry getRangeFormattingCapabilityRegistry() {
        if (rangeFormattingCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            rangeFormattingCapabilityRegistry = new DocumentRangeFormattingCapabilityRegistry(clientFeatures);
            rangeFormattingCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return rangeFormattingCapabilityRegistry;
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
}
