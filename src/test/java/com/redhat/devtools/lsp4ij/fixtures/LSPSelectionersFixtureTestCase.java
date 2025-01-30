/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
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
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.SelectionRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base class for language-specific testing of selectioner behavior.
 */
public abstract class LSPSelectionersFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    protected LSPSelectionersFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    /**
     * Confirms selection ranges for the <b>Extend Selection</b> action.
     *
     * @param fileName                   the file name
     * @param fileBody                   the file body
     * @param mockSelectionRangesJson    mock {@code textDocument/selectionRange} response JSON
     * @param mockFoldingRangesJson      mock {@code textDocument/foldingRange} response JSON
     * @param startLineNumber            the line number at the beginning of which the <b>Extend Selection</b> action
     *                                   will be invoked
     * @param expectedSelectedLineRanges the expected selection ranges for each subsequent invocation of the
     *                                   <b>Extend Selection</b> action from from {@code startLineNumber}
     */
    protected void testSelectioner(@NotNull String fileName,
                                   @NotNull String fileBody,
                                   @NotNull String mockSelectionRangesJson,
                                   @NotNull String mockFoldingRangesJson,
                                   int startLineNumber,
                                   int[][] expectedSelectedLineRanges) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);
        List<SelectionRange> mockSelectionRanges = JSONUtils.getLsp4jGson().fromJson(mockSelectionRangesJson, new TypeToken<List<SelectionRange>>() {
        }.getType());
        MockLanguageServer.INSTANCE.setSelectionRanges(mockSelectionRanges);

        List<FoldingRange> mockFoldingRanges = JSONUtils.getLsp4jGson().fromJson(mockFoldingRangesJson, new TypeToken<List<FoldingRange>>() {
        }.getType());
        MockLanguageServer.INSTANCE.setFoldingRanges(mockFoldingRanges);

        String fileBodyWithoutReferenceLineNumbers = fileBody.replaceAll("(?ms)[ \t]+// line \\d+[ \t]*$", "");
        PsiFile file = myFixture.configureByText(fileName, fileBodyWithoutReferenceLineNumbers);
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();

        // Initialize the language server
        try {
            LanguageServiceAccessor.getInstance(file.getProject())
                    .getLanguageServers(file.getVirtualFile(), null, null)
                    .get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // Move the caret to the beginning of the start line
        int lineStartOffset = document.getLineStartOffset(startLineNumber);
        CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(lineStartOffset);

        // Make sure there's no initial selection
        SelectionModel selectionModel = editor.getSelectionModel();
        assertFalse(selectionModel.hasSelection());

        // Extend the selection for each of the provided selection ranges and confirm they match expectations
        for (int[] expectedSelectedLineRange : expectedSelectedLineRanges) {
            int expectedSelectionStartLine = expectedSelectedLineRange[0];
            int expectedSelectionEndLine = expectedSelectedLineRange[1];

            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);

            String selectedText = selectionModel.getSelectedText();
            assertNotNull(selectedText);
            assertFalse(selectedText.isEmpty());

            int actualSelectionStartOffset = selectionModel.getSelectionStart();
            int actualSelectionStartLine = document.getLineNumber(actualSelectionStartOffset);
            assertEquals(expectedSelectionStartLine, actualSelectionStartLine);
            assertEquals(actualSelectionStartOffset, document.getLineStartOffset(actualSelectionStartLine));

            int actualSelectionEndOffset = selectionModel.getSelectionEnd();
            int actualSelectionEndLine = document.getLineNumber(actualSelectionEndOffset);
            // The selection may actually extend to the beginning of the next line
            if (selectedText.endsWith("\n")) {
                assertEquals(expectedSelectionEndLine + 1, actualSelectionEndLine);
                assertEquals(actualSelectionEndOffset, document.getLineStartOffset(actualSelectionEndLine));
            } else {
                assertEquals(expectedSelectionEndLine, actualSelectionEndLine);
                assertEquals(actualSelectionEndOffset, document.getLineEndOffset(actualSelectionEndLine));
            }
        }
    }
}
