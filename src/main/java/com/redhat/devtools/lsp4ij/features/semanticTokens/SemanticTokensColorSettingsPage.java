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
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.property"), SemanticTokensHighlightingColors.PROPERTY),
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
            new AttributesDescriptor(LanguageServerBundle.message("options.lsp.attribute.descriptor.function.defaultLibrary"), SemanticTokensHighlightingColors.DEFAULT_LIBRARY_FUNCTION),

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
                <LSP_COMMENT>// Here is a Java sample</LSP_COMMENT>
                package <LSP_NAMESPACE>com</LSP_NAMESPACE>;
                
                <LSP_DECORATOR>@Deprecated</LSP_DECORATOR>(since = <LSP_STRING>"1.0.0"</LSP_STRING>)
                <LSP_MODIFIER>public</LSP_MODIFIER> <LSP_MODIFIER>class</LSP_MODIFIER> <LSP_CLASS_DECLARATION>Bar</LSP_CLASS_DECLARATION><<LSP_TYPE_PARAMETER>T</LSP_TYPE_PARAMETER> extends <LSP_INTERFACE>Runnable</LSP_INTERFACE>> {

                    <LSP_MODIFIER>private</LSP_MODIFIER> <LSP_MODIFIER>static</LSP_MODIFIER> <LSP_MODIFIER>final</LSP_MODIFIER> int <LSP_STATIC_READONLY_PROPERTY>CONSTANT</LSP_STATIC_READONLY_PROPERTY> = <LSP_NUMBER>1234</LSP_NUMBER>;
                    <LSP_MODIFIER>private</LSP_MODIFIER> <LSP_MODIFIER>static</LSP_MODIFIER> int <LSP_STATIC_PROPERTY>GLOBAL</LSP_STATIC_PROPERTY>;
                    <LSP_MODIFIER>private</LSP_MODIFIER> <LSP_MODIFIER>final</LSP_MODIFIER> int <LSP_READONLY_PROPERTY>readOnlyField</LSP_READONLY_PROPERTY> = <LSP_NUMBER>5678</LSP_NUMBER>;
                    <LSP_MODIFIER>private</LSP_MODIFIER> int <LSP_PROPERTY>field</LSP_PROPERTY>;

                    <LSP_MODIFIER>public</LSP_MODIFIER> int <LSP_METHOD_DECLARATION>someMethod</LSP_METHOD_DECLARATION>() {
                        <LSP_CLASS>var</LSP_CLASS> <LSP_VARIABLE>bar</LSP_VARIABLE> = new <LSP_CLASS>Bar</LSP_CLASS><?>();
                        <LSP_STATIC_METHOD>staticMethod</LSP_STATIC_METHOD>();
                        <LSP_METHOD>someMethod</LSP_METHOD>();
                        <LSP_KEYWORD>return</LSP_KEYWORD> <LSP_NUMBER>1</LSP_NUMBER> <LSP_OPERATOR>+</LSP_OPERATOR> <LSP_NUMBER>2</LSP_NUMBER> <LSP_OPERATOR>+</LSP_OPERATOR> <LSP_PROPERTY>field</LSP_PROPERTY>;
                    }

                    <LSP_MODIFIER>public</LSP_MODIFIER> <LSP_MODIFIER>static</LSP_MODIFIER> <LSP_CLASS>String</LSP_CLASS> <LSP_METHOD_DECLARATION>staticMethod</LSP_METHOD_DECLARATION>() {
                        <LSP_KEYWORD>return</LSP_KEYWORD> <LSP_STRING>"foo"</LSP_STRING>;
                    }
                }
                
                <LSP_COMMENT>// Here is a TypeScript sample</LSP_COMMENT>
                <LSP_KEYWORD>function</LSP_KEYWORD> <LSP_FUNCTION_DECLARATION>foo</LSP_FUNCTION_DECLARATION>() {
                    <LSP_DEFAULT_LIBRARY_FUNCTION>print</LSP_DEFAULT_LIBRARY_FUNCTION>(<LSP_STRING>"bar"</LSP_STRING>)
                }
                
                <LSP_COMMENT>// Here is a Go sample</LSP_COMMENT>
                <LSP_KEYWORD>package</LSP_KEYWORD> <LSP_NAMESPACE>src</LSP_NAMESPACE>
                
                <LSP_KEYWORD>import</LSP_KEYWORD> (
                	"<LSP_NAMESPACE>fmt</LSP_NAMESPACE>"
                )
                
                <LSP_KEYWORD>func</LSP_KEYWORD> <LSP_FUNCTION_DECLARATION>foo</LSP_FUNCTION_DECLARATION>() {
                    <LSP_KEYWORD>const</LSP_KEYWORD> <LSP_READONLY_VARIABLE>s</LSP_READONLY_VARIABLE> = <LSP_STRING>""</LSP_STRING>;
                    <LSP_NAMESPACE>fmt</LSP_NAMESPACE>.<LSP_FUNCTION>Printf</LSP_FUNCTION>(<LSP_STRING>"s: %v\\n"</LSP_STRING>, <LSP_READONLY_VARIABLE>s</LSP_READONLY_VARIABLE>)
                }
                
                <LSP_COMMENT>;; Here is a Clojure sample</LSP_COMMENT>

                (<LSP_MACRO>defn</LSP_MACRO> <LSP_FUNCTION_DECLARATION>my-zipmap</LSP_FUNCTION_DECLARATION> [<LSP_VARIABLE>keys</LSP_VARIABLE> <LSP_VARIABLE>vals</LSP_VARIABLE>]
                  (<LSP_MACRO>loop</LSP_MACRO> [<LSP_VARIABLE>my-map</LSP_VARIABLE> {}
                         <LSP_VARIABLE>my-keys</LSP_VARIABLE> (<LSP_FUNCTION>seq</LSP_FUNCTION> <LSP_VARIABLE>keys</LSP_VARIABLE>)
                         <LSP_VARIABLE>my-vals</LSP_VARIABLE> (<LSP_FUNCTION>seq</LSP_FUNCTION> <LSP_VARIABLE>vals</LSP_VARIABLE>)]
                    (<LSP_FUNCTION>if</LSP_FUNCTION> (<LSP_MACRO>and</LSP_MACRO> <LSP_VARIABLE>my-keys</LSP_VARIABLE> <LSP_VARIABLE>my-vals</LSP_VARIABLE>)
                      (<LSP_FUNCTION>recur</LSP_FUNCTION> (<LSP_FUNCTION>assoc</LSP_FUNCTION> <LSP_VARIABLE>my-map</LSP_VARIABLE> (<LSP_FUNCTION>first</LSP_FUNCTION> <LSP_VARIABLE>my-keys</LSP_VARIABLE>) (<LSP_FUNCTION>first</LSP_FUNCTION> <LSP_VARIABLE>my-vals</LSP_VARIABLE>))
                             (<LSP_FUNCTION>next</LSP_FUNCTION> <LSP_VARIABLE>my-keys</LSP_VARIABLE>)
                             (<LSP_FUNCTION>next</LSP_FUNCTION> <LSP_VARIABLE>my-vals</LSP_VARIABLE>))
                      <LSP_VARIABLE>my-map</LSP_VARIABLE>)))
                (<LSP_FUNCTION>my-zipmap</LSP_FUNCTION> [:<LSP_KEYWORD>a</LSP_KEYWORD> :<LSP_KEYWORD>b</LSP_KEYWORD> :<LSP_KEYWORD>c</LSP_KEYWORD>] [1 2 3])
                <LSP_MACRO>-></LSP_MACRO> {:<LSP_KEYWORD>b</LSP_KEYWORD> 2, :<LSP_KEYWORD>c</LSP_KEYWORD> 3, :<LSP_KEYWORD>a</LSP_KEYWORD> 1}
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
