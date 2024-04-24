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

import static com.redhat.devtools.lsp4ij.LSPIJUtils.toPosition;

/**
 * Tests for {@link LSPIJUtils#toPosition(int, Document)}.
 */
public class LSPIJUtils_toPositionTest extends BasePlatformTestCase {

    public void testSingleLine() {
        assertPosition("|foo", 0, 0);
        assertPosition("f|oo", 0, 1);
        assertPosition("fo|o", 0, 2);
        assertPosition("foo|", 0, 3);
    }

    public void testMultiLine() {
        assertPosition("bar\n|foo", 1, 0);
        assertPosition("bar\nf|oo", 1, 1);
        assertPosition("bar\nfo|o", 1, 2);
        assertPosition("bar\nfoo|", 1, 3);
    }

    public void test_vscode_invalidOffset() {
        // See https://github.com/microsoft/vscode-languageserver-node/blob/8e625564b531da607859b8cb982abb7cdb2fbe2e/textDocument/src/test/textdocument.test.ts#L109
        String str = "Hello World";
        var document = new DocumentImpl(str);

        // invalid offsets
        assertEquals(toPosition(-1, document), new Position(0, 0));
        assertEquals(toPosition(str.length(), document), new Position(0, str.length()));
        assertEquals(toPosition(str.length() + 3, document), new Position(0, str.length()));
    }

    private static void assertPosition(String contentWithOffset, int expectedLine, int expectedCharacter) {
        TextAndOffset textAndOffset = new TextAndOffset(contentWithOffset);
        assertPosition(textAndOffset.getContent(), textAndOffset.getOffset(), expectedLine, expectedCharacter);
    }

    private static void assertPosition(String content, int offset, int expectedLine, int expectedCharacter) {
        Document document = new DocumentImpl(content);
        Position expectedPosition = new Position(expectedLine, expectedCharacter);
        Position actualPosition = toPosition(offset, document);
        assertEquals(expectedPosition, actualPosition);
    }
}
