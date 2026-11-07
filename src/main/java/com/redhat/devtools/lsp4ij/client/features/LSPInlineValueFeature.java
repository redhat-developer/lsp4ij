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
import com.redhat.devtools.lsp4ij.server.capabilities.InlineValueCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP inlineValue feature, which gives the positions of the inline values that a DAP debug session shows in the
 * editor while the program is stopped.
 */
@ApiStatus.Experimental
public class LSPInlineValueFeature extends AbstractLSPDocumentFeature {

    private InlineValueCapabilityRegistry inlineValueCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isInlineValueSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support inlineValue and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support inlineValue and false otherwise.
     */
    public boolean isInlineValueSupported(@NotNull PsiFile file) {
        return getInlineValueCapabilityRegistry().isInlineValueSupported(file);
    }

    public InlineValueCapabilityRegistry getInlineValueCapabilityRegistry() {
        if (inlineValueCapabilityRegistry == null) {
            initInlineValueCapabilityRegistry();
        }
        return inlineValueCapabilityRegistry;
    }

    private synchronized void initInlineValueCapabilityRegistry() {
        if (inlineValueCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        inlineValueCapabilityRegistry = new InlineValueCapabilityRegistry(clientFeatures);
        inlineValueCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (inlineValueCapabilityRegistry != null) {
            inlineValueCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
