/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
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
import com.redhat.devtools.lsp4ij.server.capabilities.LinkedEditingRangeCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP linkedEditingRange feature, which edits the ranges that a language server links, such as the declaration and
 * the uses of a local variable, together while typing in one of them.
 */
@ApiStatus.Experimental
public class LSPLinkedEditingRangeFeature extends AbstractLSPDocumentFeature {

    private LinkedEditingRangeCapabilityRegistry linkedEditingRangeCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isLinkedEditingRangeSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support linkedEditingRange and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support linkedEditingRange and false otherwise.
     */
    public boolean isLinkedEditingRangeSupported(@NotNull PsiFile file) {
        return getLinkedEditingRangeCapabilityRegistry().isLinkedEditingRangeSupported(file);
    }

    /**
     * Returns the registry of the language server's linkedEditingRange capability, static or registered.
     *
     * @return the registry of the language server's linkedEditingRange capability.
     */
    public LinkedEditingRangeCapabilityRegistry getLinkedEditingRangeCapabilityRegistry() {
        if (linkedEditingRangeCapabilityRegistry == null) {
            initLinkedEditingRangeCapabilityRegistry();
        }
        return linkedEditingRangeCapabilityRegistry;
    }

    private synchronized void initLinkedEditingRangeCapabilityRegistry() {
        if (linkedEditingRangeCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        linkedEditingRangeCapabilityRegistry = new LinkedEditingRangeCapabilityRegistry(clientFeatures);
        linkedEditingRangeCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (linkedEditingRangeCapabilityRegistry != null) {
            linkedEditingRangeCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
