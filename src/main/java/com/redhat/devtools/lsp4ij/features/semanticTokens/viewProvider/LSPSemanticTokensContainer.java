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
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Common interface for semantic tokens containers.
 */
interface LSPSemanticTokensContainer {
    /**
     * Whether or not the file view provider is enabled for semantic tokens.
     *
     * @return true if the file view provider is enabled for semantic tokens; otherwise false
     */
    boolean isEnabled();

    /**
     * Whether or not the semantic token at the offset is for a keyword.
     *
     * @param offset the offset
     * @return true if the semantic token at the offset is for a keyword; otherwise false
     */
    boolean isKeyword(int offset);

    /**
     * Whether or not the semantic token at the offset is for a operator.
     *
     * @param offset the offset
     * @return true if the semantic token at the offset is for a operator; otherwise false
     */
    boolean isOperator(int offset);

    /**
     * Whether or not the semantic token at the offset is for a string literal.
     *
     * @param offset the offset
     * @return true if the semantic token at the offset is for a string literal; otherwise false
     */
    boolean isStringLiteral(int offset);

    /**
     * Whether or not the semantic token at the offset is for a numeric literal.
     *
     * @param offset the offset
     * @return true if the semantic token at the offset is for a numeric literal; otherwise false
     */
    boolean isNumericLiteral(int offset);

    /**
     * Whether or not the semantic token at the offset is for a regular expression.
     *
     * @param offset the offset
     * @return true if the semantic token at the offset is for a regular expression; otherwise false
     */
    boolean isRegularExpression(int offset);

    /**
     * Whether or not the semantic token at the offset is for a comment.
     *
     * @param offset the offset
     * @return true if the semantic token at the offset is for a comment; otherwise false
     */
    boolean isComment(int offset);

    /**
     * Whether or not the semantic token at the offset is for a declaration or definition.
     *
     * @param offset the offset
     * @return true if the semantic token at the offset is for a declaration or definition; otherwise false
     */
    boolean isDeclaration(int offset);

    /**
     * Whether or not the semantic token at the offset is for a reference.
     *
     * @param offset the offset
     * @return true if the semantic token at the offset is for a reference; otherwise false
     */
    boolean isReference(int offset);

    /**
     * Whether or not the semantic token at the offset is of an unknown type.
     *
     * @param offset the offset
     * @return true if the semantic token at the offset is of an unknown type; otherwise false
     */
    boolean isUnknown(int offset);

    /**
     * Whether or not the semantic token at the offset is for an identifier.
     *
     * @param offset the offset
     * @return {@link ThreeState#YES} if the semantic token at the offset is conclusively for an identifier;
     * {@link ThreeState#NO} if the semantic token at the offset is conclusively <b>not</b> for an identifier;
     * otherwise {@link ThreeState#UNSURE}
     */
    @NotNull
    ThreeState isIdentifier(int offset);

    /**
     * Whether or not the semantic token at the offset is for a type declaration, definition, or reference.
     *
     * @param offset the offset
     * @return {@link ThreeState#YES} if the semantic token at the offset is conclusively for a type;
     * {@link ThreeState#NO} if the semantic token at the offset is conclusively <b>not</b> for a type;
     * otherwise {@link ThreeState#UNSURE}
     */
    @NotNull
    ThreeState isType(int offset);

    /**
     * Whether or not the offset is for whitespace.
     *
     * @param offset the offset
     * @return true if the offset is for whitespace; otherwise false
     */
    boolean isWhitespace(int offset);

    /**
     * Returns the text range of the semantic token at the offset.
     *
     * @param offset the offset
     * @return the text range of the semantic token at the offset, or null if there is no semantic token at the offset
     */
    @Nullable
    TextRange getSemanticTokenTextRange(int offset);

    /**
     * Adds a semantic token to the file view provider.
     *
     * @param textRange      the semantic token's text range
     * @param tokenType      the optional semantic token type, generally as one of {@link org.eclipse.lsp4j.SemanticTokenTypes},
     *                       but a custom type can be specified
     * @param tokenModifiers the optional semantic token modifiers, generally as a collection of
     *                       {@link org.eclipse.lsp4j.SemanticTokenModifiers}, but custom modifiers can be specified
     */
    void addSemanticToken(@NotNull TextRange textRange,
                          @Nullable String tokenType,
                          @Nullable List<String> tokenModifiers);

    /**
     * Returns the effective offset for the provided element.
     *
     * @param element the element
     * @return the element's effective offset or -1 if none is available
     */
    int getEffectiveOffset(@NotNull PsiElement element);
}
