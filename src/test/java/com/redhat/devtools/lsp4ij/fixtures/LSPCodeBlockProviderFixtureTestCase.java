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
import com.intellij.codeInsight.editorActions.CodeBlockUtil;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
 * Base class test case to test the 'codeBlockProvider' based on LSP 'textDocument/codeBlock' feature.
 */
public abstract class LSPCodeBlockProviderFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    protected LSPCodeBlockProviderFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    protected void assertCodeBlocks(@NotNull String fileName,
                                    @NotNull String fileBody,
                                    @NotNull String mockFoldingRangesJson,
                                    int[][] testOffsetScenarios) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);
        List<FoldingRange> mockFoldingRanges = JSONUtils.getLsp4jGson().fromJson(mockFoldingRangesJson, new TypeToken<List<FoldingRange>>() {
        }.getType());
        MockLanguageServer.INSTANCE.setFoldingRanges(mockFoldingRanges);

        Project project = myFixture.getProject();
        PsiFile file = myFixture.configureByText(fileName, fileBody);
        Editor editor = myFixture.getEditor();

        // Initialize the language server
        try {
            LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file.getVirtualFile(), null, null)
                    .get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        EditorTestUtil.buildInitialFoldingsInBackground(editor);

        CaretModel caretModel = editor.getCaretModel();
        for (int[] testOffsetScenario : testOffsetScenarios) {
            assertEquals("Test offset scenarios must be expressed in triplets.", 3, testOffsetScenario.length);
            int initialOffset = testOffsetScenario[0];
            int expectedMoveToStartOffset = testOffsetScenario[1];
            int expectedMoveToEndOffset = testOffsetScenario[2];

            caretModel.moveToOffset(initialOffset);
            CodeBlockUtil.moveCaretToCodeBlockStart(project, editor, false);
            assertEquals(expectedMoveToStartOffset, caretModel.getOffset());

            caretModel.moveToOffset(initialOffset);
            CodeBlockUtil.moveCaretToCodeBlockEnd(project, editor, false);
            assertEquals(expectedMoveToEndOffset, caretModel.getOffset());
        }
    }
}
