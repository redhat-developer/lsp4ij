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
import com.redhat.devtools.lsp4ij.server.capabilities.TypeDefinitionCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP typeDefinition feature.
 */
@ApiStatus.Experimental
public class LSPTypeDefinitionFeature extends AbstractLSPDocumentFeature {

    private TypeDefinitionCapabilityRegistry typeDefinitionCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isTypeDefinitionSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support typeDefinition and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support typeDefinition and false otherwise.
     */
    public boolean isTypeDefinitionSupported(@NotNull PsiFile file) {
        return getTypeDefinitionCapabilityRegistry().isTypeDefinitionSupported(file);
    }

    public TypeDefinitionCapabilityRegistry getTypeDefinitionCapabilityRegistry() {
        if (typeDefinitionCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            typeDefinitionCapabilityRegistry = new TypeDefinitionCapabilityRegistry(clientFeatures);
            typeDefinitionCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return typeDefinitionCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (typeDefinitionCapabilityRegistry != null) {
            typeDefinitionCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
