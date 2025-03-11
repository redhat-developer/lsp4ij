/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Default semantic tokens colors provider.
 */
public class DefaultSemanticTokensColorsProvider implements SemanticTokensColorsProvider {


    @Override
    public @Nullable TextAttributesKey getTextAttributesKey(@NotNull String tokenType,
                                                            @NotNull List<String> tokenModifiers,
                                                            @NotNull PsiFile file) {
        LSPSemanticTokenType lspSemanticTokenType = LSPSemanticTokenTypes.valueOf(tokenType);
        if (lspSemanticTokenType != null) {
            // If this is for a custom semantic token type that expresses an inheritance relationship, swap it out now
            if (lspSemanticTokenType.getInheritFrom() != null) {
                tokenType = lspSemanticTokenType.getInheritFrom();
            }
            // If it's for one that has a specific text attributes key, use it
            else if (lspSemanticTokenType.getTextAttributesKey() != null) {
                return TextAttributesKey.find(lspSemanticTokenType.getTextAttributesKey());
            }
        }

        switch (tokenType) {

            // namespace: for identifiers that declare or reference a namespace, module, or package.
            case SemanticTokenTypes.Namespace:
                if (hasTokenModifiers(tokenModifiers,
                        SemanticTokenModifiers.Declaration,
                        SemanticTokenModifiers.Definition)) {
                    // with declaration, definition modifiers
                    return SemanticTokensHighlightingColors.NAMESPACE_DECLARATION;
                }
                // with other modifiers
                return SemanticTokensHighlightingColors.NAMESPACE;

            // class: for identifiers that declare or reference a class type.
            case SemanticTokenTypes.Class:
                if (hasTokenModifiers(tokenModifiers,
                        SemanticTokenModifiers.Declaration,
                        SemanticTokenModifiers.Definition)) {
                    // with declaration, definition modifiers
                    return SemanticTokensHighlightingColors.CLASS_DECLARATION;
                }
                // with other modifiers
                return SemanticTokensHighlightingColors.CLASS;

            // enum: for identifiers that declare or reference an enumeration type.
            case SemanticTokenTypes.Enum:
                return SemanticTokensHighlightingColors.ENUM;

            // interface: for identifiers that declare or reference an interface type.
            case SemanticTokenTypes.Interface:
                return SemanticTokensHighlightingColors.INTERFACE;

            // struct: for identifiers that declare or reference a struct type.
            case SemanticTokenTypes.Struct:
                return SemanticTokensHighlightingColors.STRUCT;

            // typeParameter: for identifiers that declare or reference a type parameter.
            case SemanticTokenTypes.TypeParameter:
                return SemanticTokensHighlightingColors.TYPE_PARAMETER;

            // type: for identifiers that declare or reference a type that is not covered above.
            case SemanticTokenTypes.Type:
                return SemanticTokensHighlightingColors.TYPE;

            // parameter: for identifiers that declare or reference a function or method parameters.
            case SemanticTokenTypes.Parameter:
                return SemanticTokensHighlightingColors.PARAMETER;

            // variable: for identifiers that declare or reference a local or global variable.
            case SemanticTokenTypes.Variable:
                if (hasTokenModifiers(tokenModifiers, SemanticTokenModifiers.Static)) {
                    if (hasTokenModifiers(tokenModifiers, SemanticTokenModifiers.Readonly)) {
                        // with static, readonly modifiers
                        return SemanticTokensHighlightingColors.STATIC_READONLY_VARIABLE;
                    }
                    // with static readonly modifiers
                    return SemanticTokensHighlightingColors.STATIC_VARIABLE;
                }
                if (hasTokenModifiers(tokenModifiers, SemanticTokenModifiers.Readonly)) {
                    // with readonly modifiers
                    return SemanticTokensHighlightingColors.READONLY_VARIABLE;
                }
                // with other modifiers
                return SemanticTokensHighlightingColors.VARIABLE;

            // property: for identifiers that declare or reference a member property, member field, or member variable.
            case SemanticTokenTypes.Property:
                if (hasTokenModifiers(tokenModifiers, SemanticTokenModifiers.Static)) {
                    if (hasTokenModifiers(tokenModifiers, SemanticTokenModifiers.Readonly)) {
                        // with static, readonly modifiers
                        return SemanticTokensHighlightingColors.STATIC_READONLY_PROPERTY;
                    }
                    // with static readonly modifiers
                    return SemanticTokensHighlightingColors.STATIC_PROPERTY;
                }
                if (hasTokenModifiers(tokenModifiers, SemanticTokenModifiers.Readonly)) {
                    // with readonly modifiers
                    return SemanticTokensHighlightingColors.READONLY_PROPERTY;
                }
                // with other modifiers
                return SemanticTokensHighlightingColors.PROPERTY;

            // enum member: for identifiers that declare or reference an enumeration property, constant, or member.
            case SemanticTokenTypes.EnumMember:
                return SemanticTokensHighlightingColors.ENUM_MEMBER;

            // decorator: for identifiers that declare or reference decorators and annotations.
            case SemanticTokenTypes.Decorator:
                return SemanticTokensHighlightingColors.DECORATOR;

            // event: for identifiers that declare an event property.
            case SemanticTokenTypes.Event:
                return SemanticTokensHighlightingColors.EVENT;

            // function: for identifiers that declare a function.
            case SemanticTokenTypes.Function:
                if (hasTokenModifiers(tokenModifiers,
                        SemanticTokenModifiers.DefaultLibrary)) {
                    // with defaultLibrary modifiers
                    return SemanticTokensHighlightingColors.DEFAULT_LIBRARY_FUNCTION;
                }
                if (hasTokenModifiers(tokenModifiers,
                        SemanticTokenModifiers.Declaration,
                        SemanticTokenModifiers.Definition)) {
                    // with declaration, definition modifiers
                    return SemanticTokensHighlightingColors.FUNCTION_DECLARATION;
                }
                // with other modifiers
                return SemanticTokensHighlightingColors.FUNCTION;

            // method: for identifiers that declare a member function or method.
            case SemanticTokenTypes.Method: {
                if (hasTokenModifiers(tokenModifiers,
                        SemanticTokenModifiers.Declaration,
                        SemanticTokenModifiers.Definition)) {
                    // with declaration, definition modifiers
                    return SemanticTokensHighlightingColors.METHOD_DECLARATION;
                }
                if (hasTokenModifiers(tokenModifiers,
                        SemanticTokenModifiers.Static)) {
                    // with static modifiers
                    return SemanticTokensHighlightingColors.STATIC_METHOD;
                }
                // with other modifiers
                return SemanticTokensHighlightingColors.METHOD;
            }

            // macro: for identifiers that declare a macro.
            case SemanticTokenTypes.Macro:
                return SemanticTokensHighlightingColors.MACRO;

            // comment: for tokens that represent a comment.
            case SemanticTokenTypes.Comment:
                return SemanticTokensHighlightingColors.COMMENT;

            // string: for tokens that represent a string literal.
            case SemanticTokenTypes.String:
                return SemanticTokensHighlightingColors.STRING;

            // keyword: for tokens that represent a language keyword.
            case SemanticTokenTypes.Keyword:
                return SemanticTokensHighlightingColors.KEYWORD;

            // number: for tokens that represent a number literal.
            case SemanticTokenTypes.Number:
                return SemanticTokensHighlightingColors.NUMBER;

            // regexp: for tokens that represent a regular expression literal.
            case SemanticTokenTypes.Regexp:
                return SemanticTokensHighlightingColors.REGEXP;

            // modifier
            case SemanticTokenTypes.Modifier:
                return SemanticTokensHighlightingColors.MODIFIER;

            // operator
            case SemanticTokenTypes.Operator:
                return SemanticTokensHighlightingColors.OPERATOR;

        }
        return null;
    }

    protected boolean hasTokenModifiers(List<String> tokenModifiers, String... checkedTokenModifiers) {
        if (tokenModifiers.isEmpty()) {
            return false;
        }
        for (var modifier : checkedTokenModifiers) {
            if (tokenModifiers.contains(modifier)) {
                return true;
            }
        }
        return false;
    }

}
