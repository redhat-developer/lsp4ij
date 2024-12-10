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

import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.FoldingRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base class test case to test LSP 'textDocument/foldingRange' feature.
 */
public abstract class LSPFoldingRangeFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    protected LSPFoldingRangeFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    protected void assertFoldingRanges(@NotNull String fileName,
                                       @NotNull String fileBody,
                                       @NotNull String mockFoldingRangesJson,
                                       @NotNull TextRange... expectedFoldingTextRanges) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);
        List<FoldingRange> mockFoldingRanges = JSONUtils.getLsp4jGson().fromJson(mockFoldingRangesJson, new TypeToken<List<FoldingRange>>() {
        }.getType());
        MockLanguageServer.INSTANCE.setFoldingRanges(mockFoldingRanges);

        PsiFile file = myFixture.configureByText(fileName, fileBody);
        Editor editor = myFixture.getEditor();

        // Initialize the language server
        try {
            LanguageServiceAccessor.getInstance(file.getProject())
                    .getLanguageServers(file.getVirtualFile(), null, null)
                    .get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        EditorTestUtil.buildInitialFoldingsInBackground(editor);
        FoldingModel foldingModel = editor.getFoldingModel();
        FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();

        assertEquals(expectedFoldingTextRanges.length, foldRegions.length);

        for (int i = 0; i < foldRegions.length; i++) {
            FoldRegion actualFoldRegion = foldRegions[i];

            // Check the text range
            TextRange expectedFoldingTextRange = expectedFoldingTextRanges[i];
            assertEquals(expectedFoldingTextRange, actualFoldRegion.getTextRange());

            // Check the placeholder text
            FoldingRange expectedFoldingRange = mockFoldingRanges.get(i);
            String expectedPlaceholderText = StringUtil.isNotEmpty(expectedFoldingRange.getCollapsedText()) ? expectedFoldingRange.getCollapsedText() : "...";
            assertEquals(expectedPlaceholderText, actualFoldRegion.getPlaceholderText());
        }
    }
}
