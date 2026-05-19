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
import com.redhat.devtools.lsp4ij.server.capabilities.InlineCompletionCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP inline completion feature.
 *
 * @since LSP 3.18.0
 */
@ApiStatus.Experimental
public class LSPInlineCompletionFeature extends AbstractLSPDocumentFeature {

    private InlineCompletionCapabilityRegistry inlineCompletionCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isInlineCompletionSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support inline completion and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support inline completion and false otherwise.
     */
    public boolean isInlineCompletionSupported(@NotNull PsiFile file) {
        return getInlineCompletionCapabilityRegistry().isInlineCompletionSupported(file);
    }

    public InlineCompletionCapabilityRegistry getInlineCompletionCapabilityRegistry() {
        if (inlineCompletionCapabilityRegistry == null) {
            initInlineCompletionCapabilityRegistry();
        }
        return inlineCompletionCapabilityRegistry;
    }

    private synchronized void initInlineCompletionCapabilityRegistry() {
        if (inlineCompletionCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        inlineCompletionCapabilityRegistry = new InlineCompletionCapabilityRegistry(clientFeatures);
        inlineCompletionCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (inlineCompletionCapabilityRegistry != null) {
            inlineCompletionCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Returns true if inline completion should be enabled for the specified file.
     * By default, returns false to avoid unexpected behavior.
     *
     * @param file the file
     * @return true if inline completion should be enabled for the specified file
     */
    @Override
    public boolean isEnabled(@NotNull PsiFile file) {
        // Inline completion is opt-in by default since it's experimental
        return super.isEnabled(file);
    }
}
