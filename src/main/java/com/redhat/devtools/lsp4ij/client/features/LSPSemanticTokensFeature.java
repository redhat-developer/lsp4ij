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

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * LSP semanticTokens feature.
 */
@ApiStatus.Experimental
public class LSPSemanticTokensFeature extends AbstractLSPDocumentFeature implements SemanticTokensColorsProvider {

    /**
     * Returns the {@link TextAttributesKey} to use for colorization for the given token type and given token modifiers and null otherwise.
     *
     * @param tokenType      the token type.
     * @param tokenModifiers the token modifiers.
     * @param file           the Psi file.
     * @return the {@link TextAttributesKey} to use for colorization for the given token type and given token modifiers and null otherwise.
     */
    @Override
    public @Nullable TextAttributesKey getTextAttributesKey(@NotNull String tokenType,
                                                            @NotNull List<String> tokenModifiers,
                                                            @NotNull PsiFile file) {
        return getClientFeatures()
                .getServerWrapper()
                .getServerDefinition()
                .getSemanticTokensColorsProvider()
                .getTextAttributesKey(tokenType,tokenModifiers, file);
    }

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isSemanticTokensSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support semanticTokens and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support semanticTokens and false otherwise.
     */
    public boolean isSemanticTokensSupported(@NotNull PsiFile file) {
        var serverCapabilities = getClientFeatures().getServerWrapper().getServerCapabilitiesSync();
        return serverCapabilities != null &&
                serverCapabilities.getSemanticTokensProvider() != null;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        // Do nothing
    }
}
