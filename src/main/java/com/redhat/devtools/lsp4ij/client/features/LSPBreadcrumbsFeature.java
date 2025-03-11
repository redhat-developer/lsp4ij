/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Breadcrumbs/sticky lines feature. This does not correspond to an actual LSP feature but is built upon
 * {@link LSPDocumentSymbolFeature}.
 */
@ApiStatus.Experimental
public class LSPBreadcrumbsFeature {

    private LSPClientFeatures clientFeatures;

    /**
     * Creates the editor behavior feature for the containing client features.
     *
     * @param clientFeatures the client features
     */
    public LSPBreadcrumbsFeature(@NotNull LSPClientFeatures clientFeatures) {
        this.clientFeatures = clientFeatures;
    }

    /**
     * Sets the containing client features.
     *
     * @param clientFeatures the client features.
     */
    void setClientFeatures(@NotNull LSPClientFeatures clientFeatures) {
        this.clientFeatures = clientFeatures;
    }

    /**
     * Returns the containing client features.
     *
     * @return the client features
     */
    @NotNull
    protected LSPClientFeatures getClientFeatures() {
        return clientFeatures;
    }

    /**
     * Whether or not the document symbols-based breadcrumbs info provider is enabled.
     *
     * @param file the file
     * @return true if the document symbols-based breadcrumbs info provider is enabled; otherwise false
     */
    public boolean isEnabled(@NotNull PsiFile file) {
        // Default to enabled
        return true;
    }
}
