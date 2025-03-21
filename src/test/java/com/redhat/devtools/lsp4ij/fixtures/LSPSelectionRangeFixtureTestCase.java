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
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.SelectionRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base class test case to test LSP 'textDocument/selectionRange' feature.
 */
public abstract class LSPSelectionRangeFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    protected LSPSelectionRangeFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    protected void assertSelectionRanges(@NotNull String fileName,
                                         @NotNull String fileBody,
                                         @NotNull String setCaretBefore,
                                         @NotNull String mockSelectionRangesJson,
                                         @NotNull String... selections) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);
        List<SelectionRange> mockSelectionRanges = JSONUtils.getLsp4jGson().fromJson(mockSelectionRangesJson, new TypeToken<List<SelectionRange>>() {
        }.getType());
        MockLanguageServer.INSTANCE.setSelectionRanges(mockSelectionRanges);

        PsiFile file = myFixture.configureByText(fileName, fileBody);
        Editor editor = myFixture.getEditor();

        // Make sure there's no initial selection
        SelectionModel selectionModel = editor.getSelectionModel();
        assertFalse(selectionModel.hasSelection());

        // Move the caret to the specified text
        int setCaretBeforeOffset = fileBody.indexOf(setCaretBefore);
        assertTrue(setCaretBeforeOffset > -1);
        CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(setCaretBeforeOffset);

        // Initialize the language server
        try {
            LanguageServiceAccessor.getInstance(file.getProject())
                    .getLanguageServers(file, null, null)
                    .get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // Trigger the Extend Selection action repeatedly and confirm selections
        for (String expectedSelection : selections) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
            String actualSelection = selectionModel.getSelectedText();
            assertEquals(expectedSelection, actualSelection);
        }

        // If the entire file is selected, extending the selection again should leave it unchanged
        String lastSelection = ArrayUtil.getLastElement(selections);
        if (fileBody.equals(lastSelection)) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
            String actualSelection = selectionModel.getSelectedText();
            assertEquals(fileBody, actualSelection);
        }

        // And now do the opposite for the Shrink Selection action; start with the next-to-last selection
        for (int i = selections.length - 2; i >= 0; i--) {
            String expectedSelection = selections[i];
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET);
            String actualSelection = selectionModel.getSelectedText();
            assertEquals(expectedSelection, actualSelection);
        }

        // One more time and there should be no selection again
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET);
        assertFalse(selectionModel.hasSelection());
    }
}
