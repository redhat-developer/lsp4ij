/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;

/**
 * Tests for {@link LSPIJUtils#applyEdits(Document, List)} with text edits whose new text has '\r\n' or '\r' line
 * separators. The text it returns is for a document, which accepts '\n' line separators only.
 */
public class LSPIJUtils_applyEdits_LineSeparatorsTest extends BasePlatformTestCase {

    private static final String TEXT = "a=1;\nb=2;\n";

    public void testCrlfBecomesLf() {
        List<TextEdit> edits = List.of(
                new TextEdit(new Range(new Position(0, 0), new Position(2, 0)), "a = 1;\r\nb = 2;\r\n"));
        assertEquals("a = 1;\nb = 2;\n", LSPIJUtils.applyEdits(new DocumentImpl(TEXT), edits));
    }

    public void testACrInsertedBeforeALfIsRemoved() {
        // A server which diffs its CRLF lines against the document inserts a '\r' at the end of each line
        List<TextEdit> edits = List.of(
                new TextEdit(new Range(new Position(0, 4), new Position(0, 4)), "\r"),
                new TextEdit(new Range(new Position(1, 4), new Position(1, 4)), "\r"));
        assertEquals(TEXT, LSPIJUtils.applyEdits(new DocumentImpl(TEXT), edits));
    }

    public void testCrBecomesLf() {
        // Prettier with 'endOfLine: "cr"' ends its lines with '\r'
        List<TextEdit> edits = List.of(
                new TextEdit(new Range(new Position(0, 0), new Position(2, 0)), "a = 1;\rb = 2;\r"));
        assertEquals("a = 1;\nb = 2;\n", LSPIJUtils.applyEdits(new DocumentImpl(TEXT), edits));
    }
}
