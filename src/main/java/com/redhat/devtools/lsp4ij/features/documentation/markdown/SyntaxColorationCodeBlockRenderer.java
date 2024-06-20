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

import com.intellij.lang.Language;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.richcopy.SyntaxInfoBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.TokenType;
import com.redhat.devtools.lsp4ij.features.documentation.LightQuickDocHighlightingHelper;
import com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter;
import com.redhat.devtools.lsp4ij.internal.SimpleLanguageUtils;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRendererFactory;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.util.ast.ContentNode;
import com.vladsch.flexmark.util.data.DataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Custom Html renderer of Markdown to support syntax coloration (with TextMate and custom Syntax Lexer):
 *
 * <ul>
 *     <li>in markdown code block. Ex:
 *     ```typescript
 *      const s = "";
 *      ```
 *     </li>in indented blockquote (in this case it use the syntax coloration from the file which have triggered the hover / completion documentation. Ex:
 *     >     const s = "";
 *     </li>
 * </ul>
 */
public class SyntaxColorationCodeBlockRenderer implements NodeRenderer {

    private static final boolean HAS_TEXTMATE_SUPPORT = hasTextMateSupport();

    private final Project project;
    private final Language fileLanguage;

    private final String fileName;

    public SyntaxColorationCodeBlockRenderer(DataHolder options) {
        this.project = MarkdownConverter.PROJECT_CONTEXT.get(options);
        this.fileLanguage = MarkdownConverter.LANGUAGE_CONTEXT.get(options);
        this.fileName = MarkdownConverter.FILE_NAME_CONTEXT.get(options);
    }

    @Override
    public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
        Set<NodeRenderingHandler<?>> set = new HashSet<>();
        set.add(new NodeRenderingHandler<>(FencedCodeBlock.class, this::render));
        set.add(new NodeRenderingHandler<>(IndentedCodeBlock.class, this::render));
        return set;
    }

    static final class HighlighterRangeIterator implements SyntaxInfoBuilder.RangeIterator {
        private static final TextAttributes EMPTY_ATTRIBUTES = new TextAttributes();

        private final EditorHighlighter myHighlighter;
        private final HighlighterIterator myIterator;
        private final int myStartOffset;
        private final int myEndOffset;

        private int myCurrentStart;
        private int myCurrentEnd;
        private TextAttributes myCurrentAttributes;

        HighlighterRangeIterator(@NotNull EditorHighlighter highlighter, int startOffset, int endOffset) {
            myHighlighter = highlighter;
            myStartOffset = startOffset;
            myEndOffset = endOffset;
            myIterator = highlighter.createIterator(startOffset);
        }

        @Override
        public boolean atEnd() {
            return myIterator.atEnd() || getCurrentStart() >= myEndOffset;
        }

        private int getCurrentStart() {
            return Math.max(myIterator.getStart(), myStartOffset);
        }

        private int getCurrentEnd() {
            return Math.min(myIterator.getEnd(), myEndOffset);
        }

        @Override
        public void advance() {
            int prevEnd = myCurrentEnd;
            myCurrentStart = getCurrentStart();
            myCurrentEnd = getCurrentEnd();
            assert prevEnd <= myCurrentStart && myCurrentStart <= myCurrentEnd
                    : "Unexpected range returned by highlighter: " +
                    myIterator.getStart() + ":" + myIterator.getEnd() +
                    ", prevEnd: " + prevEnd +
                    ", scanned range: " + myStartOffset + ":" + myEndOffset +
                    ", resulting range: " + myCurrentStart + ":" + myCurrentEnd +
                    ", highlighter: " + myHighlighter;
            myCurrentAttributes = myIterator.getTokenType() == TokenType.BAD_CHARACTER ? EMPTY_ATTRIBUTES : myIterator.getTextAttributes();
            myIterator.advance();
        }

        @Override
        public int getRangeStart() {
            return myCurrentStart;
        }

        @Override
        public int getRangeEnd() {
            return myCurrentEnd;
        }

        @Override
        public TextAttributes getTextAttributes() {
            return myCurrentAttributes;
        }

        @Override
        public void dispose() {
        }

        @Override
        public String toString() {
            return "HighlighterRangeIterator[" + myHighlighter + "]";
        }
    }

    private void render(FencedCodeBlock node, NodeRendererContext context, HtmlWriter html) {
        // Try to highlight content of blockquote by using syntax coloration:
        // 1. by using specified languageId. Ex:

        // ```ts
        // const s = "";
        // ```

        // 2. or from the file which triggered the completion / hover documentation. Ex:

        // ```
        // const s = "";
        // ```
        String language = node.getInfo().toString();
        render(node, language, context, html);
    }

    void render(IndentedCodeBlock node, NodeRendererContext context, HtmlWriter html) {
        // Try to highlight content of blockquote by using syntax coloration from the file
        // which triggered the completion / hover documentation. Ex:
        // > const s = "";
        render(node, null, context, html);
    }

    private void render(ContentNode node, String language, NodeRendererContext context, HtmlWriter html) {
        html.line();
        html.srcPosWithTrailingEOL(node.getChars()).withAttr().tag("pre").openPre();

        CharSequence code = node.getContentChars();

        Language lang = getLanguage(language);
        if (lang != null) {
            // Case 1: Highlight code block with the guessed language
            String s = LightQuickDocHighlightingHelper.getStyledSignatureFragment(project, lang, node.getContentChars().toString());
            html.append(s);
        } else {
            // Case 2: Try to highlight code block with TextMate
            if (!(HAS_TEXTMATE_SUPPORT && TextMateHighlighterHelper.highlightWithTextMate(code, language , this.fileName, html))) {
                // Case 3 : no highlight
                html.withAttr().tag("code");
                html.text(node.getContentChars());
                html.tag("/code");
            }
        }
        html.tag("/pre").closePre();
    }

    @Nullable
    private Language getLanguage(String language) {
        if(language != null && StringUtils.isNotBlank(language)) {
            return LightQuickDocHighlightingHelper.guessLanguage(language);
        }
        if (fileLanguage != null && !SimpleLanguageUtils.isSupported(fileLanguage)) {
            // It is not a TextMate language or TEXT language, use it.
            return fileLanguage;
        }
        return null;
    }

    private static boolean hasTextMateSupport() {
        try {
            return Class.forName("org.jetbrains.plugins.textmate.TextMateService") != null;
        } catch (Throwable e) {
            return false;
        }
    }

    public static class Factory implements NodeRendererFactory {
        @Override
        public NodeRenderer apply(DataHolder options) {
            return new SyntaxColorationCodeBlockRenderer(options);
        }
    }
}

