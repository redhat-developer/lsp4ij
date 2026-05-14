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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link LSPIJUtils#applyEdits(Document, List)} with multiple text edits at the same position.
 * According to LSP spec: "if multiple inserts have the same position, the order in the array
 * defines the order in which the inserted strings appear in the resulting text."
 *
 * This method is used for formatting, where text edits must be sorted by position and
 * same-position edits must preserve array order.
 */
public class LSPIJUtils_applyEdits_SamePositionTest extends BasePlatformTestCase {

    /**
     * Test that multiple insertions at the same position maintain array order.
     */
    public void testMultipleInsertsAtSamePosition() {
        Document document = new DocumentImpl("world");

        List<TextEdit> edits = new ArrayList<>();
        // Insert three strings at position 0:0 in order: "Hello ", ", ", "beautiful "
        // Expected result: "Hello , beautiful world" (in that exact order)
        edits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "Hello "));
        edits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), ", "));
        edits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "beautiful "));

        String result = LSPIJUtils.applyEdits(document, edits);
        assertEquals("Hello , beautiful world", result);
    }

    /**
     * Test multiple insertions at the same position with mixed line/character positions.
     */
    public void testMultipleInsertsAtSamePositionMidLine() {
        Document document = new DocumentImpl("start end");

        List<TextEdit> edits = new ArrayList<>();
        // Insert three strings at position 0:6 (between "start " and "end")
        // In order: "one ", "two ", "three "
        edits.add(new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "one "));
        edits.add(new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "two "));
        edits.add(new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "three "));

        String result = LSPIJUtils.applyEdits(document, edits);
        assertEquals("start one two three end", result);
    }

    /**
     * Test that edits maintain order even when mixed with edits at different positions.
     */
    public void testMixedPositionEditsWithSamePositionInserts() {
        Document document = new DocumentImpl("line1\nline2\nline3\n");

        List<TextEdit> edits = new ArrayList<>();
        // Multiple edits:
        // - Two inserts at 0:0 (should maintain order: "A", "B")
        // - One insert at 1:0
        // - Two inserts at 2:0 (should maintain order: "X", "Y")
        edits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "A"));
        edits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "B"));
        edits.add(new TextEdit(new Range(new Position(1, 0), new Position(1, 0)), "C"));
        edits.add(new TextEdit(new Range(new Position(2, 0), new Position(2, 0)), "X"));
        edits.add(new TextEdit(new Range(new Position(2, 0), new Position(2, 0)), "Y"));

        String result = LSPIJUtils.applyEdits(document, edits);
        assertEquals("ABline1\nCline2\nXYline3\n", result);
    }
}
