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
import com.redhat.devtools.lsp4ij.server.capabilities.TypeHierarchyCapabilityRegistry;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP type hierarchy feature.
 */
@ApiStatus.Experimental
public class LSPTypeHierarchyFeature extends AbstractLSPDocumentFeature {

    private TypeHierarchyCapabilityRegistry typeHierarchyCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isTypeHierarchySupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support type hierarchy and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support type hierarchy and false otherwise.
     */
    public boolean isTypeHierarchySupported(@NotNull PsiFile file) {
        return getTypeHierarchyCapabilityRegistry().isTypeHierarchySupported(file);
    }

    public TypeHierarchyCapabilityRegistry getTypeHierarchyCapabilityRegistry() {
        if (typeHierarchyCapabilityRegistry == null) {
            initTypeHierarchyCapabilityRegistry();
        }
        return typeHierarchyCapabilityRegistry;
    }

    private synchronized void initTypeHierarchyCapabilityRegistry() {
        if (typeHierarchyCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        typeHierarchyCapabilityRegistry = new TypeHierarchyCapabilityRegistry(clientFeatures);
        typeHierarchyCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (typeHierarchyCapabilityRegistry != null) {
            typeHierarchyCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Returns the typeHierarchyItem text from the LSP typeHierarchyItem and null otherwise (to ignore the LSP TypeHierarchyItem).
     *
     * @param typeHierarchyItem the LSP typeHierarchyItem
     * @return the typeHierarchyItem text from the LSP typeHierarchyItem and null otherwise (to ignore the LSP TypeHierarchyItem).
     */
    @Nullable
    public String getText(@NotNull TypeHierarchyItem typeHierarchyItem) {
        return typeHierarchyItem.getName();
    }

}
