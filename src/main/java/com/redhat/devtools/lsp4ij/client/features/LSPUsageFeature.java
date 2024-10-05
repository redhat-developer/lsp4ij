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
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP usage feature.
 */
@ApiStatus.Experimental
public class LSPUsageFeature extends AbstractLSPDocumentFeature {

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isUsageSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support usage and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support usage and false otherwise.
     */
    public boolean isUsageSupported(@NotNull PsiFile file) {
        var clientFeature = getClientFeatures();
        return clientFeature.getDeclarationFeature().isDeclarationSupported(file) ||
                clientFeature.getTypeDefinitionFeature().isTypeDefinitionSupported(file) ||
                clientFeature.getDefinitionFeature().isDefinitionSupported(file) ||
                clientFeature.getReferencesFeature().isReferencesSupported(file) ||
                clientFeature.getImplementationFeature().isImplementationSupported(file);
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        // Do nothing
    }
}
