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

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base class test case to test LSP 'textDocument/formatting', 'textDocument/rangeFormatting' features.
 */
public abstract class LSPFormattingFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    public LSPFormattingFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    private class MyList {
        public List<TextEdit> edits;
    }

    protected void assertFormatting(@NotNull String fileName,
                                    @NotNull String text,
                                    @NotNull String jsonFormatting,
                                    @NotNull String formattedText) {
        MyList myList = JSONUtils.getLsp4jGson().fromJson("{\"edits\":" + jsonFormatting + "}", MyList.class);
        assertFormatting(fileName, text, myList.edits, formattedText);
    }

    /**
     * Test LSP formatting.
     *
     * @param fileName       the file name used to match registered language servers.
     * @param text           the editor content text.
     * @param formattingTextEdits the formatting TextEdit response of language server.
     * @param formattedText  the expected formatted text.
     */
    protected void assertFormatting(@NotNull String fileName,
                                    @NotNull String text,
                                    @NotNull List<TextEdit> formattingTextEdits,
                                    @NotNull String formattedText) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);
        MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);
        // Open editor for a given file name and content
        PsiFile file = myFixture.configureByText(fileName, text);
        // As LSPFormattingService is done in async mode, force the connection of the language server
        // to avoid having some block when ReadAction#compute is required (ex: call of LSP4IJUtils#getDocument).
        try {
            LanguageServiceAccessor.getInstance(file.getProject())
                    .getLanguageServers(file, null, null)
                    .get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }

        myFixture.type('1');
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT);
        assertEquals(formattedText, myFixture.getEditor().getDocument().getText());
    }

}
