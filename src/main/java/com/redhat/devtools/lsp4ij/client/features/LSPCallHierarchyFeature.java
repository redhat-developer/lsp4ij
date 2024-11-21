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
import com.redhat.devtools.lsp4ij.server.capabilities.CallHierarchyCapabilityRegistry;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP call hierarchy feature.
 */
@ApiStatus.Experimental
public class LSPCallHierarchyFeature extends AbstractLSPDocumentFeature {

    private CallHierarchyCapabilityRegistry callHierarchyCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isCallHierarchySupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support call hierarchy and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support call hierarchy and false otherwise.
     */
    public boolean isCallHierarchySupported(@NotNull PsiFile file) {
        return getCallHierarchyCapabilityRegistry().isCallHierarchySupported(file);
    }

    public CallHierarchyCapabilityRegistry getCallHierarchyCapabilityRegistry() {
        if (callHierarchyCapabilityRegistry == null) {
            initCallHierarchyCapabilityRegistry();
        }
        return callHierarchyCapabilityRegistry;
    }

    private synchronized void initCallHierarchyCapabilityRegistry() {
        if (callHierarchyCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        callHierarchyCapabilityRegistry = new CallHierarchyCapabilityRegistry(clientFeatures);
        callHierarchyCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (callHierarchyCapabilityRegistry != null) {
            callHierarchyCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Returns the callHierarchyItem text from the LSP callHierarchyItem and null otherwise (to ignore the LSP CallHierarchyItem).
     *
     * @param callHierarchyItem the LSP callHierarchyItem
     * @return the callHierarchyItem text from the LSP callHierarchyItem and null otherwise (to ignore the LSP CallHierarchyItem).
     */
    @Nullable
    public String getText(@NotNull CallHierarchyItem callHierarchyItem) {
        return callHierarchyItem.getName();
    }

}
