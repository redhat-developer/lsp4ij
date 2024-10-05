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
import com.redhat.devtools.lsp4ij.server.capabilities.DocumentHighlightCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP documentHighlight feature.
 */
@ApiStatus.Experimental
public class LSPDocumentHighlightFeature extends AbstractLSPDocumentFeature {

    private DocumentHighlightCapabilityRegistry documentHighlightCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isDocumentHighlightSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support documentHighlight and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support documentHighlight and false otherwise.
     */
    public boolean isDocumentHighlightSupported(@NotNull PsiFile file) {
        return getDocumentHighlightCapabilityRegistry().isDocumentHighlightSupported(file);
    }

    public DocumentHighlightCapabilityRegistry getDocumentHighlightCapabilityRegistry() {
        if (documentHighlightCapabilityRegistry == null) {
            documentHighlightCapabilityRegistry = new DocumentHighlightCapabilityRegistry(getClientFeatures());
            documentHighlightCapabilityRegistry.setServerCapabilities(getClientFeatures().getServerWrapper().getServerCapabilitiesSync());
        }
        return documentHighlightCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if(documentHighlightCapabilityRegistry != null) {
            documentHighlightCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
