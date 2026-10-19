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
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FileDocumentMoveTest extends LSPCodeInsightFixtureTestCase {
    public FileDocumentMoveTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            var operations = new FileOperationsServerCapabilities();
            operations.setDidRename(new FileOperationOptions(List.of(
                    new FileOperationFilter(new FileOperationPattern("**/*.foo")))));
            var workspace = new WorkspaceServerCapabilities();
            workspace.setFileOperations(operations);
            capabilities.setWorkspace(workspace);
            return capabilities;
        });
    }

    public void testNativeRenameClosesOldUriAndReopensUnsavedText() throws Exception {
        var context = openDocument();
        reconnectAfter(context, () -> write(() -> context.file().rename(this, "renamed.foo")));
    }

    public void testNativeMoveUndoAndRedoReconnectTheCurrentUri() throws Exception {
        var context = openDocument();
        var originalParent = context.file().getParent();
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination");
        reconnectAfter(context, () -> write(() -> context.file().move(this, destination)));
        var undo = UndoManager.getInstance(myFixture.getProject());
        assertTrue(undo.isUndoAvailable(null));
        reconnectAfter(context, () -> boundedOperation(() -> undo.undo(null)));
        assertEquals(originalParent, context.file().getParent());
        assertTrue(undo.isRedoAvailable(null));
        reconnectAfter(context, () -> boundedOperation(() -> undo.redo(null)));
        assertEquals(destination, context.file().getParent());
    }

    public void testRenameTwiceBeforeReconnectOpensOnlyTheFinalUri() throws Exception {
        var context = openDocument();
        var reopened = new CompletableFuture<DidOpenTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(reopened);
        write(() -> {
            context.file().rename(this, "intermediate.foo");
            context.file().rename(this, "final.foo");
        });
        PlatformTestUtil.waitWithEventsDispatching("the final URI must reconnect", reopened::isDone, 10);
        assertEquals(context.wrapper().toUri(context.file()), URI.create(reopened.join().getTextDocument().getUri()));
        assertEquals("unsaved", reopened.join().getTextDocument().getText());
        assertEquals(1, context.wrapper().getOpenedDocuments().size());
    }

    public void testCloseBeforeDeferredReconnectDoesNotReopenTheFile() throws Exception {
        var context = openDocument();
        write(() -> {
            context.file().rename(this, "renamed.foo");
            FileEditorManager.getInstance(myFixture.getProject()).closeFile(context.file());
        });
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertTrue(context.wrapper().getOpenedDocuments().isEmpty());
    }

    public void testEditorOutsideFileEditorManagerRetainsItsConnectionAfterRename() throws Exception {
        var file = myFixture.getTempDirFixture().createFile("source/raw.foo", "disk");
        var document = LSPIJUtils.getDocument(file);
        var editor = EditorFactory.getInstance().createEditor(document, myFixture.getProject());
        try {
            assertFalse(FileEditorManager.getInstance(myFixture.getProject()).isFileOpen(file));
            var context = connect(file);
            write(() -> document.setText("unsaved"));
            reconnectAfter(context, () -> write(() -> file.rename(this, "renamed.foo")));
        } finally {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    private record Context(VirtualFile file, LanguageServerWrapper wrapper) {}

    private Context openDocument() throws Exception {
        var file = myFixture.getTempDirFixture().createFile("source/test.foo", "disk");
        myFixture.configureFromExistingVirtualFile(file);
        var context = connect(file);
        write(() -> myFixture.getEditor().getDocument().setText("unsaved"));
        return context;
    }

    private Context connect(VirtualFile file) throws Exception {
        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(PsiManager.getInstance(myFixture.getProject()).findFile(file), null, null)
                .get(5, TimeUnit.SECONDS);
        return new Context(file, servers.get(0).getServerWrapper());
    }

    private void reconnectAfter(Context context, Runnable operation) {
        var closed = new CompletableFuture<DidCloseTextDocumentParams>();
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        var renamed = new CompletableFuture<RenameFilesParams>();
        var closedBeforeOpen = opened.thenApply(ignored -> closed.isDone());
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidCloseCallback(closed);
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened);
        MockLanguageServer.INSTANCE.getWorkspaceService().setDidRenameCallback(renamed);
        URI oldUri = context.wrapper().toUri(context.file());
        operation.run();
        PlatformTestUtil.waitWithEventsDispatching("rename must deliver close, open and workspace notification",
                () -> closed.isDone() && opened.isDone() && renamed.isDone() && closedBeforeOpen.isDone(), 10);
        URI newUri = context.wrapper().toUri(context.file());
        assertTrue("didClose must precede didOpen", closedBeforeOpen.join());
        assertEquals(oldUri, URI.create(closed.join().getTextDocument().getUri()));
        assertEquals(newUri, URI.create(opened.join().getTextDocument().getUri()));
        assertEquals("unsaved", opened.join().getTextDocument().getText());
        assertEquals(1, renamed.join().getFiles().size());
        assertEquals(oldUri, URI.create(renamed.join().getFiles().get(0).getOldUri()));
        assertEquals(newUri, URI.create(renamed.join().getFiles().get(0).getNewUri()));
        assertEquals(1, context.wrapper().getOpenedDocuments().size());
        assertNotNull(context.wrapper().getOpenedDocument(context.wrapper().toUri(context.file())));
    }

    private void write(ThrowableRunnable<IOException> operation) {
        boundedOperation(() -> {
            try {
                WriteCommandAction.writeCommandAction(myFixture.getProject())
                        .withName("Move file").withGlobalUndo().run(operation);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void boundedOperation(Runnable operation) {
        EdtTestUtil.runInEdtAndWait(() -> {
            var progress = new EmptyProgressIndicator();
            // The old implementation waits for a read action while holding the VFS write lock.
            // Cancel that wait on regression, so this test fails instead of hanging the suite.
            var deadline = AppExecutorUtil.getAppScheduledExecutorService()
                    .schedule(progress::cancel, 10, TimeUnit.SECONDS);
            try {
                ProgressManager.getInstance().runProcess(operation, progress);
                assertFalse("Native rename must not wait for didOpen under the write lock", progress.isCanceled());
            } finally {
                deadline.cancel(false);
            }
        });
    }
}
