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
package com.redhat.devtools.lsp4ij.fixtures;

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider;
import com.intellij.platform.backend.documentation.DocumentationData;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationTarget;
import com.redhat.devtools.lsp4ij.features.documentation.LSPPendingHoverDocumentationTarget;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Hover;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class test case to test LSP 'textDocument/hover' feature.
 */
public abstract class LSPHoverFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    public LSPHoverFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    /**
     * Test LSP hover.
     *
     * @param fileName             the file name used to match registered language servers.
     * @param editorContentText    the editor content text.
     * @param jsonHover            the LSP CompletionList as JSON string.
     * @param expectedHoverContent the expected IJ lookupItem string.
     */
    protected void assertHover(@NotNull String fileName,
                               @NotNull String editorContentText,
                               @NotNull String jsonHover,
                               @Nullable String expectedHoverContent) {
        assertHover(fileName,
                editorContentText,
                JSONUtils.getLsp4jGson().fromJson(jsonHover, Hover.class),
                expectedHoverContent);
    }

    /**
     * Test LSP hover.
     *
     * @param fileName             the file name used to match registered language servers.
     * @param editorContentText    the editor content text.
     * @param hover                the LSP CompletionList.
     * @param expectedHoverContent the expected IJ lookupItem string.
     */
    protected void assertHover(@NotNull String fileName,
                               @NotNull String editorContentText,
                               @NotNull Hover hover,
                               @Nullable String expectedHoverContent) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);
        MockLanguageServer.INSTANCE.setHover(hover);
        // Open editor for a given file name and content (which declares <caret> to know where the completion is triggered).
        myFixture.configureByText(fileName, editorContentText);

        var editor = myFixture.getEditor();
        var file = myFixture.getFile();
        int offset = editor.getCaretModel().getOffset();
        List<String> html = getLSPHoverHtml(IdeDocumentationTargetProvider.getInstance(myFixture.getProject())
                .documentationTargets(editor, file, offset));
        if (expectedHoverContent == null) {
            assertEmpty(html);
            return;
        }
        assertSize(1, html);
        assertEquals("", expectedHoverContent, html.get(0));
    }

    /**
     * Returns the documentation of the LSP hover targets among the given ones, as HTML, waiting for the reply of a
     * pending target. LSPTargetElementEvaluator will always return a non-null target when the caret is on a word, but
     * only those that are translated into LSP targets are processed, and those without documentation are left out.
     *
     * @param targets the documentation targets.
     * @return the HTML of the LSP targets which have documentation.
     */
    public static @NotNull List<String> getLSPHoverHtml(@NotNull List<? extends DocumentationTarget> targets) {
        List<String> html = new ArrayList<>();
        for (DocumentationTarget target : targets) {
            if (target instanceof LSPDocumentationTarget lspTarget) {
                html.add(((DocumentationData) lspTarget.computeDocumentation()).getHtml());
            } else if (target instanceof LSPPendingHoverDocumentationTarget pendingTarget) {
                ContainerUtil.addIfNotNull(html, pendingTarget.getHtml());
            }
        }
        return html;
    }
}
