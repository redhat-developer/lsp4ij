/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DirectoryDocumentMoveTest extends LSPCodeInsightFixtureTestCase {
    public DirectoryDocumentMoveTest() {
        super("*.foo");
    }

    public void testMoveRestoreAndMoveAgainKeepOneCurrentUnsavedDocument() throws Exception {
        var context = openDocument();
        var directory = context.file().getParent();
        var originalParent = directory.getParent();
        var target = myFixture.getTempDirFixture().findOrCreateDir("destination");
        move(context, directory, target);
        move(context, directory, originalParent);
        move(context, directory, target);
    }

    public void testNativeUndoAndRedoReconnectTheUnsavedDescendant() throws Exception {
        var context = openDocument();
        var directory = context.file().getParent();
        var originalParent = directory.getParent();
        var target = myFixture.getTempDirFixture().findOrCreateDir("destination");
        move(context, directory, target);
        var undo = UndoManager.getInstance(myFixture.getProject());
        assertTrue("The native VFS command must be undoable", undo.isUndoAvailable(null));
        reconnectAfter(context, () -> undo.undo(null));
        assertEquals(originalParent, directory.getParent());
        assertTrue(undo.isRedoAvailable(null));
        reconnectAfter(context, () -> undo.redo(null));
        assertEquals(target, directory.getParent());
    }

    private void reconnectAfter(Context context, Runnable operation) {
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        var closed = new CompletableFuture<DidCloseTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened);
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidCloseCallback(closed);
        String oldUri = context.wrapper().toUri(context.file()).toString();
        operation.run();
        awaitConnection(context, opened, closed, oldUri);
    }

    public void testDirectoryRenameReconnectsItsOpenDescendant() throws Exception {
        var context = openDocument();
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        var closed = new CompletableFuture<DidCloseTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened);
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidCloseCallback(closed);
        String oldUri = context.wrapper().toUri(context.file()).toString();
        WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> context.file().getParent().rename(this, "renamed"));
        awaitConnection(context, opened, closed, oldUri);
    }

    public void testImmediateReverseBeforeReconnectDoesNotDuplicateConnections() throws Exception {
        var context = openDocument();
        var directory = context.file().getParent();
        var originalParent = directory.getParent();
        var target = myFixture.getTempDirFixture().findOrCreateDir("destination");
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened);
        WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> {
            directory.move(this, target);
            directory.move(this, originalParent);
        });
        PlatformTestUtil.waitWithEventsDispatching("the current path should reconnect exactly once",
                () -> opened.isDone() && context.wrapper().getOpenedDocuments().size() == 1, 10);
        assertEquals(context.wrapper().toUri(context.file()).toString(), opened.join().getTextDocument().getUri());
        assertEquals("unsaved", opened.join().getTextDocument().getText());
    }

    public void testClosingBeforeReconnectRetiresTheDocument() throws Exception {
        var context = openDocument();
        var target = myFixture.getTempDirFixture().findOrCreateDir("destination");
        WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> {
            context.file().getParent().move(this, target);
            FileEditorManager.getInstance(myFixture.getProject()).closeFile(context.file());
        });
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertTrue(context.wrapper().getOpenedDocuments().isEmpty());
    }

    private record Context(VirtualFile file, LanguageServerWrapper wrapper) {}

    private Context openDocument() throws Exception {
        var virtualFile = myFixture.getTempDirFixture().createFile("source/nested/test.foo", "disk");
        myFixture.configureFromExistingVirtualFile(virtualFile);
        var file = myFixture.getFile();
        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(5, TimeUnit.SECONDS);
        var wrapper = servers.get(0).getServerWrapper();
        WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> myFixture.getEditor().getDocument().setText("unsaved"));
        return new Context(virtualFile, wrapper);
    }

    private void move(Context context, VirtualFile directory, VirtualFile target) throws Exception {
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        var closed = new CompletableFuture<DidCloseTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened);
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidCloseCallback(closed);
        String oldUri = context.wrapper().toUri(context.file()).toString();
        WriteCommandAction.writeCommandAction(myFixture.getProject()).withName("Move directory")
                .withGlobalUndo().run(() -> directory.move(this, target));
        awaitConnection(context, opened, closed, oldUri);
    }

    private void awaitConnection(Context context, CompletableFuture<DidOpenTextDocumentParams> opened,
                                 CompletableFuture<DidCloseTextDocumentParams> closed, String oldUri) {
        PlatformTestUtil.waitWithEventsDispatching("the descendant must close its old URI and reopen its current buffer",
                () -> opened.isDone() && closed.isDone() && context.wrapper().getOpenedDocuments().size() == 1 &&
                        context.wrapper().getOpenedDocument(context.wrapper().toUri(context.file())) != null, 10);
        assertEquals(oldUri, closed.join().getTextDocument().getUri());
        assertEquals(context.wrapper().toUri(context.file()).toString(), opened.join().getTextDocument().getUri());
        assertEquals("unsaved", opened.join().getTextDocument().getText());
    }
}
