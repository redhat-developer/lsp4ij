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
 * Tests for {@link LSPIJUtils#applyEdits(Editor, Document, List)}, which applies the edits to the document one at a
 * time, with multiple text edits at the same position. According to LSP spec: "if multiple inserts have the same
 * position, the order in the array defines the order in which the inserted strings appear in the resulting text."
 * {@link LSPIJUtils_applyEdits_SamePositionTest} has the same first three tests for
 * {@link LSPIJUtils#applyEdits(Document, List)}.
 */
public class LSPIJUtils_applyEdits_SamePositionInEditorTest extends BasePlatformTestCase {

    public void testMultipleInsertsAtSamePosition() {
        assertEquals("Hello , beautiful world", applyEdits("world", List.of(
                insert(0, 0, "Hello "),
                insert(0, 0, ", "),
                insert(0, 0, "beautiful "))));
    }

    public void testMultipleInsertsAtSamePositionMidLine() {
        assertEquals("start one two three end", applyEdits("start end", List.of(
                insert(0, 6, "one "),
                insert(0, 6, "two "),
                insert(0, 6, "three "))));
    }

    public void testMixedPositionEditsWithSamePositionInserts() {
        assertEquals("ABline1\nCline2\nXYline3\n", applyEdits("line1\nline2\nline3\n", List.of(
                insert(0, 0, "A"),
                insert(0, 0, "B"),
                insert(1, 0, "C"),
                insert(2, 0, "X"),
                insert(2, 0, "Y"))));
    }

    public void testInsertsBeforeAReplaceAtTheSamePosition() {
        // Any number of inserts followed by a single replace may have the same start position
        assertEquals("let 12y = 1;", applyEdits("let x = 1;", List.of(
                insert(0, 4, "1"),
                insert(0, 4, "2"),
                replace(0, 4, 5, "y"))));
    }

    public void testInsertsAfterAReplaceWhichEndsAtTheirPosition() {
        assertEquals("let y12 = 1;", applyEdits("let x = 1;", List.of(
                replace(0, 4, 5, "y"),
                insert(0, 5, "1"),
                insert(0, 5, "2"))));
    }

    public void testInsertsAroundADeletionWhichEndsAtTheirPosition() {
        assertEquals("let 12 = 1;", applyEdits("let x = 1;", List.of(
                insert(0, 5, "1"),
                replace(0, 4, 5, ""),
                insert(0, 5, "2"))));
    }

    public void testInsertsAtTheCaretMoveTheCaretAfterThem() {
        myFixture.configureByText("test.txt", "<caret>world");
        Editor editor = myFixture.getEditor();
        List<TextEdit> edits = List.of(
                insert(0, 0, "Hello "),
                insert(0, 0, ", "),
                insert(0, 0, "beautiful "));
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> LSPIJUtils.applyEdits(editor, editor.getDocument(), edits));
        assertEquals("Hello , beautiful world", editor.getDocument().getText());
        assertEquals("Hello , beautiful ".length(), editor.getCaretModel().getOffset());
    }

    private String applyEdits(String text, List<TextEdit> edits) {
        Document document = new DocumentImpl(text);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> LSPIJUtils.applyEdits(null, document, edits));
        return document.getText();
    }

    private static TextEdit insert(int line, int character, String newText) {
        return replace(line, character, character, newText);
    }

    private static TextEdit replace(int line, int startCharacter, int endCharacter, String newText) {
        return new TextEdit(new Range(new Position(line, startCharacter), new Position(line, endCharacter)), newText);
    }
}
