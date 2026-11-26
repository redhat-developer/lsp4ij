/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;

/**
 * Tests how a completion item with an {@code InsertReplaceEdit} (LSP 3.16) is applied: Tab replaces the whole word
 * with its {@code replace} range, and any other completion char inserts at the caret with its {@code insert} range.
 * An item with a plain {@code TextEdit}, which has only one range, is applied as the language server gave it.
 */
public class CompletionInsertReplaceTest extends LSPCompletionFixtureTestCase {

    // 'foo|Bar': insert covers 'foo' (columns 0 to 3), replace covers the whole word 'fooBar' (columns 0 to 6)
    // language=json
    private static final String INSERT_REPLACE_JSON = """
            [
              {
                "label": "fooBaz",
                "textEdit": {
                  "newText": "fooBaz",
                  "insert":  { "start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 3} },
                  "replace": { "start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 6} }
                }
              }
            ]""";

    // 'foo|Bar': a plain TextEdit has only one range, which stops at the caret
    // language=json
    private static final String TEXT_EDIT_JSON = """
            [
              {
                "label": "fooBaz",
                "textEdit": {
                  "newText": "fooBaz",
                  "range": { "start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 3} }
                }
              }
            ]""";

    // The list is asked for at 'f|Bar', before 'oo' is typed: insert covers 'f' (columns 0 to 1), replace covers the
    // whole word 'fBar' (columns 0 to 4)
    // language=json
    private static final String INSERT_REPLACE_BEFORE_TYPING_JSON = """
            [
              {
                "label": "fooBaz",
                "textEdit": {
                  "newText": "fooBaz",
                  "insert":  { "start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 1} },
                  "replace": { "start": {"line": 0, "character": 0}, "end": {"line": 0, "character": 4} }
                }
              }
            ]""";

    public CompletionInsertReplaceTest() {
        super("*.ts");
    }

    public void testEnterInsertsAndKeepsTheRestOfTheWord() {
        assertCompletion("test.ts", "foo<caret>Bar", INSERT_REPLACE_JSON, "fooBaz");
        assertApplyCompletionItem(0, "fooBaz<caret>Bar");
    }

    public void testTabReplacesTheWholeWord() {
        assertCompletion("test.ts", "foo<caret>Bar", INSERT_REPLACE_JSON, "fooBaz");
        acceptWithTab();
        assertEquals("fooBaz", myFixture.getEditor().getDocument().getText());
        assertEquals("fooBaz".length(), myFixture.getEditor().getCaretModel().getOffset());
    }

    public void testTabDoesNotWidenAPlainTextEdit() {
        assertCompletion("test.ts", "foo<caret>Bar", TEXT_EDIT_JSON, "fooBaz");
        acceptWithTab();
        assertEquals("fooBazBar", myFixture.getEditor().getDocument().getText());
    }

    public void testTabReplacesTheWholeWordWhenTheCaretMovedAfterTheListWasAskedFor() {
        assertCompletion("test.ts", "f<caret>Bar", INSERT_REPLACE_BEFORE_TYPING_JSON, "fooBaz");
        myFixture.type("oo");
        assertNotNull("the lookup is still shown", myFixture.getLookup());
        acceptWithTab();
        assertEquals("fooBaz", myFixture.getEditor().getDocument().getText());
    }

    public void testEnterInsertsWhenTheCaretMovedAfterTheListWasAskedFor() {
        assertCompletion("test.ts", "f<caret>Bar", INSERT_REPLACE_BEFORE_TYPING_JSON, "fooBaz");
        myFixture.type("oo");
        assertNotNull("the lookup is still shown", myFixture.getLookup());
        assertApplyCompletionItem(0, "fooBaz<caret>Bar");
    }

    private void acceptWithTab() {
        myFixture.selectItem(myFixture.getLookupElements()[0], Lookup.REPLACE_SELECT_CHAR);
    }
}
