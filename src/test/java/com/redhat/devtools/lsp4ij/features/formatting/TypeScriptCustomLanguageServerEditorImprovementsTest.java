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

package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;

/**
 * These verify that editor improvements are disabled by default in custom (i.e., non-user-defined) language servers.
 */
public class TypeScriptCustomLanguageServerEditorImprovementsTest extends AbstractTypeScriptEditorImprovementsTest {

    // Verify LSPEditorImprovementsTypedHandler.handleNestedQuote() and LSPEditorImprovementsBackspaceHandler

    public void testNestedQuotesStringLiteralDisabledByDefault() {
        char outerQuote = '\'';
        char innerQuote = '"';

        String fileBody = "console.log();";
        int initialOffset = fileBody.indexOf("(") + 1;

        myFixture.configureByText(TEST_FILE_NAME, fileBody);
        initializeLanguageServer();

        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();

        CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(initialOffset);

        // Typing an initial outer quote character should result in paired quotes
        EditorTestUtil.performTypingAction(editor, outerQuote);
        assertEquals("console.log(" + outerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());

        // Typing another outer quote character should just advance the caret outside of the string literal
        EditorTestUtil.performTypingAction(editor, outerQuote);
        assertEquals("console.log(" + outerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 2, caretModel.getOffset());

        // Return to the interior of the string literal and type an inner quote character which should be inserted
        // paired with the caret between inner quotes
        caretModel.moveToOffset(initialOffset + 1);
        EditorTestUtil.performTypingAction(editor, innerQuote);
        assertEquals("console.log(" + outerQuote + innerQuote + innerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 2, caretModel.getOffset());

        // Typing a backspace should remove the paired inner quotes
        EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE);
        assertEquals("console.log(" + outerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());

        // Now insert an escaped outer quote just before the close outer quote which should result in advancing the
        // caret outside of the close outer quote
        EditorTestUtil.performTypingAction(editor, BACKSLASH);
        EditorTestUtil.performTypingAction(editor, outerQuote);
        assertEquals("console.log(" + outerQuote + BACKSLASH + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 3, caretModel.getOffset());
    }

    // Verify LSPEditorImprovementsTypedHandler.handleStatementTerminator()

    public void testStatementTerminatorDisabledByDefault() {
        String fileBody = "console.log()";
        int initialOffset = fileBody.indexOf(")") + 1;

        myFixture.configureByText(TEST_FILE_NAME, fileBody);
        initializeLanguageServer();

        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();

        CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(initialOffset);

        // Typing a statement terminator should insert that character and advance the caret
        EditorTestUtil.performTypingAction(editor, ';');
        assertEquals(fileBody + ";", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());

        // Move back to just before the statement terminator and type it again; it should insert another terminator
        caretModel.moveToOffset(initialOffset);
        EditorTestUtil.performTypingAction(editor, ';');
        assertEquals(fileBody + ";;", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());
    }

    // Verify LSPEditorImprovementsEnterBetweenBracesHandler

    public void testEnterBetweenBracesDisabledByDefault() {
        String fileBody =
                """
                        export class Foo {
                            values = [];
                            bar() {}
                        }
                        """;

        myFixture.configureByText(TEST_FILE_NAME, fileBody);
        initializeLanguageServer();

        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        CaretModel caretModel = editor.getCaretModel();

        // Use the appropriate indent
        CodeStyle.getSettings(editor).getIndentOptions().USE_TAB_CHARACTER = false;

        // Move into the empty brackets and type enter
        int bracketsOffset = fileBody.indexOf("[]") + 1;
        caretModel.moveToOffset(bracketsOffset);
        EditorTestUtil.performTypingAction(editor, '\n');
        String enterBetweenBracketsFileBody = """
                export class Foo {
                    values = [
                    <caret>];
                    bar() {}
                }
                """;
        assertEquals(enterBetweenBracketsFileBody.replace(CARET, ""), document.getText());
        assertEquals(enterBetweenBracketsFileBody.indexOf(CARET), caretModel.getOffset());

        // Move into the empty parens and type enter
        fileBody = document.getText();
        int parensOffset = fileBody.indexOf("()") + 1;
        caretModel.moveToOffset(parensOffset);
        EditorTestUtil.performTypingAction(editor, '\n');
        String enterBetweenParensFileBody = """
                export class Foo {
                    values = [
                    ];
                    bar(
                    <caret>) {}
                }
                """;
        assertEquals(enterBetweenParensFileBody.replace(CARET, ""), document.getText());
        assertEquals(enterBetweenParensFileBody.indexOf(CARET), caretModel.getOffset());

        // Move into the empty braces and type enter
        fileBody = document.getText();
        int bracesOffset = fileBody.indexOf("{}") + 1;
        caretModel.moveToOffset(bracesOffset);
        EditorTestUtil.performTypingAction(editor, '\n');
        String enterBetweenBracesFileBody = """
                export class Foo {
                    values = [
                    ];
                    bar(
                    ) {
                    <caret>}
                }
                """;
        assertEquals(enterBetweenBracesFileBody.replace(CARET, ""), document.getText());
        assertEquals(enterBetweenBracesFileBody.indexOf(CARET), caretModel.getOffset());
    }

    // Verify LSPImprovedTextMateNestedBracesTypedHandler

    public void testImprovedTextMateNestedBracesDisabledByDefault() {
        String fileBody = """
                function foo() {
                    invokePromise()
                        .then()
                }
                """;
        int initialOffset = fileBody.indexOf(".then(") + ".then(".length();

        myFixture.configureByText(TEST_FILE_NAME, fileBody);
        initializeLanguageServer();

        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();

        CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(initialOffset);

        // Type four open parens and they should be inserted unpaired
        for (int iteration = 1; iteration <= 4; iteration++) {
            EditorTestUtil.performTypingAction(editor, '(');
            assertEquals(fileBody.replace(".then()", ".then(" + StringUtil.repeatSymbol('(', iteration) + ")"), document.getText());
            assertEquals(initialOffset + iteration, caretModel.getOffset());
        }
    }
}
