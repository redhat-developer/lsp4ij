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
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.vladsch.flexmark.html.HtmlWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateHighlighter;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerDataStorage;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper to highlight code with the proper textmate.
 */
public class TextMateHighlighterHelper {

    /**
     * The file extension of a language whose name, as a code block gives it, isn't one: the language identifiers of
     * the LSP specification, and names that code blocks often use (#557).
     */
    private static final Map<String, String> FILE_EXTENSIONS = Map.ofEntries(
            Map.entry("bash", "sh"),
            Map.entry("bibtex", "bib"),
            Map.entry("c#", "cs"),
            Map.entry("c++", "cpp"),
            Map.entry("clojure", "clj"),
            Map.entry("coffeescript", "coffee"),
            Map.entry("csharp", "cs"),
            Map.entry("elixir", "ex"),
            Map.entry("erlang", "erl"),
            Map.entry("fsharp", "fs"),
            Map.entry("golang", "go"),
            Map.entry("handlebars", "hbs"),
            Map.entry("haskell", "hs"),
            Map.entry("javascript", "js"),
            Map.entry("javascriptreact", "jsx"),
            Map.entry("julia", "jl"),
            Map.entry("kotlin", "kt"),
            Map.entry("latex", "tex"),
            Map.entry("markdown", "md"),
            Map.entry("objective-c", "m"),
            Map.entry("objective-cpp", "mm"),
            Map.entry("ocaml", "ml"),
            Map.entry("perl", "pl"),
            Map.entry("powershell", "ps1"),
            Map.entry("python", "py"),
            Map.entry("razor", "cshtml"),
            Map.entry("ruby", "rb"),
            Map.entry("rust", "rs"),
            Map.entry("shell", "sh"),
            Map.entry("shellscript", "sh"),
            Map.entry("typescript", "ts"),
            Map.entry("typescriptreact", "tsx"),
            Map.entry("zsh", "sh"));

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

    /**
     * Returns the TextMate grammar of the given language of a code block, or else, as for a code block without a
     * language, that of the given file, whose documentation it is. The language of the code block comes first: the
     * grammar of the file would color a code block of another language, as {@code ```python} in the documentation of a
     * Rust file, by the rules of the file's language (#557).
     */
    @Nullable
    private static TextMateLanguageDescriptor findLanguageDescriptor(String languageId, String fileName) {
        if (StringUtils.isNotBlank(languageId)) {
            var languageDescriptor = findLanguageDescriptor(languageId);
            if (languageDescriptor != null) {
                return languageDescriptor;
            }
        }
        if (fileName != null) {
            return TextMateService.getInstance().getLanguageDescriptorByFileName(fileName);
        }
        return null;
    }

    /**
     * Returns the TextMate grammar of the given language: that of a file extension which a language server's mapping
     * gives the language, or else that of the language's name as a file extension, as {@code ts}, or of the file
     * extension of the name, as {@code rs} for {@code rust}.
     */
    @Nullable
    private static TextMateLanguageDescriptor findLanguageDescriptor(@NotNull String languageId) {
        List<String> fileExtensions = LanguageServersRegistry.getInstance().getFileExtensions(languageId);
        if (fileExtensions != null) {
            for (var fileExtension : fileExtensions) {
                var languageDescriptor = TextMateService.getInstance().getLanguageDescriptorByExtension(fileExtension);
                if (languageDescriptor != null) {
                    return languageDescriptor;
                }
            }
        }
        String name = languageId.toLowerCase(Locale.ROOT);
        return TextMateService.getInstance().getLanguageDescriptorByExtension(FILE_EXTENSIONS.getOrDefault(name, name));
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
