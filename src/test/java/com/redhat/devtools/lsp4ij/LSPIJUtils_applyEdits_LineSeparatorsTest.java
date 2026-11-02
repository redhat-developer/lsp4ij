/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;

/**
 * Tests for {@link LSPIJUtils#applyEdits(Document, List)}, which returns the edited text, and
 * {@link LSPIJUtils#applyEdits(Editor, Document, List)}, which edits the document one edit at a time, with text edits
 * whose new text has '\r\n' or '\r' line separators. A document accepts '\n' line separators only.
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

    public void testCrlfBecomesLfInTheDocument() {
        List<TextEdit> edits = List.of(
                new TextEdit(new Range(new Position(0, 0), new Position(2, 0)), "a = 1;\r\nb = 2;\r\n"));
        assertEquals("a = 1;\nb = 2;\n", applyEditsToTheDocument(edits));
    }

    public void testACrBeforeALfOfTheDocumentIsRemoved() {
        // The '\r' which ends each edit and the '\n' after the edit are one line separator
        List<TextEdit> edits = List.of(
                new TextEdit(new Range(new Position(0, 4), new Position(0, 4)), "\r"),
                new TextEdit(new Range(new Position(1, 0), new Position(1, 4)), "b = 2;\r"));
        assertEquals("a=1;\nb = 2;\n", applyEditsToTheDocument(edits));
    }

    public void testCrBecomesLfInTheDocument() {
        // Prettier with 'endOfLine: "cr"' ends its lines with '\r'
        List<TextEdit> edits = List.of(
                new TextEdit(new Range(new Position(0, 0), new Position(2, 0)), "a = 1;\rb = 2;\r"));
        assertEquals("a = 1;\nb = 2;\n", applyEditsToTheDocument(edits));
    }

    public void testACrAndALfOfTwoEditsAreOneLineSeparator() {
        // A server may split a "\r\n" between two edits: the "\r" ends one, and the "\n" starts the next
        List<TextEdit> edits = List.of(
                new TextEdit(new Range(new Position(0, 0), new Position(0, 2)), "a\r"),
                new TextEdit(new Range(new Position(0, 2), new Position(0, 4)), "\n1;"));
        assertEquals("a\n1;\nb=2;\n", applyEditsToTheDocument(edits));
        assertEquals("a\n1;\nb=2;\n", LSPIJUtils.applyEdits(new DocumentImpl(TEXT), edits));
    }

    public void testACrBeforeALfWhichAnotherEditReplacesIsALineSeparator() {
        // The "\n" after the first edit is replaced, so its "\r" is a line separator of its own
        List<TextEdit> edits = List.of(
                new TextEdit(new Range(new Position(0, 0), new Position(0, 4)), "a = 1;\r"),
                new TextEdit(new Range(new Position(0, 4), new Position(1, 0)), "c = 3;"));
        assertEquals("a = 1;\nc = 3;b=2;\n", applyEditsToTheDocument(edits));
        assertEquals("a = 1;\nc = 3;b=2;\n", LSPIJUtils.applyEdits(new DocumentImpl(TEXT), edits));
    }

    public void testACrBeforeACrlfOfAnotherEditIsTwoLineSeparators() {
        // "\r" followed by "\r\n" is a lone CR and a CRLF
        List<TextEdit> edits = List.of(
                new TextEdit(new Range(new Position(0, 0), new Position(0, 4)), "a = 1;\r"),
                new TextEdit(new Range(new Position(0, 4), new Position(1, 0)), "\r\n"));
        assertEquals("a = 1;\n\nb=2;\n", applyEditsToTheDocument(edits));
        assertEquals("a = 1;\n\nb=2;\n", LSPIJUtils.applyEdits(new DocumentImpl(TEXT), edits));
    }

    private String applyEditsToTheDocument(List<TextEdit> edits) {
        Document document = new DocumentImpl(TEXT);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> LSPIJUtils.applyEdits(null, document, edits));
        return document.getText();
    }
}
