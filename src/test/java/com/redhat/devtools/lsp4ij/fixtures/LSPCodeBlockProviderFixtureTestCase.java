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

    protected static int before(@NotNull String fileBody, @NotNull String snippet, int count) {
        int fromIndex = 0;
        for (int i = 0; i < count; i++) {
            int index = fileBody.indexOf(snippet, fromIndex);
            assertFalse("Failed to find occurrence " + (i + 1) + " of '" + snippet + "'.", index == -1);
            if (count == (i + 1)) {
                return index;
            } else {
                fromIndex = index + 1;
                if (fromIndex == fileBody.length()) {
                    fail("Failed to find occurrence " + (i + 1) + " of '" + snippet + "'.");
                }
            }
        }
        return fromIndex;
    }

    protected static int after(@NotNull String fileBody, @NotNull String snippet, int count) {
        return before(fileBody, snippet, count) + snippet.length();
    }

    protected static int beforeLast(@NotNull String fileBody, @NotNull String snippet, int count) {
        int fromIndex = fileBody.length() - 1;
        for (int i = 0; i < count; i++) {
            int index = fileBody.lastIndexOf(snippet, fromIndex);
            assertFalse("Failed to find last occurrence " + (i + 1) + " of '" + snippet + "'.", index == -1);
            if (count == (i + 1)) {
                return index;
            } else {
                fromIndex = index - 1;
                if (fromIndex == 0) {
                    fail("Failed to find occurrence " + (i + 1) + " of '" + snippet + "'.");
                }
            }
        }
        return fromIndex;
    }

    protected static int afterLast(@NotNull String fileBody, @NotNull String snippet, int count) {
        return beforeLast(fileBody, snippet, count) + snippet.length();
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
