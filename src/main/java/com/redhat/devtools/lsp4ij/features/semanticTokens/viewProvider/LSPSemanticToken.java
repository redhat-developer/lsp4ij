/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Represents a concrete semantic token in a file.
 */
final class LSPSemanticToken {
    // Semantic token types that should be interpreted as representing identifier names
    private static final Set<String> IDENTIFIER_NAME_TOKEN_TYPES = Set.of(
            SemanticTokenTypes.Namespace,
            SemanticTokenTypes.Type,
            SemanticTokenTypes.Class,
            SemanticTokenTypes.Enum,
            SemanticTokenTypes.Interface,
            SemanticTokenTypes.Struct,
            SemanticTokenTypes.TypeParameter,
            SemanticTokenTypes.Parameter,
            SemanticTokenTypes.Variable,
            SemanticTokenTypes.Property,
            SemanticTokenTypes.EnumMember,
            SemanticTokenTypes.Event,
            SemanticTokenTypes.Function,
            SemanticTokenTypes.Method,
            SemanticTokenTypes.Macro,
            SemanticTokenTypes.Decorator,
            // TODO: Need a mechanism for language server definitions to register token types and token modifiers
            //  including how they should be interpreted for syntax highlighting and categorization
            "member" // JavaScript/TypeScript-specific
    );

    // Semantic token modifiers that should be interpreted as representing identifier declarations
    private static final Set<String> IDENTIFIER_DECLARATION_TOKEN_MODIFIERS = Set.of(
            SemanticTokenModifiers.Declaration,
            SemanticTokenModifiers.Definition
    );

    // Semantic token types that should be interpreted as representing keywords/reserved words in the language
    private static final Set<String> KEYWORD_TOKEN_TYPES = Set.of(
            SemanticTokenTypes.Keyword,
            SemanticTokenTypes.Modifier
    );

    private final PsiFile file;
    private final TextRange textRange;
    private final String tokenType;
    private final List<String> tokenModifiers;

    private final LSPSemanticTokenElementType elementType;
    private final LSPSemanticTokenPsiElement element;

    /**
     * Creates a new semantic token.
     *
     * @param file           the file
     * @param textRange      the semantic token's text range
     * @param tokenType      the token type
     * @param tokenModifiers the token modifiers
     */
    LSPSemanticToken(
            @NotNull PsiFile file,
            @NotNull TextRange textRange,
            @Nullable String tokenType,
            @Nullable List<String> tokenModifiers
    ) {
        this.file = file;
        this.textRange = textRange;
        this.tokenType = tokenType;
        this.tokenModifiers = tokenModifiers != null ? tokenModifiers : Collections.emptyList();

        this.elementType = getElementType(this.tokenType, this.tokenModifiers);
        this.element = new LSPSemanticTokenPsiElement(this);
    }

    @NotNull
    PsiFile getFile() {
        return file;
    }

    @NotNull
    TextRange getTextRange() {
        return textRange;
    }

    @Nullable
    String getTokenType() {
        return tokenType;
    }

    @NotNull
    List<String> getTokenModifiers() {
        return tokenModifiers;
    }

    @NotNull
    LSPSemanticTokenElementType getElementType() {
        return elementType;
    }

    @NotNull
    private static LSPSemanticTokenElementType getElementType(@Nullable String tokenType,
                                                              @NotNull List<String> tokenModifiers) {
        if (tokenType != null) {
            // If this is an identifier name token, see if it's a declaration or a reference
            if (IDENTIFIER_NAME_TOKEN_TYPES.contains(tokenType)) {
                return ContainerUtil.intersects(IDENTIFIER_DECLARATION_TOKEN_MODIFIERS, tokenModifiers) ?
                        LSPSemanticTokenElementType.DECLARATION :
                        LSPSemanticTokenElementType.REFERENCE;
            }
            // Keyword/reserved word
            else if (KEYWORD_TOKEN_TYPES.contains(tokenType)) {
                return LSPSemanticTokenElementType.KEYWORD;
            }
            // Other, e.g., string/numeric literal, comment, operator, etc.
            else {
                return switch (tokenType) {
                    case SemanticTokenTypes.Comment -> LSPSemanticTokenElementType.COMMENT;
                    case SemanticTokenTypes.String -> LSPSemanticTokenElementType.STRING;
                    case SemanticTokenTypes.Number -> LSPSemanticTokenElementType.NUMBER;
                    case SemanticTokenTypes.Regexp -> LSPSemanticTokenElementType.REGEXP;
                    case SemanticTokenTypes.Operator -> LSPSemanticTokenElementType.OPERATOR;
                    default -> LSPSemanticTokenElementType.UNKNOWN;
                };
            }
        }

        // Fallback is unknown
        return LSPSemanticTokenElementType.UNKNOWN;
    }

    @NotNull
    LSPSemanticTokenPsiElement getElement() {
        return element;
    }
}
