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
import com.redhat.devtools.lsp4ij.server.capabilities.DocumentColorCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP documentColor feature.
 */
@ApiStatus.Experimental
public class LSPDocumentColorFeature extends AbstractLSPDocumentFeature {

    private DocumentColorCapabilityRegistry documentColorCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isDocumentColorSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support documentColor and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support documentColor and false otherwise.
     */
    public boolean isDocumentColorSupported(@NotNull PsiFile file) {
        return getDocumentColorCapabilityRegistry().isDocumentColorSupported(file);
    }

    public DocumentColorCapabilityRegistry getDocumentColorCapabilityRegistry() {
        if (documentColorCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            documentColorCapabilityRegistry = new DocumentColorCapabilityRegistry(clientFeatures);
            documentColorCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return documentColorCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (documentColorCapabilityRegistry != null) {
            documentColorCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
