/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererFactory;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Converts Markdown to HTML
 */
public class MarkdownConverter {

    private final Project project;

    private final Parser htmlParser;
    private final HtmlRenderer htmlRenderer;
    private final MutableDataSet options;

    public static MarkdownConverter getInstance(@NotNull Project project) {
        return project.getService(MarkdownConverter.class);
    }

    private MarkdownConverter(Project project) {
        this.project = project;

        //Adding flexmark extensions requires adding them to build.gradle.kts. Make sure classes are not picked up from the IJ platform!
        options = new MutableDataSet().set(Parser.EXTENSIONS, Arrays.asList(
                        AutolinkExtension.create(), //See flexmark-ext-autolink
                        StrikethroughExtension.create(), //See flexmark-ext-gfm-strikethrough
                        TablesExtension.create() //See flexmark-ext-tables
                ))
                // set GitHub table parsing options
                .set(TablesExtension.WITH_CAPTION, false)
                .set(TablesExtension.COLUMN_SPANS, false)
                .set(TablesExtension.MIN_HEADER_ROWS, 1)
                .set(TablesExtension.MAX_HEADER_ROWS, 1)
                .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
                .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
                .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true);

        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
        htmlRenderer = HtmlRenderer.builder(options)
                .nodeRendererFactory(new NodeRendererFactory() {
                    @Override
                    public NodeRenderer apply(DataHolder options) {
                        return new SyntaxColorationCodeBlockRenderer(project, null, null);
                    }
                }).build();
        htmlParser = Parser.builder(options).build();
    }


    /**
     * Convert the given <code>markdown</code> to Html.
     *
     * @param markdown the MarkDown content to convert to Html.
     * @return the given <code>markdown</code> to Html.
     */
    public @NotNull String toHtml(@NotNull String markdown) {
        return toHtml(markdown, null);
    }

    /**
     * Convert the given <code>markdown</code> to Html.
     *
     * @param markdown the MarkDown content to convert to Html.
     * @param file     the {@link PsiFile} which has triggered the convertion (from hover, completion documentation) used to retrieve
     *                 the syntax coloration to use if MarkDown content contains some code block or blockquote to highlight.
     * @return the given <code>markdown</code> to Html.
     */
    public @NotNull String toHtml(@NotNull String markdown, @Nullable PsiFile file) {
        return toHtml(markdown, file != null ? file.getLanguage() : null, file != null ? file.getName() : null);
    }

    /**
     * Convert the given <code>markdown</code> to Html.
     *
     * @param markdown the MarkDown content to convert to Html.
     * @param language the {@link Language} which must be used (if non-null) for MarkDown code block which defines the language or indented blockquote.
     * @param fileName the file name which must be used to retrieve TextMate (if non-null) for MarkDown code block which defines the language or indented blockquote.
     * @return the given <code>markdown</code> to Html.
     */
    public @NotNull String toHtml(@NotNull String markdown, @Nullable Language language, @Nullable String fileName) {
        var htmlRenderer = this.htmlRenderer;
        if (language != null || fileName != null) {
            htmlRenderer = HtmlRenderer.builder(options)
                    .nodeRendererFactory(new NodeRendererFactory() {
                        @Override
                        public NodeRenderer apply(DataHolder options) {
                            return new SyntaxColorationCodeBlockRenderer(project, language, fileName);
                        }
                    }).build();
        }
        return htmlRenderer.render(htmlParser.parse(markdown));
    }
}
