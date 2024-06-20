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
package com.redhat.devtools.lsp4ij.features.documentation.markdown;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.DataStorage;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.util.registry.Registry;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.features.documentation.markdown.SyntaxColorationCodeBlockRenderer;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.vladsch.flexmark.html.HtmlWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateHighlighter;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerDataStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper to highlight code with the proper textmate.
 */
public class TextMateHighlighterHelper {

    private static final Map<String, String> languageFileExtensionCache;

    static {
        languageFileExtensionCache = new HashMap<>();
        // TODO: fill this mapping in an extension point
        // languageFileExtensionCache.put("python", "py");
        // languageFileExtensionCache.put("typescript", "ts");
        // languageFileExtensionCache.put("clojure", "clj");
        // languageFileExtensionCache.put("rust", "rs");
    }

    public static boolean highlightWithTextMate(CharSequence code, final String languageId, String fileName, HtmlWriter html) {
        // Find TextMate language descriptor by file extension.
        var languageDescriptor = findLanguageDescriptor(languageId, fileName);
        if (languageDescriptor == null) {
            return false;
        }
        // TextMate exists, highlight the code with the textmate
        int length = code.length();
        var textMateHighlighter = new TextMateHighlighter(new TextMateHighlightingLexer(languageDescriptor,
                Registry.get("textmate.line.highlighting.limit").asInteger()));

        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        var editorHighlighter = new TextMateLexerEditorHighlighter(textMateHighlighter, scheme);
        editorHighlighter.setText(code);

        SyntaxColorationCodeBlockRenderer.HighlighterRangeIterator highlighterRangeIterator =
                new SyntaxColorationCodeBlockRenderer.HighlighterRangeIterator(editorHighlighter, 0, length);

        var htmlTextMate = HtmlSyntaxInfoUtil.getHtmlContent(code, highlighterRangeIterator, scheme, length);
        html.append(htmlTextMate);
        return true;
    }

    @Nullable
    private static TextMateLanguageDescriptor findLanguageDescriptor(String languageId, String fileName) {
        if (fileName != null) {
            var languageDescriptor = TextMateService.getInstance().getLanguageDescriptorByFileName(fileName);
            if (languageDescriptor != null) {
                return languageDescriptor;
            }
        }
        if (languageId != null) {
            List<String> fileExtensions = LanguageServersRegistry.getInstance().getFileExtensions(languageId);
            if (fileExtensions != null) {
                for (var fileExtension : fileExtensions) {
                    var languageDescriptor = TextMateService.getInstance().getLanguageDescriptorByExtension(fileExtension);
                    if (languageDescriptor != null) {
                        return languageDescriptor;
                    }
                }
            }
            String fileExtension = getFileExtensionFor(languageId);
            return TextMateService.getInstance().getLanguageDescriptorByExtension(fileExtension);
        }
        return null;
    }

    @NotNull
    private static String getFileExtensionFor(String language) {
        String fileExtension = languageFileExtensionCache.get(language);
        return StringUtils.isEmpty(fileExtension) ? language : fileExtension;
    }

    private static final class TextMateLexerEditorHighlighter extends LexerEditorHighlighter {
        private TextMateLexerEditorHighlighter(@Nullable SyntaxHighlighter highlighter, @NotNull EditorColorsScheme colors) {
            super(highlighter != null ? highlighter : new TextMateHighlighter(null), colors);
        }

        @NotNull
        @Override
        protected DataStorage createStorage() {
            return new TextMateLexerDataStorage();
        }
    }
}
