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
import com.redhat.devtools.lsp4ij.server.capabilities.FoldingRangeCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP foldingRange feature.
 */
@ApiStatus.Experimental
public class LSPFoldingRangeFeature extends AbstractLSPDocumentFeature {

    private FoldingRangeCapabilityRegistry foldingRangeCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isFoldingRangeSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support foldingRange and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support foldingRange and false otherwise.
     */
    public boolean isFoldingRangeSupported(@NotNull PsiFile file) {
        return getFoldingRangeCapabilityRegistry().isFoldingRangeSupported(file);
    }

    public FoldingRangeCapabilityRegistry getFoldingRangeCapabilityRegistry() {
        if (foldingRangeCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            foldingRangeCapabilityRegistry = new FoldingRangeCapabilityRegistry(clientFeatures);
            foldingRangeCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return foldingRangeCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (foldingRangeCapabilityRegistry != null) {
            foldingRangeCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
