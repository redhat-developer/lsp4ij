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
        assertOffset("foo", 999999, 0, 0, 'f');
        assertOffset("foo", 999999, 1, 1, 'o');
        assertOffset("foo", 999999, 2, 2, 'o');
        assertOffset("foo", 999999, 3, 3, null);
    }

    public void testInvalidLineWithTwoLines() {
        assertOffset("foo\nbar", 999999, 0, 4, 'b');
        assertOffset("foo\nbar", 999999, 1, 5, 'a');
        assertOffset("foo\nbar", 999999, 2, 6, 'r');
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

    public void assertOffset(String content, int line, int character, int expectedOffset, Character expectedChar) {
        assertOffset(content, new Position(line, character), expectedOffset, expectedChar);
    }

    public void assertOffset(String content, Position position, int expectedOffset, Character expectedChar) {
        Document document = new DocumentImpl(content);
        int actualOffset = LSPIJUtils.toOffset(position, document);
        assertEquals(expectedOffset, actualOffset);
        if (expectedChar == null) {
            assertTrue(expectedOffset == content.length()  /* offset is at the end of the file */
                    || content.charAt(expectedOffset) == '\n' /* or offset is at the end of line */);
        } else {
            // get the character from the given offset
            Character actualChar = document.getCharsSequence().charAt(expectedOffset);
            assertEquals(expectedChar, actualChar);
        }
    }


}
