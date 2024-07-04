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

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * Semantic tokens highlighting color constants.
 */
public class SemanticTokensHighlightingColors {

    // namespace
    public static final TextAttributesKey NAMESPACE_DECLARATION = TextAttributesKey.createTextAttributesKey("LSP_NAMESPACE_DECLARATION", DefaultLanguageHighlighterColors.CLASS_NAME);
    public static final TextAttributesKey NAMESPACE = TextAttributesKey.createTextAttributesKey("LSP_NAMESPACE", DefaultLanguageHighlighterColors.CLASS_REFERENCE);

    // class
    public static final TextAttributesKey CLASS_DECLARATION = TextAttributesKey.createTextAttributesKey("LSP_CLASS_DECLARATION", DefaultLanguageHighlighterColors.CLASS_NAME);
    public static final TextAttributesKey CLASS = TextAttributesKey.createTextAttributesKey("LSP_CLASS", DefaultLanguageHighlighterColors.CLASS_REFERENCE);

    // enum
    public static final TextAttributesKey ENUM = TextAttributesKey.createTextAttributesKey("LSP_ENUM", DefaultLanguageHighlighterColors.CLASS_NAME);

    // interface
    public static final TextAttributesKey INTERFACE = TextAttributesKey.createTextAttributesKey("LSP_INTERFACE", DefaultLanguageHighlighterColors.INTERFACE_NAME);

    // struct
    public static final TextAttributesKey STRUCT = TextAttributesKey.createTextAttributesKey("LSP_STRUCT", DefaultLanguageHighlighterColors.CLASS_NAME);

    // typeParameter
    public static final TextAttributesKey TYPE_PARAMETER = TextAttributesKey.createTextAttributesKey("LSP_TYPE_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);

    // type
    public static final TextAttributesKey TYPE = TextAttributesKey.createTextAttributesKey("LSP_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME);

    // parameter
    public static final TextAttributesKey PARAMETER = TextAttributesKey.createTextAttributesKey("LSP_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);

    // variable
    public static final TextAttributesKey STATIC_VARIABLE = TextAttributesKey.createTextAttributesKey("LSP_STATIC_VARIABLE", DefaultLanguageHighlighterColors.STATIC_FIELD);
    public static final TextAttributesKey STATIC_READONLY_VARIABLE = TextAttributesKey.createTextAttributesKey("LSP_STATIC_READONLY_VARIABLE", DefaultLanguageHighlighterColors.CONSTANT);
    public static final TextAttributesKey READONLY_VARIABLE = TextAttributesKey.createTextAttributesKey("LSP_READONLY_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    public static final TextAttributesKey VARIABLE = TextAttributesKey.createTextAttributesKey("LSP_VARIABLE", DefaultLanguageHighlighterColors.REASSIGNED_LOCAL_VARIABLE);

    // property
    public static final TextAttributesKey STATIC_PROPERTY = TextAttributesKey.createTextAttributesKey("LSP_STATIC_PROPERTY", DefaultLanguageHighlighterColors.STATIC_FIELD);
    public static final TextAttributesKey STATIC_READONLY_PROPERTY = TextAttributesKey.createTextAttributesKey("LSP_STATIC_READONLY_PROPERTY", DefaultLanguageHighlighterColors.CONSTANT);
    public static final TextAttributesKey PROPERTY = TextAttributesKey.createTextAttributesKey("LSP_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey READONLY_PROPERTY = TextAttributesKey.createTextAttributesKey("LSP_READONLY_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);

    // enumMember
    public static final TextAttributesKey ENUM_MEMBER = TextAttributesKey.createTextAttributesKey("LSP_ENUM_MEMBER", DefaultLanguageHighlighterColors.STATIC_FIELD);

    // decorator
    public static final TextAttributesKey DECORATOR = TextAttributesKey.createTextAttributesKey("LSP_DECORATOR", DefaultLanguageHighlighterColors.METADATA);

    // event
    public static final TextAttributesKey EVENT = TextAttributesKey.createTextAttributesKey("LSP_EVENT", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL);

    // function
    public static final TextAttributesKey FUNCTION = TextAttributesKey.createTextAttributesKey("LSP_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    public static final TextAttributesKey FUNCTION_DECLARATION = TextAttributesKey.createTextAttributesKey("LSP_FUNCTION_DECLARATION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey DEFAULT_LIBRARY_FUNCTION = TextAttributesKey.createTextAttributesKey("LSP_DEFAULT_LIBRARY_FUNCTION", DefaultLanguageHighlighterColors.STATIC_METHOD);

    // method
    public static final TextAttributesKey METHOD = TextAttributesKey.createTextAttributesKey("LSP_METHOD", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    public static final TextAttributesKey METHOD_DECLARATION = TextAttributesKey.createTextAttributesKey("LSP_METHOD_DECLARATION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey STATIC_METHOD = TextAttributesKey.createTextAttributesKey("LSP_STATIC_METHOD", DefaultLanguageHighlighterColors.STATIC_METHOD);

    // macro
    public static final TextAttributesKey MACRO = TextAttributesKey.createTextAttributesKey("LSP_MACRO", DefaultLanguageHighlighterColors.KEYWORD);

    // label
    public static final TextAttributesKey LABEL = TextAttributesKey.createTextAttributesKey("LSP_LABEL", DefaultLanguageHighlighterColors.LABEL);

    // comment
    public static final TextAttributesKey COMMENT = TextAttributesKey.createTextAttributesKey("LSP_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

    // string
    public static final TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey("LSP_STRING", DefaultLanguageHighlighterColors.STRING);

    // keyword
    public static final TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey("LSP_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

    // number
    public static final TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey("LSP_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

    // regexp
    public static final TextAttributesKey REGEXP = TextAttributesKey.createTextAttributesKey("LSP_REGEXP", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);

    // modifier
    public static final TextAttributesKey MODIFIER = TextAttributesKey.createTextAttributesKey("LSP_MODIFIER", DefaultLanguageHighlighterColors.KEYWORD);

    // operator
    public static final TextAttributesKey OPERATOR = TextAttributesKey.createTextAttributesKey("LSP_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);

}
