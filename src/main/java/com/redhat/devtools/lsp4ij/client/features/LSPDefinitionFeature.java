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
import com.redhat.devtools.lsp4ij.server.capabilities.DefinitionCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP definition feature.
 */
@ApiStatus.Experimental
public class LSPDefinitionFeature extends AbstractLSPDocumentFeature {

    private DefinitionCapabilityRegistry definitionCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isDefinitionSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support definition and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support definition and false otherwise.
     */
    public boolean isDefinitionSupported(@NotNull PsiFile file) {
        return getDefinitionCapabilityRegistry().isDefinitionSupported(file);
    }

    public DefinitionCapabilityRegistry getDefinitionCapabilityRegistry() {
        if (definitionCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            definitionCapabilityRegistry = new DefinitionCapabilityRegistry(clientFeatures);
            definitionCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return definitionCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (definitionCapabilityRegistry != null) {
            definitionCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }
}
