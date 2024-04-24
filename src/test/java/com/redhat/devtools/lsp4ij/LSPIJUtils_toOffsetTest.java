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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.Position;

import static com.redhat.devtools.lsp4ij.LSP4IJAssert.assertOffset;
import static com.redhat.devtools.lsp4ij.LSPIJUtils.toOffset;
import static com.redhat.devtools.lsp4ij.LSPIJUtils.toPosition;

/**
 * Tests for {@link LSPIJUtils#toOffset(Position, Document)}.
 */
public class LSPIJUtils_toOffsetTest extends BasePlatformTestCase {

    public void testValidPositionWithOneLine() {
        assertOffset("foo", 0, 0, 0, 'f');
        assertOffset("foo", 0, 1, 1, 'o');
        assertOffset("foo", 0, 2, 2, 'o');
        assertOffset("foo", 0, 3, 3, null);
    }

    public void testValidPositionWithTwoLines() {
        assertOffset("foo\nbar", 0, 0, 0, 'f');
        assertOffset("foo\nbar", 0, 1, 1, 'o');
        assertOffset("foo\nbar", 0, 2, 2, 'o');
        assertOffset("foo\nbar", 0, 3, 3, null);

        assertOffset("foo\nbar", 1, 0, 4, 'b');
        assertOffset("foo\nbar", 1, 1, 5, 'a');
        assertOffset("foo\nbar", 1, 2, 6, 'r');
        assertOffset("foo\nbar", 1, 3, 7, null);
    }

    public void testInvalidLineWithOneLine() {
        assertOffset("foo", 999999, 0, 3, null);
        assertOffset("foo", 999999, 1, 3, null);
        assertOffset("foo", 999999, 2, 3, null);
        assertOffset("foo", 999999, 3, 3, null);
    }

    public void testInvalidLineWithTwoLines() {
        assertOffset("foo\nbar", 999999, 0, 7, null);
        assertOffset("foo\nbar", 999999, 1, 7, null);
        assertOffset("foo\nbar", 999999, 2, 7, null);
        assertOffset("foo\nbar", 999999, 3, 7, null);
    }

    public void testInvalidCharacterWithOneLine() {
        assertOffset("foo", 0, 999999, 3, null);
    }

    public void testInvalidCharacterWithTwoLines() {
        assertOffset("foo\nbar", 0, 999999, 3, null);
        assertOffset("foo\nbar", 1, 999999, 7, null);
    }

    public void testInvalidPositionWithOneLine() {
        assertOffset("foo", 999999, 999999, 3, null);
    }

    public void testInvalidPositionWithTwoLine() {
        assertOffset("foo\nbar", 999999, 999999, 7, null);
    }

    public void test_vscode_EmptyContent() {
        // See https://github.com/microsoft/vscode-languageserver-node/blob/8e625564b531da607859b8cb982abb7cdb2fbe2e/textDocument/src/test/textdocument.test.ts#L18
        String str = "";
        var document = new DocumentImpl(str);
        assertEquals(0, document.getLineCount());
        assertEquals(0, toOffset(new Position(0, 0), document));
        assertEquals(new Position(0, 0), toPosition(0, document));
    }

    public void test_vscode_SingleLine() {
        // See https://github.com/microsoft/vscode-languageserver-node/blob/8e625564b531da607859b8cb982abb7cdb2fbe2e/textDocument/src/test/textdocument.test.ts#L26

        String str = "Hello World";
        var document = new DocumentImpl(str);
        assertEquals(1, document.getLineCount());

        for (int i = 0; i < str.length(); i++) {
            assertEquals(i, toOffset(new Position(0, i), document));
            assertEquals(new Position(0, i), toPosition(i, document));
        }
    }

    public void test_vscode_Multiples_Lines() {
        // See https://github.com/microsoft/vscode-languageserver-node/blob/8e625564b531da607859b8cb982abb7cdb2fbe2e/textDocument/src/test/textdocument.test.ts#L37
        String str = "ABCDE\nFGHIJ\nKLMNO\n";
        var document = new DocumentImpl(str);
        assertEquals(4, document.getLineCount());

        for (int i = 0; i < str.length(); i++) {
            int line = (int) Math.floor(i / 6);
            int column = i % 6;

            assertEquals(i, toOffset(new Position(line, column), document));
            assertEquals(new Position(line, column), toPosition(i, document));
        }

        assertEquals(18, toOffset(new Position(3, 0), document));
        assertEquals(18, toOffset(new Position(3, 1), document));
        assertEquals(new Position(3, 0), toPosition(19, document));
        assertEquals(new Position(3, 0), toPosition(19, document));
    }

    public void test_vscode_StartWithNewLine() {
        // See https://github.com/microsoft/vscode-languageserver-node/blob/8e625564b531da607859b8cb982abb7cdb2fbe2e/textDocument/src/test/textdocument.test.ts#L56
        var document = new DocumentImpl("\nABCDE");
        assertEquals(2, document.getLineCount());
        assertEquals(new Position(0, 0), toPosition(0, document));
        assertEquals(new Position(1, 0), toPosition(1, document));
        assertEquals(new Position(1, 5), toPosition(6, document));
    }

    public void test_vscode_invalidPosition() {
        // See https://github.com/microsoft/vscode-languageserver-node/blob/8e625564b531da607859b8cb982abb7cdb2fbe2e/textDocument/src/test/textdocument.test.ts#L101
        String str = "Hello World";
        var document = new DocumentImpl(str);

        // invalid position
        assertEquals(str.length(), toOffset(new Position(0, str.length()), document));
        assertEquals(str.length(), toOffset(new Position(0, str.length() + 3), document));
        assertEquals(str.length(), toOffset(new Position(2, 3), document));
        assertEquals(0, toOffset(new Position(-1, 3), document));
        assertEquals(0, toOffset(new Position(0, -3), document));
        assertEquals(str.length(), toOffset(new Position(1, -3), document));
    }

}
