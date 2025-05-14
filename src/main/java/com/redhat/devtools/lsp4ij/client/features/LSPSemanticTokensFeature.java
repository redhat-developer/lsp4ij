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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider;
import com.redhat.devtools.lsp4ij.internal.SimpleLanguageUtils;
import com.redhat.devtools.lsp4ij.server.capabilities.SemanticTokensCapabilityRegistry;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * LSP semanticToken feature.
 */
@ApiStatus.Experimental
public class LSPSemanticTokensFeature extends AbstractLSPDocumentFeature implements SemanticTokensColorsProvider {

    private SemanticTokensCapabilityRegistry semanticTokensCapabilityRegistry;
    private @Nullable ServerCapabilities serverCapabilities;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isSemanticTokensSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support semanticTokenss and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support semanticToken and false otherwise.
     */
    public boolean isSemanticTokensSupported(@NotNull PsiFile file) {
        return getSemanticTokensCapabilityRegistry().isSemanticTokensSupported(file);
    }

    public SemanticTokensCapabilityRegistry getSemanticTokensCapabilityRegistry() {
        if (semanticTokensCapabilityRegistry == null) {
            initSemanticTokensCapabilityRegistry();
        }
        return semanticTokensCapabilityRegistry;
    }

    private synchronized void initSemanticTokensCapabilityRegistry() {
        if (semanticTokensCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        semanticTokensCapabilityRegistry = new SemanticTokensCapabilityRegistry(clientFeatures);
        semanticTokensCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
        if (semanticTokensCapabilityRegistry != null) {
            semanticTokensCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

    /**
     * Returns the {@link TextAttributesKey} to use for colorization for the given token type and modifiers.
     *
     * @param tokenType      the semantic token type.
     * @param tokenModifiers the associated token modifiers.
     * @param file           the file in which the token appears.
     * @return the corresponding text attributes key, or {@code null} if none is applicable.
     */
    @Override
    public @Nullable TextAttributesKey getTextAttributesKey(@NotNull String tokenType,
                                                            @NotNull List<String> tokenModifiers,
                                                            @NotNull PsiFile file) {
        return getClientFeatures()
                .getServerWrapper()
                .getServerDefinition()
                .getSemanticTokensColorsProvider()
                .getTextAttributesKey(tokenType, tokenModifiers, file);
    }

    /**
     * Returns the {@link SemanticTokensLegend} reported by the language server, if available.
     *
     * @return the semantic tokens legend or {@code null} if not available.
     */
    public @Nullable SemanticTokensLegend getLegend() {
        if (serverCapabilities != null &&
                serverCapabilities.getSemanticTokensProvider() != null &&
                serverCapabilities.getSemanticTokensProvider().getLegend() != null) {
            return serverCapabilities.getSemanticTokensProvider().getLegend();
        }
        if (semanticTokensCapabilityRegistry != null) {
            return semanticTokensCapabilityRegistry.getLegend();
        }
        return null;
    }

    /**
     * Determines whether the elements of the given file should be visited during semantic highlighting.
     * <p>
     * This method controls whether the semantic tokens logic should traverse PSI elements
     * using the IntelliJ {@link com.intellij.codeInsight.daemon.impl.HighlightVisitor#visit(PsiElement)} API.
     * <p>
     * * The default implementation returns {@code true} only for files whose language does not provide
     * * built-in PSI-based syntax highlighting, such as those handled by TextMate or PlainText support.
     * * For such files, semantic tokens from the language server can provide the primary source of highlighting.
     * * <p>
     * Subclasses may override this method to enable or disable traversal for specific file types.
     *
     * @param file the PSI file.
     * @return {@code true} if elements should be visited via {@link com.intellij.codeInsight.daemon.impl.HighlightVisitor#visit(PsiElement)}, {@code false} otherwise.
     */
    public boolean shouldVisitPsiElement(@NotNull PsiFile file) {
        return !SimpleLanguageUtils.isSupported(file.getLanguage());
    }

    /**
     * Determines whether the given PSI element should be highlighted using semantic tokens.
     * <p>
     * This method is invoked during the semantic highlighting phase, specifically from within
     * the {@link com.intellij.codeInsight.daemon.impl.HighlightVisitor#visit(PsiElement)} method.
     * It controls whether a particular PSI element should be processed for semantic token highlighting.
     * <p>
     * The default implementation returns {@code true} only for {@link LeafElement} instances,
     * which typically represent fine-grained syntax elements such as keywords, identifiers, or symbols.
     * This selective behavior avoids unnecessary processing of higher-level PSI structures
     * that are not directly associated with semantic tokens.
     * <p>
     * Subclasses may override this method to customize the filtering logic and support additional
     * element types as needed.
     *
     * @param element the PSI element to check.
     * @return {@code true} if the element should be highlighted, {@code false} otherwise.
     */
    public boolean isEligibleForSemanticHighlighting(@NotNull PsiElement element) {
        return element instanceof LeafElement;
    }
}
