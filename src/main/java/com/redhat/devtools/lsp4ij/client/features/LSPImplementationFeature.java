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

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.server.capabilities.ImplementationCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP implementation feature.
 */
@ApiStatus.Experimental
public class LSPImplementationFeature extends AbstractLSPDocumentFeature {

    private ImplementationCapabilityRegistry implementationCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isImplementationSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support implementation and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support implementation and false otherwise.
     */
    public boolean isImplementationSupported(@NotNull PsiFile file) {
        return getImplementationCapabilityRegistry().isImplementationSupported(file);
    }

    public ImplementationCapabilityRegistry getImplementationCapabilityRegistry() {
        if (implementationCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            implementationCapabilityRegistry = new ImplementationCapabilityRegistry(clientFeatures);
            implementationCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return implementationCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (implementationCapabilityRegistry != null) {
            implementationCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

}
