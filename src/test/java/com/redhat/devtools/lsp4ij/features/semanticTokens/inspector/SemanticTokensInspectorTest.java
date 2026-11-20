/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens.inspector;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The semantic tokens inspector shows the tokens in the text that they were computed for, also once the document has
 * changed (#399), shows nothing for a token outside that text, and a refresh of its text doesn't move its caret to the
 * end, which scrolls it to the bottom (#400).
 */
public class SemanticTokensInspectorTest extends BasePlatformTestCase {

    public void testTheTokensShowInTheTextTheyWereComputedForOnceTheDocumentHasChanged() {
        var file = myFixture.configureByText("main.txt", "abc def");
        var document = myFixture.getEditor().getDocument();
        var data = new SemanticTokensInspectorData(document, file, tokens(new SemanticTokensHighlightInfo(
                "keyword", List.of(), 4, 7, null)));

        // An edit, as by typing or an undo, before the inspector shows the tokens
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("x"));

        assertEquals("abc <keyword>def</keyword>", format(data));
    }

    public void testATokenOutsideTheTextIsNotShown() {
        var file = myFixture.configureByText("main.txt", "abc def");
        var document = myFixture.getEditor().getDocument();
        var data = new SemanticTokensInspectorData(document, file, tokens(
                new SemanticTokensHighlightInfo("keyword", List.of(), 0, 3, null),
                new SemanticTokensHighlightInfo("variable", List.of(), 4, 99999, null)));

        assertEquals("<keyword>abc</keyword> def", format(data));
    }

    public void testARefreshOfTheTextDoesNotMoveTheCaretToItsEnd() {
        var editor = SemanticTokensInspectorToolWindowPanel.createEditor();
        editor.setText("line\n".repeat(100));
        editor.setCaretPosition(10);

        String refreshed = "<keyword>line</keyword>\n".repeat(100);
        editor.setText(refreshed);

        assertTrue("A refresh must not move the caret to the end of the text, which scrolls the view to the bottom",
                editor.getCaretPosition() < refreshed.length());
    }

    private @NotNull String format(@NotNull SemanticTokensInspectorData data) {
        return SemanticTokensInspectorManager.format(data, false, true, false, getProject());
    }

    private static @NotNull List<SemanticTokensHighlightInfo> tokens(SemanticTokensHighlightInfo... infos) {
        // The inspector sorts the tokens in place
        return new ArrayList<>(List.of(infos));
    }
}
