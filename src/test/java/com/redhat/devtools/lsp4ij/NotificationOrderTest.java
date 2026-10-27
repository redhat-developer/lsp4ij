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
import java.util.stream.IntStream;

/**
 * Notifications reach the server in the order they are sent, also while the server is still
 * initializing. The wrapper keeps its real dispatcher; only the end of initialization is controlled.
 */
public class NotificationOrderTest extends LightPlatformTestCase {

    public void testNotificationsSentDuringInitializationKeepTheirOrder() throws Exception {
        var wrapper = new InitializingWrapper();
        var sent = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> wrapper.sendNotification(ls -> {
                    wrapper.events.add("notification:" + i);
                    return ls;
                }))
                .toArray(CompletableFuture[]::new);

        wrapper.initialized.complete(null);
        CompletableFuture.allOf(sent).get(10, TimeUnit.SECONDS);

        assertEquals(List.of("notification:1", "notification:2", "notification:3", "notification:4",
                "notification:5"), wrapper.events);
    }

    public void testDidOpenKeepsItsPlaceBeforeChangeAndClose() throws Exception {
        var wrapper = new InitializingWrapper();
        var synchronizer = synchronizer(wrapper);
        var opened = synchronizer.getDidOpenFuture();
        change(synchronizer, "second");
        synchronizer.documentClosed();

        wrapper.initialized.complete(null);
        opened.get(10, TimeUnit.SECONDS);

        assertEquals(List.of("open:1:first", "change:2:second", "close"), wrapper.sentSoFar());
    }

    public void testReopenSendsTheCloseBeforeTheNewOpen() throws Exception {
        var wrapper = new InitializingWrapper();
        var first = synchronizer(wrapper);
        first.getDidOpenFuture();
        first.documentClosed();
        var second = synchronizer(wrapper);
        second.getDidOpenFuture();
        change(second, "reopened");

        wrapper.initialized.complete(null);

        assertEquals(List.of("open:1:first", "close", "open:2:first", "change:3:reopened"),
                wrapper.sentSoFar());
    }

    private DocumentContentSynchronizer synchronizer(InitializingWrapper wrapper) {
        var document = EditorFactory.getInstance().createDocument("first");
        var synchronizer = new DocumentContentSynchronizer(wrapper, "file:///order.test",
                new LightVirtualFile("order.test"), document, null, "test");
        Disposer.register(getTestRootDisposable(), synchronizer);
        document.addDocumentListener(synchronizer, synchronizer);
        return synchronizer;
    }

    private static void change(DocumentContentSynchronizer synchronizer, String text) {
        ApplicationManager.getApplication().runWriteAction(() -> synchronizer.getDocument().setText(text));
    }

    /**
     * A wrapper whose server is initialized when the test says so. getInitializedServer() answers
     * as the real one does during initialization: a new dependent of one initialization future.
     */
    private class InitializingWrapper extends LanguageServerWrapper {
        private final CompletableFuture<Void> initialized = new CompletableFuture<>();
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

        InitializingWrapper() {
            super(NotificationOrderTest.this.getProject(), new MockLanguageServerDefinition("notification-order", false));
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
            return initialized.thenApply(ignored -> server);
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
