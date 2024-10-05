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
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.server.capabilities.RenameCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP rename feature.
 */
@ApiStatus.Experimental
public class LSPRenameFeature extends AbstractLSPDocumentFeature {

    private RenameCapabilityRegistry renameCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isRenameSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support rename and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support rename and false otherwise.
     */
    public boolean isRenameSupported(@NotNull PsiFile file) {
        return getRenameCapabilityRegistry().isRenameSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support prepareRename and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support prepareRename and false otherwise.
     */
    public boolean isPrepareRenameSupported(@NotNull PsiFile file) {
        return getRenameCapabilityRegistry().isPrepareRenameSupported(file);
    }

    public RenameCapabilityRegistry getRenameCapabilityRegistry() {
        if (renameCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            renameCapabilityRegistry = new RenameCapabilityRegistry(clientFeatures);
            renameCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return renameCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (renameCapabilityRegistry != null) {
            renameCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    public boolean isWillRenameFilesSupported(@NotNull PsiFile file) {
        // TODO implement documentSelector to use language of the given file
        return LanguageServerItem.isWillRenameFilesSupported(getClientFeatures().getServerWrapper().getServerCapabilitiesSync());
    }

    /**
     * Returns true if the given file support the 'workspace/didRenameFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/didRenameFiles' and false otherwise.
     */
    public boolean isDidRenameFilesSupported(@NotNull PsiFile file) {
        return getClientFeatures().getServerWrapper().isDidRenameFilesSupported(file);
    }
}
