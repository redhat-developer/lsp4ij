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
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Hover;
import org.jetbrains.annotations.NotNull;

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
     * @param fileName           the file name used to match registered language servers.
     * @param editorContentText  the editor content text.
     * @param jsonHover the LSP CompletionList as JSON string.
     * @param expectedHoverContent      the expected IJ lookupItem string.
     */
    protected void assertHover(@NotNull String fileName,
                                    @NotNull String editorContentText,
                                    @NotNull String jsonHover,
                                    @NotNull String expectedHoverContent) {
        assertHover(fileName,
                editorContentText,
                JSONUtils.getLsp4jGson().fromJson(jsonHover, Hover.class),
                expectedHoverContent);
    }

    /**
     * Test LSP hover.
     *
     * @param fileName          the file name used to match registered language servers.
     * @param editorContentText the editor content text.
     * @param hover    the LSP CompletionList.
     * @param expectedHoverContent     the expected IJ lookupItem string.
     */
    protected void assertHover(@NotNull String fileName,
                                    @NotNull String editorContentText,
                                    @NotNull Hover hover,
                                    @NotNull String expectedHoverContent) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(250);
        MockLanguageServer.INSTANCE.setHover(hover);
        // Open editor for a given file name and content (which declares <caret> to know where the completion is triggered).
        myFixture.configureByText(fileName, editorContentText);

        var editor = myFixture.getEditor();
        var file = myFixture.getFile();
        int offset = editor.getCaretModel().getOffset();
        List<? extends @NotNull DocumentationTarget> targets = IdeDocumentationTargetProvider.getInstance(myFixture.getProject()).documentationTargets(editor, file, offset);
        assertSize(1, targets);

        DocumentationTarget target = targets.get(0);
        DocumentationResult documentationResult = target.computeDocumentation();
        DocumentationData documentationData = (DocumentationData) documentationResult;

        String actual = documentationData.getHtml();
        assertEquals("", expectedHoverContent, actual);

    }

}
