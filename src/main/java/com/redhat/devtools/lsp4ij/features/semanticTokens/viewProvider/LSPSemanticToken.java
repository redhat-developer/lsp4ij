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
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.features.semanticTokens.LSPSemanticTokenType;
import com.redhat.devtools.lsp4ij.features.semanticTokens.LSPSemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a concrete semantic token in a file.
 */
class LSPSemanticToken {

    // All semantic token types
    private static final Set<String> ALL_TOKEN_TYPES = ContainerUtil.union(
            Set.of(
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
                    SemanticTokenTypes.Modifier,
                    SemanticTokenTypes.Comment,
                    SemanticTokenTypes.String,
                    SemanticTokenTypes.Number,
                    SemanticTokenTypes.Regexp,
                    SemanticTokenTypes.Operator,
                    SemanticTokenTypes.Decorator
            ),
            Arrays.stream(LSPSemanticTokenTypes.values())
                    .map(LSPSemanticTokenType::getName)
                    .collect(Collectors.toSet())
    );

    // Semantic token types that should be interpreted as representing identifiers
    private static final Set<String> IDENTIFIER_TOKEN_TYPES = ContainerUtil.union(
            Set.of(
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
                    SemanticTokenTypes.Decorator
            ),
            Arrays.stream(LSPSemanticTokenTypes.values())
                    .filter(LSPSemanticTokenType::isIdentifier)
                    .map(LSPSemanticTokenType::getName)
                    .collect(Collectors.toSet())
    );

    // Semantic token types that should NOT be interpreted as representing identifiers
    private static final Set<String> NON_IDENTIFIER_TOKEN_TYPES = new LinkedHashSet<>(ContainerUtil.subtract(ALL_TOKEN_TYPES, IDENTIFIER_TOKEN_TYPES));

    // Semantic token types that should be interpreted as representing types
    private static final Set<String> TYPE_TOKEN_TYPES = ContainerUtil.union(
            Set.of(
                    SemanticTokenTypes.Namespace,
                    SemanticTokenTypes.Type,
                    SemanticTokenTypes.Class,
                    SemanticTokenTypes.Enum,
                    SemanticTokenTypes.Interface,
                    SemanticTokenTypes.Struct
            ),
            Arrays.stream(LSPSemanticTokenTypes.values())
                    .filter(LSPSemanticTokenType::isType)
                    .map(LSPSemanticTokenType::getName)
                    .collect(Collectors.toSet())
    );

    // Semantic token types that should NOT be interpreted as representing types
    private static final Set<String> NON_TYPE_TOKEN_TYPES = new LinkedHashSet<>(ContainerUtil.subtract(ALL_TOKEN_TYPES, TYPE_TOKEN_TYPES));

    // Semantic token types that should be interpreted as representing keywords/reserved words in the language
    private static final Set<String> KEYWORD_TOKEN_TYPES = ContainerUtil.union(
            Set.of(
                    SemanticTokenTypes.Keyword,
                    SemanticTokenTypes.Modifier
            ),
            Arrays.stream(LSPSemanticTokenTypes.values())
                    .filter(LSPSemanticTokenType::isKeyword)
                    .map(LSPSemanticTokenType::getName)
                    .collect(Collectors.toSet())
    );

    // Semantic token types that should be interpreted as representing declarations
    private static final Set<String> DECLARATION_TOKEN_TYPES = Arrays.stream(LSPSemanticTokenTypes.values())
            .filter(LSPSemanticTokenType::isDeclaration)
            .map(LSPSemanticTokenType::getName)
            .collect(Collectors.toSet());

    // Semantic token modifiers that should be interpreted as representing declarations
    private static final Set<String> DECLARATION_TOKEN_MODIFIERS = Set.of(
            SemanticTokenModifiers.Declaration,
            SemanticTokenModifiers.Definition
    );

    private final PsiFile file;
    private final TextRange textRange;
    private final String tokenType;
    private final List<String> tokenModifiers;

    private final boolean isFileLevel;
    private final LSPSemanticTokenElementType elementType;
    private volatile LSPSemanticTokenPsiElement element = null;
    private final ThreadLocal<Integer> lastRequestedOffsetPtr = new InheritableThreadLocal<>();

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
        this.isFileLevel = Objects.equals(file.getTextRange(), textRange);
        this.elementType = getElementType(this.tokenType, this.tokenModifiers);
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
    ThreeState isIdentifier() {
        if (tokenType != null) {
            if (IDENTIFIER_TOKEN_TYPES.contains(tokenType)) {
                return ThreeState.YES;
            } else if (NON_IDENTIFIER_TOKEN_TYPES.contains(tokenType)) {
                return ThreeState.NO;
            }
        }
        return ThreeState.UNSURE;
    }

    @NotNull
    ThreeState isType() {
        if (tokenType != null) {
            if (TYPE_TOKEN_TYPES.contains(tokenType)) {
                return ThreeState.YES;
            } else if (NON_TYPE_TOKEN_TYPES.contains(tokenType)) {
                return ThreeState.NO;
            }
        }
        return ThreeState.UNSURE;
    }

    @NotNull
    List<String> getTokenModifiers() {
        return tokenModifiers;
    }

    boolean isFileLevel() {
        return isFileLevel;
    }

    @NotNull
    LSPSemanticTokenElementType getElementType() {
        return elementType;
    }

    @NotNull
    private static LSPSemanticTokenElementType getElementType(@Nullable String tokenType,
                                                              @NotNull List<String> tokenModifiers) {
        if (tokenType != null) {
            // If this is an identifier token, see if it's a declaration or a reference
            if (IDENTIFIER_TOKEN_TYPES.contains(tokenType)) {
                return DECLARATION_TOKEN_TYPES.contains(tokenType) || ContainerUtil.intersects(DECLARATION_TOKEN_MODIFIERS, tokenModifiers) ?
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
        // Create the element lazily so that if it's never needed, it's never created
        if (element == null) {
            synchronized (this) {
                if (element == null) {
                    element = new LSPSemanticTokenPsiElement(this);
                }
            }
        }
        return element;
    }

    /**
     * Stores the last requested offset for which a file-level semantic token was returned as a thread local.
     *
     * @param offset the last requested offset
     */
    void setLastRequestedOffset(int offset) {
        if (isFileLevel) {
            lastRequestedOffsetPtr.set(offset);
        }
    }

    /**
     * Returns the last requested offset for which a file-level semantic token was returned from a thread local.
     *
     * @return the last requested offset or -1 if none has been stored
     */
    int getLastRequestedOffset() {
        Integer lastRequestedOffset = isFileLevel ? lastRequestedOffsetPtr.get() : null;
        return lastRequestedOffset != null ? lastRequestedOffset : -1;
    }

    @Override
    public boolean equals(Object o) {
        if ((o == null) || (getClass() != o.getClass())) return false;
        LSPSemanticToken that = (LSPSemanticToken) o;
        return Objects.equals(file, that.file) &&
                Objects.equals(textRange, that.textRange) &&
                Objects.equals(tokenType, that.tokenType) &&
                Objects.equals(tokenModifiers, that.tokenModifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                file,
                textRange,
                tokenType,
                tokenModifiers
        );
    }
}
