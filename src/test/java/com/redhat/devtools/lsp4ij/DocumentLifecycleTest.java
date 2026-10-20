/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The server receives a document's didOpen before any didChange or didClose of it, nothing after its
 * didClose, and no didClose without a didOpen, also when didOpen is requested only after the document
 * changed, as for a document connected during a write action.
 */
public class DocumentLifecycleTest extends LightPlatformTestCase {

    public void testChangesBeforeTheOpenAreSentInIt() throws Exception {
        var wrapper = new RecordingWrapper();
        var synchronizer = synchronizer(wrapper, null);
        change(synchronizer, "second");

        synchronizer.getDidOpenFuture().get(10, TimeUnit.SECONDS);

        // The didOpen reads the current text, which contains the change
        assertEquals(List.of("open:1:second"), wrapper.sentSoFar());
    }

    public void testChangesSinceACapturedTextFollowTheOpen() throws Exception {
        var wrapper = new RecordingWrapper();
        var synchronizer = synchronizer(wrapper, "first");
        change(synchronizer, "second");

        synchronizer.getDidOpenFuture().get(10, TimeUnit.SECONDS);

        // The didOpen sends the captured text, so the change must follow it
        assertEquals(List.of("open:1:first", "change:2:second"), wrapper.sentSoFar());
    }

    public void testRequestingTheOpenDoesNotWaitForAWriteAction() throws Exception {
        var wrapper = new RecordingWrapper();
        var synchronizer = synchronizer(wrapper, "first");
        change(synchronizer, "second");

        // A caller can hold the wrapper's documentsLock, which a write action can be waiting for, so
        // requesting the open must not wait for read access, as sending the change after it does
        var open = new AtomicReference<CompletableFuture<LanguageServer>>();
        ApplicationManager.getApplication().runWriteAction(() -> {
            var request = ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> open.set(synchronizer.getDidOpenFuture()));
            try {
                request.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError("Requesting the open waited for the write action", e);
            }
        });

        open.get().get(10, TimeUnit.SECONDS);
        assertEquals(List.of("open:1:first", "change:2:second"), wrapper.sentSoFar());
    }

    public void testACloseWithoutAnOpenSendsNothing() throws Exception {
        var wrapper = new RecordingWrapper();
        var synchronizer = synchronizer(wrapper, null);

        synchronizer.documentClosed();

        assertEquals(List.of(), wrapper.sentSoFar());
        // A closed document is not opened; its didOpen future is cancelled, as for a stopped server
        assertTrue(synchronizer.getDidOpenFuture().isCancelled());
        assertEquals(List.of(), wrapper.sentSoFar());
    }

    public void testNothingIsSentAfterTheClose() throws Exception {
        var wrapper = new RecordingWrapper();
        var synchronizer = synchronizer(wrapper, null);
        synchronizer.getDidOpenFuture().get(10, TimeUnit.SECONDS);

        synchronizer.documentClosed();
        change(synchronizer, "after close");
        synchronizer.documentClosed();

        assertEquals(List.of("open:1:first", "close"), wrapper.sentSoFar());
    }

    private DocumentContentSynchronizer synchronizer(RecordingWrapper wrapper, String capturedText) {
        var document = EditorFactory.getInstance().createDocument("first");
        var synchronizer = new DocumentContentSynchronizer(wrapper, "file:///lifecycle.test",
                new LightVirtualFile("lifecycle.test"), document, capturedText, "test");
        Disposer.register(getTestRootDisposable(), synchronizer);
        document.addDocumentListener(synchronizer, synchronizer);
        return synchronizer;
    }

    private static void change(DocumentContentSynchronizer synchronizer, String text) {
        ApplicationManager.getApplication().runWriteAction(() -> synchronizer.getDocument().setText(text));
    }

    /**
     * A wrapper with an initialized server that records the document notifications it receives.
     * It keeps the real notification dispatcher.
     */
    private class RecordingWrapper extends LanguageServerWrapper {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final TextDocumentService documents = new MockTextDocumentService(CompletableFuture::completedFuture) {
            @Override
            public void didOpen(DidOpenTextDocumentParams params) {
                events.add("open:" + params.getTextDocument().getVersion() + ":" + params.getTextDocument().getText());
            }

            @Override
            public void didChange(DidChangeTextDocumentParams params) {
                events.add("change:" + params.getTextDocument().getVersion() + ":" + params.getContentChanges().get(0).getText());
            }

            @Override
            public void didClose(DidCloseTextDocumentParams params) {
                events.add("close");
            }
        };
        private final LanguageServer server = new LanguageServer() {
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
                return documents;
            }

            @Override
            public WorkspaceService getWorkspaceService() {
                return MockLanguageServer.INSTANCE.getWorkspaceService();
            }
        };

        RecordingWrapper() {
            super(DocumentLifecycleTest.this.getProject(), new MockLanguageServerDefinition("document-lifecycle", false));
            Disposer.register(getTestRootDisposable(), this);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public TextDocumentSyncOptions getSyncOptions() {
            var options = new TextDocumentSyncOptions();
            options.setOpenClose(true);
            options.setChange(TextDocumentSyncKind.Full);
            return options;
        }

        @Override
        public CompletableFuture<LanguageServer> getInitializedServer() {
            return CompletableFuture.completedFuture(server);
        }

        /**
         * The notifications sent before this call. A last notification is dispatched after them,
         * so its completion means they have all been sent.
         */
        List<String> sentSoFar() throws Exception {
            sendNotification(ls -> ls).get(10, TimeUnit.SECONDS);
            return List.copyOf(events);
        }
    }
}
