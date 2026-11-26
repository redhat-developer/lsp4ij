/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.CreateFileOptions;
import org.eclipse.lsp4j.DeleteFile;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exercises document and connection changes between capture and validation.
 * Each transition is explicit; the waits only guard against hangs.
 */
public class WorkspaceEditSnapshotTest extends LSPCodeInsightFixtureTestCase {

    public WorkspaceEditSnapshotTest() {
        super("*.foo");
    }

    private LanguageServerItem open() throws Exception {
        myFixture.configureByText("test.foo", "before");
        return connect(myFixture.getFile());
    }

    private LanguageServerItem connect(PsiFile file) throws Exception {
        return LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS).get(0);
    }

    private String uri(LanguageServerItem server, PsiFile file) {
        return server.getServerWrapper().toUri(file.getVirtualFile()).toString();
    }

    private static TextEdit replacement() {
        return new TextEdit(new Range(new Position(0, 0), new Position(0, 6)), "after");
    }

    private static WorkspaceEdit unversioned(String uri) {
        return new WorkspaceEdit(Map.of(uri, List.of(replacement())));
    }

    private static WorkspaceEdit versioned(String uri, int version) {
        return new WorkspaceEdit(List.of(Either.forLeft(new TextDocumentEdit(
                new VersionedTextDocumentIdentifier(uri, version), List.of(Either.forLeft(replacement()))))));
    }

    private String validate(WorkspaceEditSnapshot snapshot, WorkspaceEdit edit) {
        return WriteCommandAction.writeCommandAction(myFixture.getProject())
                .compute(() -> snapshot.validate(edit));
    }

    /**
     * Waits until the notifications queued so far, as didOpen and didChange, have been dispatched.
     */
    private static void dispatched(LanguageServerItem server) throws Exception {
        server.getServerWrapper().sendNotification(ls -> ls).get(30, TimeUnit.SECONDS);
    }

    /**
     * Holds back the notifications queued after this call, such as didChange, until the returned latch is released.
     */
    private static CountDownLatch holdNotifications(LanguageServerItem server) {
        var release = new CountDownLatch(1);
        server.getServerWrapper().sendNotification(ls -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ls;
        });
        return release;
    }

    private void type(String text) {
        type(myFixture.getEditor().getDocument(), text);
    }

    private void type(Document document, String text) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> document.setText(text));
    }

    /**
     * Undoes the last change in the test's editor, as Ctrl+Z does, which gives the document back the modification
     * stamp of its text before that change.
     */
    private void undo() {
        var undoManager = UndoManager.getInstance(myFixture.getProject());
        var editor = TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
        assertTrue("the change must be undoable in the editor", undoManager.isUndoAvailable(editor));
        undoManager.undo(editor);
    }

    private PsiFile psiFile(VirtualFile file) {
        return PsiManager.getInstance(myFixture.getProject()).findFile(file);
    }

    private String opened(String uri) {
        return LanguageServerBundle.message("lsp.workspace.edit.document.opened", uri);
    }

    public void testUnchangedDocumentIsCurrent() throws Exception {
        var server = open();
        var snapshot = WorkspaceEditSnapshot.capture(server);

        assertNull(validate(snapshot, unversioned(uri(server, myFixture.getFile()))));
    }

    public void testAPlaceholderVersionAppliesToTheTextTheServerHad() throws Exception {
        var server = open();
        dispatched(server);
        var snapshot = WorkspaceEditSnapshot.capture(server);
        String uri = uri(server, myFixture.getFile());
        int version = server.getServerWrapper().getOpenedDocument(URI.create(uri)).getSynchronizer().getVersion();
        assertTrue("LSP4IJ numbers document versions from 1", version > 0);

        // The edits of intellij-quarkus and Liberty Tools name their document with version 0
        assertNull(validate(snapshot, versioned(uri, 0)));
        assertNull(validate(snapshot, versioned(uri, -1)));
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.version.changed", uri),
                validate(snapshot, versioned(uri, version + 1)));

        // As for an unversioned edit, the document must not have changed since the request
        type("changed");
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.document.changed", uri),
                validate(snapshot, versioned(uri, 0)));
    }

    public void testAPlaceholderVersionIsRefusedForTextNotYetSent() throws Exception {
        var server = open();
        String uri = uri(server, myFixture.getFile());
        // The typing below has not reached the server at capture, as for a request, such as
        // codeAction/resolve, that does not wait for the document's changes
        var release = holdNotifications(server);
        WorkspaceEditSnapshot snapshot;
        try {
            type("changed");
            snapshot = WorkspaceEditSnapshot.capture(server);
        } finally {
            release.countDown();
        }
        dispatched(server);

        // The server may have answered for the text before the typing, and version 0 does not say
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.version.changed", uri),
                validate(snapshot, versioned(uri, 0)));
        // Captured once the typing has been sent, the same edit applies
        assertNull(validate(WorkspaceEditSnapshot.capture(server), versioned(uri, 0)));
    }

    public void testCancellingAFlushDoesNotMakeTheTextLookSent() throws Exception {
        var server = open();
        String uri = uri(server, myFixture.getFile());
        var synchronizer = server.getServerWrapper().getOpenedDocument(URI.create(uri)).getSynchronizer();
        var release = holdNotifications(server);
        WorkspaceEditSnapshot snapshot;
        try {
            type("changed");
            // a caller gives up waiting for the typing to be dispatched
            assertTrue(synchronizer.flushPendingChanges().cancel(true));
            snapshot = WorkspaceEditSnapshot.capture(server);
        } finally {
            release.countDown();
        }
        dispatched(server);

        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.version.changed", uri),
                validate(snapshot, versioned(uri, 0)));
    }

    public void testAnIncomingEditWithAPlaceholderVersionIsRefused() throws Exception {
        var server = open();
        dispatched(server);
        String uri = uri(server, myFixture.getFile());
        // workspace/applyEdit follows no request of ours, so its version 0 can describe text that a change crossed,
        // even when the text had been sent at receipt
        var snapshot = WorkspaceEditSnapshot.capture(server.getServerWrapper(), () -> true);

        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.version.changed", uri),
                validate(snapshot, versioned(uri, 0)));
    }

    public void testChangedAndRestoredTextStillInvalidatesRequest() throws Exception {
        var server = open();
        var snapshot = WorkspaceEditSnapshot.capture(server);
        String uri = uri(server, myFixture.getFile());
        // The text matches again, but the server prepared the edit before both changes.
        type("changed");
        type("before");

        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.document.changed", uri),
                validate(snapshot, unversioned(uri)));
    }

    public void testATargetChangedAndUndoneSinceCaptureIsRefused() throws Exception {
        var server = open();
        dispatched(server);
        String uri = uri(server, myFixture.getFile());
        var document = myFixture.getEditor().getDocument();
        var synchronizer = server.getServerWrapper().getOpenedDocument(URI.create(uri)).getSynchronizer();
        long stamp = document.getModificationStamp();
        int version = synchronizer.getVersion();
        // as while a rename, a codeAction/resolve or an incoming workspace/applyEdit is pending
        var request = WorkspaceEditSnapshot.capture(server);
        var incoming = WorkspaceEditSnapshot.capture(server.getServerWrapper(), () -> true);

        // The server is sent the changed text, which it may answer for, and then the text before it
        type("changed");
        dispatched(server);
        undo();
        dispatched(server);
        assertEquals("before", document.getText());
        assertEquals("the undo must give back the stamp of the text before the change", stamp, document.getModificationStamp());
        assertTrue("the server must have been sent the change and its undo", synchronizer.getVersion() > version);

        String changed = LanguageServerBundle.message("lsp.workspace.edit.document.changed", uri);
        assertEquals(changed, validate(request, unversioned(uri)));
        assertEquals(changed, validate(request, versioned(uri, 0)));
        assertEquals(changed, validate(incoming, unversioned(uri)));
        // Captured after the undo, the same edits apply
        assertNull(validate(WorkspaceEditSnapshot.capture(server), unversioned(uri)));
        assertNull(validate(WorkspaceEditSnapshot.capture(server), versioned(uri, 0)));
    }

    public void testAChangeSentBeforeCaptureStillApplies() throws Exception {
        var server = open();
        String uri = uri(server, myFixture.getFile());
        type("changed");
        dispatched(server);
        var request = WorkspaceEditSnapshot.capture(server);
        var incoming = WorkspaceEditSnapshot.capture(server.getServerWrapper(), () -> true);
        int version = server.getServerWrapper().getOpenedDocument(URI.create(uri)).getSynchronizer().getVersion();

        assertNull(validate(request, unversioned(uri)));
        assertNull(validate(request, versioned(uri, version)));
        assertNull(validate(request, versioned(uri, 0)));
        assertNull(validate(incoming, unversioned(uri)));
        assertNull(validate(incoming, versioned(uri, version)));
    }

    public void testASaveSinceCaptureStillApplies() throws Exception {
        var server = open();
        String uri = uri(server, myFixture.getFile());
        var document = myFixture.getEditor().getDocument();
        // Unsaved changes, sent before capture, which the save writes to the file
        type("changed");
        dispatched(server);
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
        var request = WorkspaceEditSnapshot.capture(server);
        var incoming = WorkspaceEditSnapshot.capture(server.getServerWrapper(), () -> true);
        int version = server.getServerWrapper().getOpenedDocument(URI.create(uri)).getSynchronizer().getVersion();
        MockLanguageServer.INSTANCE.getNotifications().clear();

        // as an autosave when the IDE loses the focus while codeAction/resolve is pending
        FileDocumentManager.getInstance().saveDocument(document);
        assertFalse("the document must be saved", FileDocumentManager.getInstance().isDocumentUnsaved(document));
        PlatformTestUtil.waitWithEventsDispatching("the server must be sent the didSave of the save",
                () -> MockLanguageServer.INSTANCE.getNotifications().contains("didSave"), 30);

        assertNull(validate(request, unversioned(uri)));
        assertNull(validate(request, versioned(uri, version)));
        assertNull(validate(request, versioned(uri, 0)));
        assertNull(validate(incoming, unversioned(uri)));
        assertNull(validate(incoming, versioned(uri, version)));
    }

    public void testATargetWhoseDidOpenIsSentAfterCaptureStillApplies() throws Exception {
        var server = open();
        var wrapper = server.getServerWrapper();
        var file = myFixture.addFileToProject("other.foo", "before").getVirtualFile();
        var document = FileDocumentManager.getInstance().getDocument(file);
        // Connected as from a write action, which doesn't wait for the didOpen: the next request sends it
        wrapper.connect(file, wrapper.createFileConnectionInfo(file, document, false)).get(30, TimeUnit.SECONDS);
        URI uri = wrapper.toUri(file);
        var synchronizer = wrapper.getOpenedDocument(uri).getSynchronizer();
        assertEquals("the didOpen must not be sent before capture", 0, synchronizer.getVersion());
        var request = WorkspaceEditSnapshot.capture(server);
        var incoming = WorkspaceEditSnapshot.capture(wrapper, () -> true);

        // The didOpen gives the document a version, though not its text, which the server read from the file before
        synchronizer.flushPendingChanges().get(30, TimeUnit.SECONDS);
        int version = synchronizer.getVersion();
        assertTrue("the didOpen must give the document a version", version > 0);

        assertNull(validate(request, unversioned(uri.toString())));
        assertNull(validate(request, versioned(uri.toString(), version)));
        assertNull(validate(incoming, unversioned(uri.toString())));
    }

    public void testReopenDoesNotReuseRequestOwnership() throws Exception {
        var server = open();
        var snapshot = WorkspaceEditSnapshot.capture(server);
        String uri = uri(server, myFixture.getFile());
        server.getServerWrapper().disconnect(URI.create(uri), false);
        connect(myFixture.getFile());

        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.document.changed", uri),
                validate(snapshot, unversioned(uri)));
    }

    public void testTargetOpenedAfterRequestAppliesWhileItsTextIsUnchanged() throws Exception {
        var server = open();
        var other = myFixture.addFileToProject("other.foo", "before");
        String uri = uri(server, other);
        assertNull(server.getServerWrapper().getOpenedDocument(URI.create(uri)));
        var request = WorkspaceEditSnapshot.capture(server);
        var incoming = WorkspaceEditSnapshot.capture(server.getServerWrapper(), () -> true);
        // as when the user opens the file in an editor while the reply is pending
        connect(other);
        int version = server.getServerWrapper().getOpenedDocument(URI.create(uri)).getSynchronizer().getVersion();

        // The server read the file until it was opened, and was sent the same text then
        assertNull(validate(request, unversioned(uri)));
        assertNull(validate(request, versioned(uri, version)));
        assertNull(validate(request, versioned(uri, 0)));
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.version.changed", uri),
                validate(request, versioned(uri, version + 1)));
        // An incoming edit is checked since its receipt, and its placeholder version is refused as for any document
        assertNull(validate(incoming, unversioned(uri)));
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.version.changed", uri),
                validate(incoming, versioned(uri, 0)));
    }

    public void testTargetChangedSinceItWasOpenedIsRefused() throws Exception {
        var server = open();
        var other = myFixture.addFileToProject("other.foo", "before");
        String uri = uri(server, other);
        var request = WorkspaceEditSnapshot.capture(server);
        var incoming = WorkspaceEditSnapshot.capture(server.getServerWrapper(), () -> true);
        connect(other);
        var document = server.getServerWrapper().getOpenedDocument(URI.create(uri)).getSynchronizer().getDocument();
        // The text is restored, but the server was sent the changed text, which it may have answered for
        type(document, "changed");
        type(document, "before");

        assertEquals(opened(uri), validate(request, unversioned(uri)));
        assertEquals(opened(uri), validate(incoming, unversioned(uri)));
    }

    public void testTargetLoadedAtRequestAppliesOnceOpenedWhileUnchanged() throws Exception {
        var server = open();
        var file = myFixture.addFileToProject("other.foo", "before").getVirtualFile();
        assertNotNull(FileDocumentManager.getInstance().getDocument(file));
        String uri = uri(server, psiFile(file));
        assertNull(server.getServerWrapper().getOpenedDocument(URI.create(uri)));
        var snapshot = WorkspaceEditSnapshot.capture(server);
        connect(psiFile(file));

        assertNull(validate(snapshot, unversioned(uri)));
    }

    public void testTargetLoadedAtRequestAndChangedBeforeItIsOpenedIsRefused() throws Exception {
        var server = open();
        var file = myFixture.addFileToProject("other.foo", "before").getVirtualFile();
        var document = FileDocumentManager.getInstance().getDocument(file);
        String uri = uri(server, psiFile(file));
        var snapshot = WorkspaceEditSnapshot.capture(server);
        // Saved, so that it has no unsaved changes: the server may have read the file before the change
        type(document, "changed");
        FileDocumentManager.getInstance().saveDocument(document);
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
        connect(psiFile(file));

        assertEquals(opened(uri), validate(snapshot, unversioned(uri)));
    }

    public void testTargetWithUnsavedChangesAtRequestIsRefusedOnceOpened() throws Exception {
        var server = open();
        var file = myFixture.addFileToProject("other.foo", "before").getVirtualFile();
        var document = FileDocumentManager.getInstance().getDocument(file);
        type(document, "unsaved");
        String uri = uri(server, psiFile(file));
        assertNull(server.getServerWrapper().getOpenedDocument(URI.create(uri)));
        // The server read the file, which hasn't the document's text
        var snapshot = WorkspaceEditSnapshot.capture(server);
        connect(psiFile(file));

        assertEquals(opened(uri), validate(snapshot, unversioned(uri)));
    }

    public void testTargetChangedOnDiskSinceRequestIsRefusedOnceOpened() throws Exception {
        var server = open();
        var file = myFixture.getTempDirFixture().createFile("other.foo", "before");
        String uri = uri(server, psiFile(file));
        // No document is loaded at the request, and the one the server is sent is loaded from the changed file
        assertNull(FileDocumentManager.getInstance().getCachedDocument(file));
        var snapshot = WorkspaceEditSnapshot.capture(server);
        WriteAction.run(() -> VfsUtil.saveText(file, "changed"));
        connect(psiFile(file));
        assertEquals("changed", FileDocumentManager.getInstance().getDocument(file).getText());

        assertEquals(opened(uri), validate(snapshot, unversioned(uri)));
    }

    public void testTargetInADirectoryMovedThereSinceRequestIsRefusedOnceOpened() throws Exception {
        var server = open();
        var file = myFixture.getTempDirFixture().createFile("moved/other.foo", "before");
        var snapshot = WorkspaceEditSnapshot.capture(server);
        // The server's edit names the file's place after the move, where it wasn't at the request
        WriteAction.run(() -> file.getParent().rename(this, "target"));
        connect(psiFile(file));
        String uri = uri(server, psiFile(file));
        assertTrue(uri, uri.endsWith("/target/other.foo"));

        assertEquals(opened(uri), validate(snapshot, unversioned(uri)));
    }

    public void testUnsynchronizedTargetRetainsExistingBehavior() throws Exception {
        var server = open();
        var other = myFixture.addFileToProject("other.foo", "before");
        var snapshot = WorkspaceEditSnapshot.capture(server);
        String uri = uri(server, other);
        assertNull(server.getServerWrapper().getOpenedDocument(URI.create(uri)));

        assertNull(validate(snapshot, unversioned(uri)));
    }

    public void testTextEditAfterDirectoryRenameUsesOriginalDocumentVersion() throws Exception {
        var server = open();
        var wrapper = server.getServerWrapper();
        var file = myFixture.addFileToProject("old/other.foo", "before");
        connect(file);
        URI uri = wrapper.toUri(file.getVirtualFile());
        String oldDirectory = uri.resolve(".").toString();
        String newDirectory = uri.resolve("../new/").toString();
        String newUri = newDirectory + "other.foo";
        var snapshot = WorkspaceEditSnapshot.capture(server);
        int version = wrapper.getOpenedDocument(uri).getSynchronizer().getVersion();
        var text = new TextDocumentEdit(new VersionedTextDocumentIdentifier(newUri, version),
                List.of(Either.forLeft(replacement())));

        // Directory URIs may include a trailing slash on either side of the move.
        for (String oldUri : List.of(oldDirectory, oldDirectory.substring(0, oldDirectory.length() - 1))) {
            for (String targetUri : List.of(newDirectory, newDirectory.substring(0, newDirectory.length() - 1))) {
                var edit = new WorkspaceEdit(List.of(Either.forRight(new RenameFile(oldUri, targetUri)), Either.forLeft(text)));
                assertNull(validate(snapshot, edit));
            }
        }
        // A sibling that only shares the prefix is not inside the moved directory, so this
        // stale version must not be checked against the moved document.
        var sibling = new TextDocumentEdit(new VersionedTextDocumentIdentifier(
                newDirectory.substring(0, newDirectory.length() - 1) + "er/other.foo", version + 1),
                List.of(Either.forLeft(replacement())));
        assertNull(validate(snapshot, new WorkspaceEdit(List.of(
                Either.forRight(new RenameFile(oldDirectory, newDirectory)), Either.forLeft(sibling)))));

        text.getTextDocument().setVersion(version + 1);
        var stale = new WorkspaceEdit(List.of(Either.forRight(new RenameFile(oldDirectory, newDirectory)), Either.forLeft(text)));
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.version.changed", oldDirectory + "other.foo"),
                validate(snapshot, stale));
    }

    public void testDeletingADocumentChangedSinceCaptureIsRefused() throws Exception {
        var server = open();
        var snapshot = WorkspaceEditSnapshot.capture(server);
        String uri = uri(server, myFixture.getFile());
        var delete = new WorkspaceEdit(List.of(Either.forRight(new DeleteFile(uri))));
        assertNull(validate(snapshot, delete));

        type("changed");
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.document.changed", uri), validate(snapshot, delete));
    }

    public void testOverwritingADocumentChangedSinceCaptureIsRefused() throws Exception {
        var server = open();
        var snapshot = WorkspaceEditSnapshot.capture(server);
        String uri = uri(server, myFixture.getFile());
        var overwrite = new WorkspaceEdit(List.of(Either.forRight(new CreateFile(uri, new CreateFileOptions(true, false)))));
        assertNull(validate(snapshot, overwrite));

        type("changed");
        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.document.changed", uri), validate(snapshot, overwrite));
        // a new file has no document to lose
        String created = URI.create(uri).resolve("created.foo").toString();
        assertNull(validate(snapshot, new WorkspaceEdit(List.of(Either.forRight(new CreateFile(created))))));
    }

    public void testStoppedConnectionCannotApplyItsResponse() throws Exception {
        var server = open();
        var snapshot = WorkspaceEditSnapshot.capture(server);
        String uri = uri(server, myFixture.getFile());
        server.getServerWrapper().stop().get(30, TimeUnit.SECONDS);

        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.connection.changed"),
                validate(snapshot, unversioned(uri)));
    }

    public void testRetiredOwnerCannotApplyItsEdit() throws Exception {
        var server = open();
        var active = new AtomicBoolean(true);
        var snapshot = WorkspaceEditSnapshot.capture(server.getServerWrapper(), active::get);
        String uri = uri(server, myFixture.getFile());
        assertNull(validate(snapshot, unversioned(uri)));
        active.set(false);

        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.connection.changed"),
                validate(snapshot, unversioned(uri)));
    }
}
