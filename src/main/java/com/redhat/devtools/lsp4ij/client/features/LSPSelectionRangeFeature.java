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
import com.redhat.devtools.lsp4ij.server.capabilities.SelectionRangeCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP selectionRange feature.
 */
@ApiStatus.Experimental
public class LSPSelectionRangeFeature extends AbstractLSPDocumentFeature {

    private SelectionRangeCapabilityRegistry selectionRangeCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isSelectionRangeSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support selectionRange and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support selectionRange and false otherwise.
     */
    public boolean isSelectionRangeSupported(@NotNull PsiFile file) {
        return getSelectionRangeCapabilityRegistry().isSelectionRangeSupported(file);
    }

    public SelectionRangeCapabilityRegistry getSelectionRangeCapabilityRegistry() {
        if (selectionRangeCapabilityRegistry == null) {
            initSelectionRangeCapabilityRegistry();
        }
        return selectionRangeCapabilityRegistry;
    }

    private synchronized void initSelectionRangeCapabilityRegistry() {
        if (selectionRangeCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        selectionRangeCapabilityRegistry = new SelectionRangeCapabilityRegistry(clientFeatures);
        selectionRangeCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (selectionRangeCapabilityRegistry != null) {
            selectionRangeCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
