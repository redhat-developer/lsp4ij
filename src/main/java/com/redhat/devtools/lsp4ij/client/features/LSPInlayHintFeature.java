/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and inlayHint
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.server.capabilities.InlayHintCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP inlayHint feature.
 */
@ApiStatus.Experimental
public class LSPInlayHintFeature extends AbstractLSPDocumentFeature {

    private InlayHintCapabilityRegistry inlayHintCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isInlayHintSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support inlayHint and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support inlayHint and false otherwise.
     */
    public boolean isInlayHintSupported(@NotNull PsiFile file) {
        return getInlayHintCapabilityRegistry().isInlayHintSupported(file);
    }

    /**
     * Returns true if the language server can support resolve inlayHint and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the language server can support resolve inlayHint and false otherwise.
     */
    public boolean isResolveInlayHintSupported(@NotNull PsiFile file) {
        return getInlayHintCapabilityRegistry().isResolveInlayHintSupported(file);
    }

    public InlayHintCapabilityRegistry getInlayHintCapabilityRegistry() {
        if (inlayHintCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            inlayHintCapabilityRegistry = new InlayHintCapabilityRegistry(clientFeatures);
            inlayHintCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return inlayHintCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (inlayHintCapabilityRegistry != null) {
            inlayHintCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
    
}
