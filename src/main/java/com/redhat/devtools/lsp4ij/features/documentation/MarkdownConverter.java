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
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.features.documentation.markdown.LSPLinkResolver;
import com.redhat.devtools.lsp4ij.features.documentation.markdown.SyntaxColorationCodeBlockRenderer;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.DataKey;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * Converts Markdown to HTML
 */
@ApiStatus.Internal
public class MarkdownConverter {

    private static final Key<HtmlRenderer> HTML_RENDERER_KEY = Key.create("lsp.html.renderer");

    public static final DataKey<Project> PROJECT_CONTEXT = new DataKey<>("LSP_PROJECT", (Project) null);
    public static final DataKey<String> FILE_NAME_CONTEXT = new DataKey<>("LSP_FILE_NAME", "");
    public static final DataKey<Language> LANGUAGE_CONTEXT = new DataKey<>("LSP_LANGUAGE", (Language) null);
    public static final DataKey<Path> FILE_BASE_DIR = new DataKey<>("LSP_BASE_DIR", (Path) null);

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

        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
        options.set(PROJECT_CONTEXT, project);
        htmlRenderer = createHtmlRenderer(options);
        htmlParser = Parser.builder(options).build();
    }

    @NotNull
    private static HtmlRenderer createHtmlRenderer(MutableDataSet options) {
        return HtmlRenderer.builder(options)
                .linkResolverFactory(new LSPLinkResolver.Factory())
                .nodeRendererFactory(new SyntaxColorationCodeBlockRenderer.Factory())
                .build();
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
    public @NotNull String toHtml(@NotNull String markdown,
                                  @Nullable PsiFile file) {
        var htmlRenderer = this.htmlRenderer;
        if (file != null) {
            // The HtmlRenderer is stored in LSPFileSupport instead of PsiFile
            // to evict it when file is closed.
            var fileSupport = LSPFileSupport.getSupport(file);
            htmlRenderer = fileSupport.getUserData(HTML_RENDERER_KEY);
            if (htmlRenderer == null) {
                htmlRenderer = getHtmlRenderer(fileSupport);
            }
        }
        return htmlRenderer.render(htmlParser.parse(markdown));
    }

    private synchronized HtmlRenderer getHtmlRenderer(@NotNull LSPFileSupport fileSupport) {
        var htmlRenderer = fileSupport.getUserData(HTML_RENDERER_KEY);
        if (htmlRenderer != null) {
            return htmlRenderer;
        }

        var file = fileSupport.getFile();
        MutableDataSet fileOptions = new MutableDataSet(options);
        fileOptions.set(LANGUAGE_CONTEXT, file.getLanguage());
        fileOptions.set(FILE_NAME_CONTEXT, file.getName());
        Path baseDir = file.getVirtualFile().getParent().getFileSystem().getNioPath(file.getVirtualFile().getParent());
        fileOptions.set(FILE_BASE_DIR, baseDir);

        htmlRenderer = createHtmlRenderer(fileOptions);
        fileSupport.putUserData(HTML_RENDERER_KEY, htmlRenderer);
        file.putUserData(HTML_RENDERER_KEY, htmlRenderer);
        return htmlRenderer;
    }

    /**
     * This method is just used by Junit tests.
     * <p>
     * Convert the given <code>markdown</code> to Html.
     *
     * @param markdown the MarkDown content to convert to Html.
     * @param language the {@link Language} which must be used (if non-null) for MarkDown code block which defines the language or indented blockquote.
     * @param fileName the file name which must be used to retrieve TextMate (if non-null) for MarkDown code block which defines the language or indented blockquote.
     * @return the given <code>markdown</code> to Html.
     */
    public @NotNull String toHtml(@NotNull String markdown,
                                  @Nullable Path baseDir,
                                  @Nullable Language language,
                                  @Nullable String fileName) {
        var htmlRenderer = this.htmlRenderer;
        if (language != null || fileName != null) {
            MutableDataSet fileOptions = new MutableDataSet(options);
            fileOptions.set(FILE_BASE_DIR, baseDir);
            fileOptions.set(LANGUAGE_CONTEXT, language);
            fileOptions.set(FILE_NAME_CONTEXT, fileName);
            htmlRenderer = createHtmlRenderer(fileOptions);
        }
        return htmlRenderer.render(htmlParser.parse(markdown));
    }
}
