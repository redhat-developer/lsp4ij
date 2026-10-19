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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A workspace edit saves the changed documents that are not open in an editor, since a language
 * server reads those from disk, and leaves other documents unsaved.
 */
public class WorkspaceEditSaveTest extends LSPCodeInsightFixtureTestCase {

    private static final String OLD_TEXT = "class Box";
    private static final String NEW_TEXT = "class Parcel";

    public WorkspaceEditSaveTest() {
        // the files are local files; no language server is connected to them
        super("*.unused");
    }

    public void testChangesToAFileWithoutAnEditorAreSaved() throws IOException {
        VirtualFile file = myFixture.addFileToProject("closed.txt", OLD_TEXT).getVirtualFile();

        apply(new WorkspaceEdit(Map.of(LSPIJUtils.toUri(file).toString(), List.of(renameClass()))));

        assertSaved(file);
    }

    public void testDocumentChangesToAFileWithoutAnEditorAreSaved() throws IOException {
        VirtualFile file = myFixture.addFileToProject("closed.txt", OLD_TEXT).getVirtualFile();

        apply(new WorkspaceEdit(List.of(Either.forLeft(new TextDocumentEdit(
                new VersionedTextDocumentIdentifier(LSPIJUtils.toUri(file).toString(), null),
                List.of(Either.forLeft(renameClass())))))));

        assertSaved(file);
    }

    public void testChangesToAFileInAnEditorAreNotSaved() throws IOException {
        VirtualFile file = myFixture.configureByText("open.txt", OLD_TEXT).getVirtualFile();

        apply(new WorkspaceEdit(Map.of(LSPIJUtils.toUri(file).toString(), List.of(renameClass()))));

        assertEquals(NEW_TEXT, document(file).getText());
        assertUnsaved(file);
    }

    public void testUnsavedChangesFromBeforeTheEditAreNotSaved() throws IOException {
        VirtualFile file = myFixture.addFileToProject("draft.txt", OLD_TEXT + " // draft").getVirtualFile();
        Document document = document(file);
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                () -> document.setText(OLD_TEXT + " // unsaved draft"));

        apply(new WorkspaceEdit(Map.of(LSPIJUtils.toUri(file).toString(), List.of(renameClass()))));

        assertEquals(NEW_TEXT + " // unsaved draft", document.getText());
        assertUnsaved(file);
        assertEquals(OLD_TEXT + " // draft", VfsUtilCore.loadText(file));
    }

    public void testChangesAreNotSavedWhenSavingIsNotAllowed() throws IOException {
        VirtualFile file = myFixture.addFileToProject("closed.txt", OLD_TEXT).getVirtualFile();

        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> LSPIJUtils.applyWorkspaceEdit(
                new WorkspaceEdit(Map.of(LSPIJUtils.toUri(file).toString(), List.of(renameClass()))), null, false));

        assertEquals(NEW_TEXT, document(file).getText());
        assertUnsaved(file);
    }

    private void apply(WorkspaceEdit edit) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> LSPIJUtils.applyWorkspaceEdit(edit));
    }

    /** Replaces the class declaration at the start of the file. */
    private static TextEdit renameClass() {
        return new TextEdit(new Range(new Position(0, 0), new Position(0, OLD_TEXT.length())), NEW_TEXT);
    }

    private static Document document(VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        return document;
    }

    private static void assertSaved(VirtualFile file) throws IOException {
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document(file)));
        assertEquals(NEW_TEXT, VfsUtilCore.loadText(file));
    }

    private static void assertUnsaved(VirtualFile file) throws IOException {
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document(file)));
        assertEquals(OLD_TEXT, VfsUtilCore.loadText(file).substring(0, OLD_TEXT.length()));
    }
}
