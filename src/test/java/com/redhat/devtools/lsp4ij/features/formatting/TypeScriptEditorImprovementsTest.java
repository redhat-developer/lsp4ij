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
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Tests for LSPEditorImprovementsTypedHandler, LSPEditorImprovementsBackspaceHandler, and LSPEditorImprovementsEnterBetweenBracesHandler.
 */
public class TypeScriptEditorImprovementsTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.ts";
    private static final char BACKSLASH = '\\';
    private static final String CARET = "<caret>";

    public TypeScriptEditorImprovementsTest() {
        super("*.ts");
    }

    @Override
    protected void tearDown() throws Exception {
        // Restore the default code style
        Editor editor = myFixture.getEditor();
        if (editor != null) {
            CodeStyle.getSettings(editor).getIndentOptions().USE_TAB_CHARACTER = false;
        }

        super.tearDown();
    }

    private void initializeLanguageServer() {
        try {
            PsiFile file = myFixture.getFile();
            LanguageServiceAccessor.getInstance(file.getProject())
                    .getLanguageServers(file.getVirtualFile(), null, null)
                    .get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    // These tests exercise both LSPEditorImprovementsTypedHandler.handleNestedQuote() and LSPEditorImprovementsBackspaceHandler

    public void testNestedQuotesInSingleQuotedStringLiteral() {
        testNestedQuotes('\'', '"');
        testNestedQuotes('\'', '`');
    }

    public void testNestedQuotesInDoubleQuotedStringLiteral() {
        testNestedQuotes('"', '\'');
        testNestedQuotes('"', '`');
    }

    public void testNestedQuotesInBacktickQuotedStringLiteral() {
        testNestedQuotes('`', '\'');
        testNestedQuotes('`', '"');
    }

    private void testNestedQuotes(char outerQuote, char innerQuote) {
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

        // Return to the interior of the string literal and type an inner quote character which should be inserted unpaired
        caretModel.moveToOffset(initialOffset + 1);
        EditorTestUtil.performTypingAction(editor, innerQuote);
        assertEquals("console.log(" + outerQuote + innerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 2, caretModel.getOffset());

        // Typing another inner quote character should also result in unpaired insertion
        EditorTestUtil.performTypingAction(editor, innerQuote);
        assertEquals("console.log(" + outerQuote + innerQuote + innerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 3, caretModel.getOffset());

        // Typing a backspace should remove the second inner quote only
        EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE);
        assertEquals("console.log(" + outerQuote + innerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 2, caretModel.getOffset());

        // And another should leave us with the empty outer quote
        EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE);
        assertEquals("console.log(" + outerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());

        // Now insert an escaped outer quote which should also not be paired
        EditorTestUtil.performTypingAction(editor, BACKSLASH);
        EditorTestUtil.performTypingAction(editor, outerQuote);
        assertEquals("console.log(" + outerQuote + BACKSLASH + outerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 3, caretModel.getOffset());

        // Remove those and make sure that nothing else is removed
        EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE);
        assertEquals("console.log(" + outerQuote + BACKSLASH + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 2, caretModel.getOffset());
        EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE);
        assertEquals("console.log(" + outerQuote + outerQuote + ");", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());

        // And finally remove the original outer quote and the quote pair should be removed
        EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE);
        assertEquals("console.log();", document.getText());
        assertEquals(initialOffset, caretModel.getOffset());
    }

    // These exercise LSPEditorImprovementsTypedHandler.handleStatementTerminator()

    public void testStatementTerminator() {
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
        assertEquals("console.log();", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());

        // Move back to just before the statement terminator and type it again; it should only advance the caret
        caretModel.moveToOffset(initialOffset);
        EditorTestUtil.performTypingAction(editor, ';');
        assertEquals("console.log();", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());
    }

    public void testStatementTerminatorInStringLiteral() {
        String fileBody = "console.log('foobar');";
        int initialOffset = fileBody.indexOf("bar");

        myFixture.configureByText(TEST_FILE_NAME, fileBody);
        initializeLanguageServer();

        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();

        CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(initialOffset);

        // Typing a statement terminator in a string literal should insert the statement terminator character
        EditorTestUtil.performTypingAction(editor, ';');
        assertEquals("console.log('foo;bar');", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());

        // Move back to just before the statement terminator and type it again; it should insert again
        caretModel.moveToOffset(initialOffset);
        EditorTestUtil.performTypingAction(editor, ';');
        assertEquals("console.log('foo;;bar');", document.getText());
        assertEquals(initialOffset + 1, caretModel.getOffset());
    }

    // This exercises LSPEditorImprovementsEnterBetweenBracesHandler

    public void testEnterBetweenBraces_spaces() {
        testEnterBetweenSpaces(false);
    }

    public void testEnterBetweenBraces_tabs() {
        testEnterBetweenSpaces(true);
    }

    private void testEnterBetweenSpaces(boolean useTabCharacter) {
        String fileBody = adjustIndent(
                """
                export class Foo {
                    values = [];
                    bar() {}
                }
                """,
                useTabCharacter
        );

        myFixture.configureByText(TEST_FILE_NAME, fileBody);
        initializeLanguageServer();

        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();
        CaretModel caretModel = editor.getCaretModel();

        // Use the appropriate indent
        CodeStyle.getSettings(editor).getIndentOptions().USE_TAB_CHARACTER = useTabCharacter;

        // Move into the empty brackets and type enter
        int bracketsOffset = fileBody.indexOf("[]") + 1;
        caretModel.moveToOffset(bracketsOffset);
        EditorTestUtil.performTypingAction(editor, '\n');
        String enterBetweenBracketsFileBody = adjustIndent(
                """
                export class Foo {
                    values = [
                        <caret>
                    ];
                    bar() {}
                }
                """,
                useTabCharacter
        );
        assertEquals(enterBetweenBracketsFileBody.replace(CARET, ""), document.getText());
        assertEquals(enterBetweenBracketsFileBody.indexOf(CARET), caretModel.getOffset());

        // Move into the empty parens and type enter
        fileBody = document.getText();
        int parensOffset = fileBody.indexOf("()") + 1;
        caretModel.moveToOffset(parensOffset);
        EditorTestUtil.performTypingAction(editor, '\n');
        String enterBetweenParensFileBody = adjustIndent(
                """
                export class Foo {
                    values = [
                       \s
                    ];
                    bar(
                        <caret>
                    ) {}
                }
                """,
                useTabCharacter
        );
        assertEquals(enterBetweenParensFileBody.replace(CARET, ""), document.getText());
        assertEquals(enterBetweenParensFileBody.indexOf(CARET), caretModel.getOffset());

        // Move into the empty braces and type enter
        fileBody = document.getText();
        int bracesOffset = fileBody.indexOf("{}") + 1;
        caretModel.moveToOffset(bracesOffset);
        EditorTestUtil.performTypingAction(editor, '\n');
        String enterBetweenBracesFileBody = adjustIndent(
                """
                export class Foo {
                    values = [
                       \s
                    ];
                    bar(
                       \s
                    ) {
                        <caret>
                    }
                }
                """,
                useTabCharacter
        );
        assertEquals(enterBetweenBracesFileBody.replace(CARET, ""), document.getText());
        assertEquals(enterBetweenBracesFileBody.indexOf(CARET), caretModel.getOffset());
    }

    @NotNull
    private static String adjustIndent(@NotNull String fileBody, boolean useTabCharacter) {
        return useTabCharacter ? fileBody.replace("    ", "\t") : fileBody;
    }
}
