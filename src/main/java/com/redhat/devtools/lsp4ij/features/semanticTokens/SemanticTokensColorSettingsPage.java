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

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * LSP Semantic tokens color setting page.
 */
public class SemanticTokensColorSettingsPage implements ColorSettingsPage, DisplayPrioritySortable {

    private static final AttributesDescriptor[] ourDescriptors = {
            // namespace
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.namespace.declaration"), SemanticTokensHighlightingColors.NAMESPACE_DECLARATION),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.namespace"), SemanticTokensHighlightingColors.NAMESPACE),

            // class
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.class.declaration"), SemanticTokensHighlightingColors.CLASS_DECLARATION),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.class.reference"), SemanticTokensHighlightingColors.CLASS),

            // enum
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.enum"), SemanticTokensHighlightingColors.ENUM),

            // interface
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.interface"), SemanticTokensHighlightingColors.INTERFACE),

            // struct
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.struct"), SemanticTokensHighlightingColors.STRUCT),

            // typeParameter
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.typeParameter"), SemanticTokensHighlightingColors.TYPE_PARAMETER),

            // type
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.type"), SemanticTokensHighlightingColors.TYPE),

            // parameter
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.parameter"), SemanticTokensHighlightingColors.PARAMETER),

            // variable
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.variable.static"), SemanticTokensHighlightingColors.STATIC_VARIABLE),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.variable.readonly.static"), SemanticTokensHighlightingColors.STATIC_READONLY_VARIABLE),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.variable.readonly"), SemanticTokensHighlightingColors.READONLY_VARIABLE),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.variable"), SemanticTokensHighlightingColors.VARIABLE),

            // property
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.property.static"), SemanticTokensHighlightingColors.STATIC_PROPERTY),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.property.static.readonly"), SemanticTokensHighlightingColors.STATIC_READONLY_PROPERTY),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.property"), SemanticTokensHighlightingColors.READONLY_PROPERTY),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.property.readonly"), SemanticTokensHighlightingColors.READONLY_PROPERTY),

            // enumMember
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.enum.member"), SemanticTokensHighlightingColors.ENUM_MEMBER),

            // decorator
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.decorator"), SemanticTokensHighlightingColors.DECORATOR),

            // event
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.event"), SemanticTokensHighlightingColors.EVENT),

            // function
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.function.call"), SemanticTokensHighlightingColors.FUNCTION),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.function.declaration"), SemanticTokensHighlightingColors.FUNCTION_DECLARATION),

            // method
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.method.call"), SemanticTokensHighlightingColors.METHOD),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.method.declaration"), SemanticTokensHighlightingColors.METHOD_DECLARATION),
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.method.static"), SemanticTokensHighlightingColors.STATIC_METHOD),

            // macro
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.macro"), SemanticTokensHighlightingColors.MACRO),

            // label
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.label"), SemanticTokensHighlightingColors.LABEL),

            // comment
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.comment"), SemanticTokensHighlightingColors.COMMENT),

            // string
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.string"), SemanticTokensHighlightingColors.STRING),

            // keyword
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.keyword"), SemanticTokensHighlightingColors.KEYWORD),

            // number
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.number"), SemanticTokensHighlightingColors.NUMBER),

            // regexp
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.regexp"), SemanticTokensHighlightingColors.REGEXP),

            // modifier
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.modifier"), SemanticTokensHighlightingColors.MODIFIER),

            // operator
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.operator"), SemanticTokensHighlightingColors.OPERATOR)

    };

    @NonNls private static final Map<String, TextAttributesKey> ourTags = RainbowHighlighter.createRainbowHLM();
    static {
        for (AttributesDescriptor descriptor : ourDescriptors) {
            var attributeKey = descriptor.getKey();
            ourTags.put(attributeKey.getExternalName(), attributeKey);
        }
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return SyntaxHighlighterFactory.getSyntaxHighlighter(PlainTextLanguage.INSTANCE, null, null);
    }

    @Override
    public @NonNls @NotNull String getDemoText() {
        return """
                public class <LSP_CLASS_DECLARATION>SomeClass</LSP_CLASS_DECLARATION><<typeParameter>T</typeParameter> extends <interface>Runnable</interface>> { // some comment
                }
                """;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return ourTags;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return ourDescriptors;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull @NlsContexts.ConfigurableName String getDisplayName() {
        return LanguageServerBundle.message("lsp.semantic.tokens.color.settings.name");
    }

    @Override
    public DisplayPriority getPriority() {
        return DisplayPriority.LANGUAGE_SETTINGS;
    }
}
