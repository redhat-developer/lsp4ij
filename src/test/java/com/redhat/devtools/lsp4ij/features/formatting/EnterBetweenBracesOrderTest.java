/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link LSPEditorImprovementsEnterBetweenBracesHandler} fixes the Enter between braces of the files of language servers
 * only, which {@link TypeScriptEditorImprovementsTest} tests. Any other file gets the platform's Enter between braces
 * where it is registered ({@code EnterBetweenBracesHandler}), after the handlers which ask to run before it, as those of
 * Kotlin and Java do. A JSON file has no language server here, and its braces are elements of their own, which the
 * platform's Enter between braces splits.
 */
public class EnterBetweenBracesOrderTest extends AbstractTypeScriptEditorImprovementsTest {

    private static final String FILE_WITHOUT_LANGUAGE_SERVER = "test.json";
    private static final String BRACES = "{}";

    public void testTheEnterBetweenTheBracesOfAFileWithoutALanguageServerSplitsThem() {
        myFixture.configureByText(FILE_WITHOUT_LANGUAGE_SERVER, "{" + CARET + "}");

        EditorTestUtil.performTypingAction(myFixture.getEditor(), '\n');

        assertBracesSplitWithTheCaretBetween(myFixture.getEditor());
    }

    public void testAHandlerBeforeTheEnterBetweenBracesSeesTheBracesOfAFileWithoutALanguageServerAsTheyWere() {
        myFixture.configureByText(FILE_WITHOUT_LANGUAGE_SERVER, "{" + CARET + "}");
        List<String> seen = textsSeenBeforeTheEnterBetweenBraces();

        EditorTestUtil.performTypingAction(myFixture.getEditor(), '\n');

        assertEquals("Nothing splits the braces before the platform's Enter between braces", List.of(BRACES), seen);
        assertBracesSplitWithTheCaretBetween(myFixture.getEditor());
    }

    /**
     * Returns the texts of the document that an Enter handler ordered before the platform's Enter between braces sees,
     * one for each Enter.
     */
    private @NotNull List<String> textsSeenBeforeTheEnterBetweenBraces() {
        List<String> seen = new CopyOnWriteArrayList<>();
        EnterHandlerDelegate.EP_NAME.getPoint().registerExtension(new EnterHandlerDelegateAdapter() {
            @Override
            public Result preprocessEnter(@NotNull PsiFile file,
                                          @NotNull Editor editor,
                                          @NotNull Ref<Integer> caretOffset,
                                          @NotNull Ref<Integer> caretAdvance,
                                          @NotNull DataContext dataContext,
                                          @Nullable EditorActionHandler originalHandler) {
                seen.add(editor.getDocument().getText());
                return Result.Continue;
            }
        }, LoadingOrder.readOrder("before EnterBetweenBracesHandler"), getTestRootDisposable());
        return seen;
    }

    /**
     * Asserts that the Enter split the braces: the open brace ends the first line, the close brace is on the last line,
     * alone, and the caret is on the line between them.
     */
    private static void assertBracesSplitWithTheCaretBetween(@NotNull Editor editor) {
        Document document = editor.getDocument();
        String text = document.getText();
        assertEquals(text, 3, document.getLineCount());
        assertEquals(text, "{", lineText(document, 0).trim());
        assertEquals(text, "}", lineText(document, 2).trim());
        assertEquals(text, 1, document.getLineNumber(editor.getCaretModel().getOffset()));
    }

    private static @NotNull String lineText(@NotNull Document document, int line) {
        return document.getText().substring(document.getLineStartOffset(line), document.getLineEndOffset(line));
    }
}
