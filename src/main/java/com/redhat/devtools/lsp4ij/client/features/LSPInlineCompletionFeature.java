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
        // Check if the server supports inline completion
        ServerCapabilities capabilities = getClientFeatures().getServerWrapper().getServerCapabilitiesSync();
        if (capabilities == null) {
            return false;
        }

        // Check for inline completion provider capability
        return capabilities.getInlineCompletionProvider() != null;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        // No capability registry to update for now
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
