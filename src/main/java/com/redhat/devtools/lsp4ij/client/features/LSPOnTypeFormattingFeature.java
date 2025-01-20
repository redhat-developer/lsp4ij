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

import com.intellij.psi.PsiFile;
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
}
