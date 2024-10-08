/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and signatureHelp
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.server.capabilities.SignatureHelpCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP signatureHelp feature.
 */
@ApiStatus.Experimental
public class LSPSignatureHelpFeature extends AbstractLSPDocumentFeature {

    private SignatureHelpCapabilityRegistry signatureHelpCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isSignatureHelpSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support signatureHelp and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support signatureHelp and false otherwise.
     */
    public boolean isSignatureHelpSupported(@NotNull PsiFile file) {
        return getSignatureHelpCapabilityRegistry().isSignatureHelpSupported(file);
    }

    public SignatureHelpCapabilityRegistry getSignatureHelpCapabilityRegistry() {
        if (signatureHelpCapabilityRegistry == null) {
            var clientFeatures = getClientFeatures();
            signatureHelpCapabilityRegistry = new SignatureHelpCapabilityRegistry(clientFeatures);
            signatureHelpCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
        }
        return signatureHelpCapabilityRegistry;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (signatureHelpCapabilityRegistry != null) {
            signatureHelpCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Returns true if the given character is defined as "signature trigger" in the server capability of the language server and false otherwise.
     *
     * @param file the file.
     * @param charTyped the current typed character.
     * @return true if the given character is defined as "signature trigger" in the server capability of the language server and false otherwise.
     */
    public boolean isSignatureTriggerCharactersSupported(@NotNull PsiFile file, String charTyped) {
        return getSignatureHelpCapabilityRegistry().isSignatureTriggerCharactersSupported(file, charTyped);
    }

}
