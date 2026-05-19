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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokenPsiElement;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP usage feature.
 * <p>
 * This feature determines whether the "Find Usages" functionality is available for a given file or element.
 * The Find Usages feature allows users to locate all references, declarations, definitions, implementations,
 * and type definitions of a symbol in the codebase.
 * </p>
 * <p>
 * This feature can be overridden by language server implementations to customize which files and elements
 * support usage searching based on their specific LSP capabilities.
 * </p>
 */
@ApiStatus.Experimental
public class LSPUsageFeature extends AbstractLSPDocumentFeature {

    /**
     * Checks if the file supports the usage feature.
     *
     * @param file the file to check.
     * @return true if usage is supported for this file, false otherwise.
     */
    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isUsageSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support usage and false otherwise.
     * <p>
     * A file supports usage if at least one of the following LSP features is supported:
     * <ul>
     *   <li>textDocument/declaration</li>
     *   <li>textDocument/typeDefinition</li>
     *   <li>textDocument/definition</li>
     *   <li>textDocument/references</li>
     *   <li>textDocument/implementation</li>
     * </ul>
     * </p>
     *
     * @param file the file to check.
     * @return true if the file associated with a language server can support usage and false otherwise.
     */
    public boolean isUsageSupported(@NotNull PsiFile file) {
        var clientFeature = getClientFeatures();
        return clientFeature.getDeclarationFeature().isSupported(file) ||
                clientFeature.getTypeDefinitionFeature().isSupported(file) ||
                clientFeature.getDefinitionFeature().isSupported(file) ||
                clientFeature.getReferencesFeature().isSupported(file) ||
                clientFeature.getImplementationFeature().isSupported(file);
    }

    /**
     * Returns true if the given PSI element supports usage searching and false otherwise.
     * <p>
     * For semantic token elements, this method checks if the token type is appropriate for usage searching.
     * Non-semantic token elements are considered supported by default.
     * </p>
     *
     * @param element the PSI element to check.
     * @return true if the element supports usage, false otherwise.
     */
    public boolean isUsageSupported(@NotNull PsiElement element) {
        if (element instanceof LSPSemanticTokenPsiElement semanticTokenPsiElement) {
            return isUsageSupported(semanticTokenPsiElement);
        }
        return true;
    }

    /**
     * Returns true if the given semantic token element supports usage searching and false otherwise.
     * <p>
     * Certain semantic token types are excluded from usage searching because they do not represent
     * symbols that can be referenced:
     * <ul>
     *   <li>Comments - documentation text, not code symbols</li>
     *   <li>Keywords - language syntax, not user-defined symbols</li>
     *   <li>Modifiers - syntax modifiers (public, static, etc.)</li>
     *   <li>Operators - language operators (+, -, *, etc.)</li>
     * </ul>
     * </p>
     *
     * @param element the semantic token element to check.
     * @return true if the element supports usage, false if it's a comment, keyword, modifier, or operator.
     */
    protected boolean isUsageSupported(@NotNull LSPSemanticTokenPsiElement element) {
        String tokenType = element.getType();
        return !(SemanticTokenTypes.Comment.equals(tokenType)
                || SemanticTokenTypes.Keyword.equals(tokenType)
                || SemanticTokenTypes.Modifier.equals(tokenType)
                || SemanticTokenTypes.Operator.equals(tokenType));
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        // Do nothing
    }

}
