/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
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

    private String validate(WorkspaceEditSnapshot snapshot, WorkspaceEdit edit) {
        return WriteCommandAction.writeCommandAction(myFixture.getProject())
                .compute(() -> snapshot.validate(edit));
    }

    private void type(String text) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () ->
                myFixture.getEditor().getDocument().setText(text));
    }

    public void testUnchangedDocumentIsCurrent() throws Exception {
        var server = open();
        var snapshot = WorkspaceEditSnapshot.capture(server);

        assertNull(validate(snapshot, unversioned(uri(server, myFixture.getFile()))));
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

    public void testReopenDoesNotReuseRequestOwnership() throws Exception {
        var server = open();
        var snapshot = WorkspaceEditSnapshot.capture(server);
        String uri = uri(server, myFixture.getFile());
        server.getServerWrapper().disconnect(URI.create(uri), false);
        connect(myFixture.getFile());

        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.document.changed", uri),
                validate(snapshot, unversioned(uri)));
    }

    public void testTargetOpenedAfterRequestIsRefused() throws Exception {
        var server = open();
        var other = myFixture.addFileToProject("other.foo", "before");
        var snapshot = WorkspaceEditSnapshot.capture(server);
        connect(other);
        String uri = uri(server, other);

        assertEquals(LanguageServerBundle.message("lsp.workspace.edit.document.opened", uri),
                validate(snapshot, unversioned(uri)));
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
