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
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.lang.LanguageFormatting;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.server.capabilities.OnTypeFormattingCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP onTypeFormatting feature.
 */
@ApiStatus.Experimental
public class LSPOnTypeFormattingFeature extends AbstractLSPDocumentFeature {

    private OnTypeFormattingCapabilityRegistry onTypeFormattingCapabilityRegistry;

    @Override
    public boolean isEnabled(@NotNull PsiFile file) {
        // Don't suspend typing to wait for a server to start
        if (getServerStatus() != ServerStatus.started) {
            return false;
        }

        // Make sure the feature is enabled in client config
        LSPFormattingFeature formattingFeature = getClientFeatures().getFormattingFeature();
        if (!formattingFeature.isOnTypeFormattingEnabled(file)) {
            return false;
        }

        // Need to perform the same check as in LSPFormattingFeature.isEnabled() to ensure that LSP formatting should be used
        if (!formattingFeature.isExistingFormatterOverrideable(file) &&
            (LanguageFormatting.INSTANCE.forContext(file) != null)) {
            return false;
        }

        return super.isEnabled(file);
    }

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isOnTypeFormattingSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support onTypeFormatting and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support onTypeFormatting and false otherwise.
     */
    public boolean isOnTypeFormattingSupported(@NotNull PsiFile file) {
        return getOnTypeFormattingCapabilityRegistry().isOnTypeFormattingSupported(file);
    }

    public OnTypeFormattingCapabilityRegistry getOnTypeFormattingCapabilityRegistry() {
        if (onTypeFormattingCapabilityRegistry == null) {
            initOnTypeFormattingCapabilityRegistry();
        }
        return onTypeFormattingCapabilityRegistry;
    }

    private synchronized void initOnTypeFormattingCapabilityRegistry() {
        if (onTypeFormattingCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        onTypeFormattingCapabilityRegistry = new OnTypeFormattingCapabilityRegistry(clientFeatures);
        onTypeFormattingCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (onTypeFormattingCapabilityRegistry != null) {
            onTypeFormattingCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Determines whether or not the given character is an on-type formatting trigger for the file according to its
     * language server.
     *
     * @param file      the file.
     * @param charTyped the typed character.
     * @return true if the given character is an on-type formatting trigger for the file and false otherwise.
     */
    public boolean isOnTypeFormattingTriggerCharacter(@NotNull PsiFile file, @Nullable String charTyped) {
        return getOnTypeFormattingCapabilityRegistry().isOnTypeFormattingTriggerCharacter(file, charTyped);
    }
}
