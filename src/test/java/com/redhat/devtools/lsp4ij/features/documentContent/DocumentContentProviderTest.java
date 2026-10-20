/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentContent;

import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.internal.capabilities.ClientCapabilitiesFactory;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentParams;
import org.eclipse.lsp4j.TextDocumentContentResult;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Virtual navigation content belongs to its connection and latest refresh. */
public class DocumentContentProviderTest extends BasePlatformTestCase {

    private static final String URI_TEXT = "lsp-source:///stdlib/Library.txt?revision=1";
    private ContentServer server;
    private DocumentContentProvider provider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new ContentServer();
        provider = provider(server);
    }

    private DocumentContentProvider provider(ContentServer owner) {
        var result = new DocumentContentProvider(getProject(), owner, () -> List.of("lsp-source", "file"));
        Disposer.register(getTestRootDisposable(), result);
        return result;
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            provider.dispose();
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        } finally {
            super.tearDown();
        }
    }

    public void testOnlyAdvertisedNonFileSchemesAreFetched() {
        assertNull(provider.resolve("file:///tmp/local.txt").join());
        assertNull(provider.resolve("https://example.test/source.txt").join());
        assertNull(provider.resolve("relative.txt").join());
        assertNull(provider.resolve("broken uri").join());
        assertEquals(0, server.pending.size());
        provider.refresh(URI_TEXT).join();
        assertEquals("Unknown refresh must not fetch or grow the cache", 0, server.pending.size());
    }

    public void testInitialReadsCoalesceAndPreserveReadOnlyUriIdentity() {
        var first = provider.resolve(URI_TEXT);
        var second = provider.resolve(URI_TEXT);
        assertEquals(1, server.pending.size());
        assertNull("Cache lookup must never block or start another request", provider.findFile(URI_TEXT));
        server.reply(0, "public source");
        var file = first.join();
        assertSame(file, second.join());
        assertSame(file, provider.findFile(URI_TEXT));
        assertEquals(URI_TEXT, file.getUrl());
        assertFalse(file.isWritable());
        var document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        assertFalse(document.isWritable());
        assertEquals("public source", document.getText());
        var features = new LSPClientFeatures();
        features.installDocumentContentProvider(provider);
        assertEquals(URI.create(URI_TEXT), FileUriSupport.getFileUri(file, features));
        assertEquals(URI_TEXT, FileUriSupport.toString(file, features));
        assertSame(file, FileUriSupport.findFileByUri(URI_TEXT, features));
    }

    public void testRefreshKeepsFileMutatorsReadOnlyAndUpdatesByteContent() throws Exception {
        var file = (DocumentContentFile) loaded("short");
        assertThrows(IncorrectOperationException.class, () -> file.setContent(this, "user edit", false));
        long oldStamp = file.getModificationStamp();
        var refresh = provider.refresh(URI_TEXT);
        String text = "longer server content \u03bb";
        server.reply(1, text);
        await(refresh);
        assertEquals(text, file.getContent().toString());
        assertEquals(text, new String(file.contentsToByteArray(), file.getCharset()));
        assertEquals(text.getBytes(file.getCharset()).length, file.getLength());
        try (var input = file.getInputStream()) {
            assertEquals(text, new String(input.readAllBytes(), file.getCharset()));
        }
        assertTrue(file.getModificationStamp() > oldStamp);
        assertFalse(file.isWritable());
        assertThrows(IncorrectOperationException.class, () -> file.setContent(this, "another user edit", false));
    }

    public void testCancellingOneLookupDoesNotCancelSharedContent() {
        var cancelled = provider.resolve(URI_TEXT);
        var retained = provider.resolve(URI_TEXT);
        cancelled.cancel(true);
        assertFalse(server.pending.get(0).reply.isCancelled());
        server.reply(0, "retained");
        assertNotNull(retained.join());
        assertTrue(cancelled.isCancelled());
    }

    public void testRefreshUpdatesSameReadOnlyDocumentAndLeavesItClean() {
        var file = loaded("old");
        var manager = FileDocumentManager.getInstance();
        var document = manager.getDocument(file);
        assertNotNull(document);
        var refreshed = provider.refresh(URI_TEXT);
        server.reply(1, "new\ncontent");
        await(refreshed);
        assertSame(file, provider.findFile(URI_TEXT));
        assertSame(document, manager.getDocument(file));
        assertEquals("new\ncontent", document.getText());
        assertFalse(document.isWritable());
        assertFalse(manager.isDocumentUnsaved(document));
    }

    public void testRefreshWaitsForAModalDialogToCloseBeforeItChangesTheDocument() {
        var file = loaded("before");
        var document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        var dialog = new Object();
        LaterInvocator.enterModal(dialog);
        CompletableFuture<Void> refreshed;
        try {
            refreshed = provider.refresh(URI_TEXT);
            server.reply(1, "after");
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertFalse("a write action must not run under a modal dialog", refreshed.isDone());
            assertEquals("before", document.getText());
        } finally {
            LaterInvocator.leaveModal(dialog);
        }
        await(refreshed);
        assertEquals("after", document.getText());
    }

    public void testOutOfOrderRefreshCannotReplaceNewerContent() {
        var file = loaded("original");
        var oldRefresh = provider.refresh(URI_TEXT);
        var newRefresh = provider.refresh(URI_TEXT);
        server.reply(2, "latest");
        await(newRefresh);
        server.reply(1, "obsolete");
        await(oldRefresh);
        assertEquals("latest", FileDocumentManager.getInstance().getDocument(file).getText());
    }

    public void testRefreshDuringInitialReadCompletesWaitingNavigationWithNewestText() {
        var navigation = provider.resolve(URI_TEXT);
        var refresh = provider.refresh(URI_TEXT);
        server.reply(0, "obsolete");
        assertFalse(navigation.isDone());
        server.reply(1, "current");
        await(refresh);
        assertEquals("current", FileDocumentManager.getInstance().getDocument(navigation.join()).getText());
    }

    public void testFailedInitialReadCanRetryAndDoesNotFailOtherLocations() {
        var location = new Location(URI_TEXT, range());
        var ordinary = new Location("file:///tmp/source.txt", range());
        var locations = List.of(location, ordinary);
        var ready = DocumentContentTargets.prepare(locations, provider);
        server.pending.get(0).reply.completeExceptionally(new IllegalStateException("Unavailable"));
        assertSame(locations, ready.join());
        assertNull(provider.findFile(URI_TEXT));
        var retry = provider.resolve(URI_TEXT);
        assertEquals(2, server.pending.size());
        server.reply(1, "retry");
        assertNotNull(retry.join());
    }

    public void testDisposalRejectsLateReplyAndRetiresCachedFile() {
        var file = loaded("loaded");
        var pending = provider.resolve("lsp-source:///pending.txt");
        provider.dispose();
        server.reply(1, "too late");
        assertNull(pending.join());
        assertNull(provider.findFile(URI_TEXT));
        PlatformTestUtil.waitWithEventsDispatching("retired file becomes invalid", () -> !file.isValid(), 5);
        assertNull(provider.resolve(URI_TEXT).join());
        assertEquals(2, server.pending.size());
    }

    public void testDisposalClosesEditorBeforeInvalidatingItsPsi() throws Exception {
        var file = loaded("visible content");
        var manager = FileEditorManager.getInstance(getProject());
        manager.openFile(file, true);
        assertTrue(manager.isFileOpen(file));
        CompletableFuture.runAsync(provider::dispose).get(5, TimeUnit.SECONDS);
        assertNull("Connection lookup retires without waiting for the EDT", provider.findFile(URI_TEXT));
        // This test is on the EDT, so queued close/invalidation has not run yet.
        assertTrue("Visible PSI must stay valid until its editor can close on the EDT", file.isValid());
        PlatformTestUtil.waitWithEventsDispatching("closed view is invalidated",
                () -> !file.isValid() && !manager.isFileOpen(file), 5);
    }

    public void testDisposalCompletesPendingRefreshEvenWhenServerIgnoresCancellation() {
        loaded("before stop");
        var refresh = provider.refresh(URI_TEXT);
        assertFalse(refresh.isDone());
        provider.dispose();
        assertTrue(refresh.isDone());
        server.reply(1, "reply after stop");
        assertNull(provider.findFile(URI_TEXT));
    }

    public void testRetiredClientCannotRemoveReplacementProvider() {
        var features = new LSPClientFeatures();
        features.installDocumentContentProvider(provider);
        var oldFile = loaded("old connection");
        var replacementServer = new ContentServer();
        var replacement = provider(replacementServer);
        features.installDocumentContentProvider(replacement);
        features.removeDocumentContentProvider(provider);
        var next = replacement.resolve(URI_TEXT);
        replacementServer.reply(0, "replacement");
        PlatformTestUtil.waitWithEventsDispatching("old file becomes invalid", () -> !oldFile.isValid(), 5);
        assertSame(next.join(), features.findFileByUri(URI_TEXT));
        var oldResult = new Location("lsp-source:///retired.txt", range());
        assertSame(oldResult, features.prepareDocumentContent(oldResult, server).join());
        assertEquals("Old server responses must not fetch through the replacement", 1, replacementServer.pending.size());
        replacement.dispose();
    }

    public void testTypedNavigationTargetsPreloadWithoutScanningCompletionOrEdits() {
        var symbol = new WorkspaceSymbol();
        symbol.setLocation(Either.forRight(new WorkspaceSymbolLocation("lsp-source:///symbol.txt")));
        var type = new TypeHierarchyItem("Type", SymbolKind.Class, "lsp-source:///type.txt", range(), range());
        var call = new CallHierarchyItem();
        call.setUri("lsp-source:///call.txt");
        var incoming = new CallHierarchyIncomingCall();
        incoming.setFrom(call);
        var link = new DocumentLink();
        link.setTarget("lsp-source:///link.txt");
        var completion = new CompletionItem("lsp-source:///completion.txt");
        completion.setData(new Location("lsp-source:///data.txt", range()));
        var edit = new WorkspaceEdit(Map.of("lsp-source:///edit.txt", List.of()));
        var response = List.of(Either.forLeft(new Location(URI_TEXT, range())), symbol, type, incoming,
                link, completion, edit, "lsp-source:///ordinary-string.txt");
        var ready = DocumentContentTargets.prepare(response, provider);
        assertEquals(5, server.pending.size());
        assertFalse(ready.isDone());
        for (int i = 0; i < server.pending.size(); i++) {
            server.reply(i, "content");
        }
        assertSame(response, ready.join());
        assertNull(provider.findFile("lsp-source:///completion.txt"));
        assertNull(provider.findFile("lsp-source:///edit.txt"));
        assertNull(provider.findFile("lsp-source:///data.txt"));
    }

    public void testShowDocumentWithoutSelectionOpensCachedVirtualContent() {
        var file = loaded("view");
        var features = new LSPClientFeatures();
        features.installDocumentContentProvider(provider);
        var shown = ServerMessageHandler.showDocument(new ShowDocumentParams(URI_TEXT), features, getProject());
        await(shown);
        assertTrue(shown.join().isSuccess());
        PlatformTestUtil.waitWithEventsDispatching("virtual content editor should open",
                () -> FileEditorManager.getInstance(getProject()).isFileOpen(file), 5);
    }

    public void testCapabilitiesAdvertiseStaticDocumentContentOnly() {
        var capability = ClientCapabilitiesFactory.create(null).getWorkspace().getTextDocumentContent();
        assertNotNull(capability);
        assertEquals(Boolean.FALSE, capability.getDynamicRegistration());
    }

    private VirtualFile loaded(String text) {
        var available = provider.resolve(URI_TEXT);
        server.reply(server.pending.size() - 1, text);
        return available.join();
    }

    private static Range range() {
        return new Range(new Position(0, 0), new Position(0, 1));
    }

    private static void await(CompletableFuture<?> future) {
        PlatformTestUtil.waitWithEventsDispatching("document content operation must complete", future::isDone, 5);
        future.join();
    }

    private static final class ContentServer implements LanguageServer, WorkspaceService {
        private record Pending(String uri, CompletableFuture<TextDocumentContentResult> reply) {
        }

        private final List<Pending> pending = new ArrayList<>();

        @Override
        public CompletableFuture<TextDocumentContentResult> textDocumentContent(TextDocumentContentParams params) {
            // Model a server which still replies after client cancellation.
            var reply = new CompletableFuture<TextDocumentContentResult>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return false;
                }
            };
            pending.add(new Pending(params.getUri(), reply));
            return reply;
        }

        private void reply(int index, String text) {
            pending.get(index).reply.complete(new TextDocumentContentResult(text));
        }

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            return CompletableFuture.completedFuture(new InitializeResult());
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void exit() {
        }

        @Override
        public TextDocumentService getTextDocumentService() {
            return null;
        }

        @Override
        public WorkspaceService getWorkspaceService() {
            return this;
        }

        @Override
        public void didChangeConfiguration(DidChangeConfigurationParams params) {
        }

        @Override
        public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        }
    }
}
