/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and references
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.server.capabilities.ReferencesCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP references feature.
 */
@ApiStatus.Experimental
public class LSPReferencesFeature extends AbstractLSPDocumentFeature {

    private ReferencesCapabilityRegistry referencesCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isReferencesSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support references and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support references and false otherwise.
     */
    public boolean isReferencesSupported(@NotNull PsiFile file) {
        return getReferencesCapabilityRegistry().isReferencesSupported(file);
    }

    public ReferencesCapabilityRegistry getReferencesCapabilityRegistry() {
        if (referencesCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            referencesCapabilityRegistry = new ReferencesCapabilityRegistry(clientFeatures);
            referencesCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return referencesCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (referencesCapabilityRegistry != null) {
            referencesCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
    
}
