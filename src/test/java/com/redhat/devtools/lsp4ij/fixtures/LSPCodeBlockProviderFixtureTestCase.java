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

    private static final String CARET_TOKEN = "<caret>";
    private static final String START_TOKEN = "<start>";
    private static final String END_TOKEN = "<end>";

    protected LSPCodeBlockProviderFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    protected void assertCodeBlocks(@NotNull String fileName,
                                    @NotNull String fileBody,
                                    @NotNull String mockFoldingRangesJson) {
        // Gather the raw token offsets
        int rawCaretOffset = fileBody.indexOf(CARET_TOKEN);
        assertFalse("No " + CARET_TOKEN + " found.", rawCaretOffset == -1);
        int rawStartOffset = fileBody.indexOf(START_TOKEN);
        assertFalse("No " + START_TOKEN + " found.", rawStartOffset == -1);
        int rawEndOffset = fileBody.indexOf(END_TOKEN);
        assertFalse("No " + END_TOKEN + " found.", rawEndOffset == -1);

        // Compute final offsets as appropriate based on relative token positioning
        int caretOffset = rawCaretOffset;
        if (rawCaretOffset > rawStartOffset) caretOffset -= START_TOKEN.length();
        if (rawCaretOffset > rawEndOffset) caretOffset -= END_TOKEN.length();
        int startOffset = rawStartOffset;
        if (rawStartOffset > rawCaretOffset) startOffset -= CARET_TOKEN.length();
        if (rawStartOffset > rawEndOffset) startOffset -= END_TOKEN.length();
        int endOffset = rawEndOffset;
        if (rawEndOffset > rawCaretOffset) endOffset -= CARET_TOKEN.length();
        if (rawEndOffset > rawStartOffset) endOffset -= START_TOKEN.length();

        // Remove the tokens
        fileBody = fileBody
                .replace(CARET_TOKEN, "")
                .replace(START_TOKEN, "")
                .replace(END_TOKEN, "");

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

        caretModel.moveToOffset(caretOffset);
        CodeBlockUtil.moveCaretToCodeBlockStart(project, editor, false);
        assertEquals(startOffset, caretModel.getOffset());

        caretModel.moveToOffset(caretOffset);
        CodeBlockUtil.moveCaretToCodeBlockEnd(project, editor, false);
        assertEquals(endOffset, caretModel.getOffset());
    }
}