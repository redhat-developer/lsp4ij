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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.services.LanguageServer;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Controls disposal and reply delivery explicitly. Timeouts only guard against hangs;
 * no assertion depends on a diagnostic debounce delay or a race between threads.
 */
public class DocumentContentSynchronizerDisposalTest extends LightPlatformTestCase {

    public void testLiveDiagnosticReplyIsPublished() throws Exception {
        var wrapper = new RecordingWrapper();
        openDocument(wrapper);

        wrapper.reply.complete(new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(List.of())));
        wrapper.reply.applied.get(30, TimeUnit.SECONDS);

        assertEquals(1, wrapper.updates.get());
    }

    public void testRefreshAfterDisposalDoesNotIssueAnotherPull() throws Exception {
        var wrapper = new RecordingWrapper();
        var synchronizer = openDocument(wrapper);

        Disposer.dispose(synchronizer);
        synchronizer.refreshPullDiagnostic(DocumentContentSynchronizer.RefreshPullDiagnosticOrigin.ON_WORKSPACE_REFRESH);

        assertEquals("A retired document must not restart pull diagnostics", 1, wrapper.requests.get());
    }

    public void testRestartDuringDidChangeDoesNotPullFromRetiredSynchronizer() throws Exception {
        var wrapper = new RecordingWrapper();
        var synchronizer = openDocument(wrapper);
        // Sending a notification may restart a failed connection and dispose its synchronizer.
        wrapper.beforeNotification = () -> Disposer.dispose(synchronizer);
        synchronizer.getDocument().addDocumentListener(synchronizer, synchronizer);

        ApplicationManager.getApplication().runWriteAction(() -> synchronizer.getDocument().setText("updated"));

        assertEquals("The callback must stop using its retired synchronizer", 1, wrapper.requests.get());
    }

    public void testDiagnosticReplyAfterDisposalDoesNotPublish() throws Exception {
        var wrapper = new RecordingWrapper();
        var synchronizer = openDocument(wrapper);
        Disposer.dispose(synchronizer);

        wrapper.reply.complete(new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(List.of())));
        wrapper.reply.applied.get(30, TimeUnit.SECONDS);

        assertEquals(0, wrapper.updates.get());
    }

    public void testDisposalWaitsForDiagnosticPublication() throws Exception {
        var wrapper = new RecordingWrapper();
        var synchronizer = openDocument(wrapper);
        var publicationStarted = new CompletableFuture<Thread>();
        var allowPublication = new CompletableFuture<Void>();
        wrapper.beforeUpdate = () -> {
            publicationStarted.complete(Thread.currentThread());
            allowPublication.join();
        };

        try {
            wrapper.reply.complete(new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(List.of())));
            var publisher = publicationStarted.get(30, TimeUnit.SECONDS);
            var disposalStarted = new CompletableFuture<Thread>();
            var disposal = CompletableFuture.runAsync(() -> {
                disposalStarted.complete(Thread.currentThread());
                Disposer.dispose(synchronizer);
            }, AppExecutorUtil.getAppExecutorService());
            try {
                var disposer = disposalStarted.get(30, TimeUnit.SECONDS);
                var threads = ManagementFactory.getThreadMXBean();
                // Both outcomes are stable while publication is paused: disposal either finishes
                // (the regression) or blocks on the publisher. Do not infer blocking from a delay.
                PlatformTestUtil.waitWithEventsDispatching("Disposal did not reach publication", () -> {
                    var info = threads.getThreadInfo(disposer.getId());
                    return disposal.isDone() || (info != null && info.getThreadState() == Thread.State.BLOCKED
                            && info.getLockOwnerId() == publisher.getId());
                }, 30);
                assertFalse("Disposal must wait for an active diagnostic publication", disposal.isDone());
            } finally {
                allowPublication.complete(null);
                disposal.get(30, TimeUnit.SECONDS);
            }
        } finally {
            allowPublication.complete(null);
            wrapper.reply.applied.get(30, TimeUnit.SECONDS);
        }

        assertEquals(1, wrapper.updates.get());
    }

    private DocumentContentSynchronizer openDocument(RecordingWrapper wrapper) throws Exception {
        var synchronizer = new DocumentContentSynchronizer(wrapper, "file:///diagnostic.test",
                new LightVirtualFile("diagnostic.test"), EditorFactory.getInstance().createDocument("original"),
                null, "test") {
            @Override
            public boolean isPullDiagnosticsSupported() {
                return true;
            }
        };
        Disposer.register(getTestRootDisposable(), synchronizer);
        synchronizer.getDidOpenFuture().get(30, TimeUnit.SECONDS);
        wrapper.firstPull.get(30, TimeUnit.SECONDS);
        assertEquals(1, wrapper.requests.get());
        return synchronizer;
    }

    private class RecordingWrapper extends LanguageServerWrapper {
        private final MockLanguageServer server;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger updates = new AtomicInteger();
        private final CompletableFuture<Void> firstPull = new CompletableFuture<>();
        private final DiagnosticReply reply = new DiagnosticReply();
        private Runnable beforeNotification = () -> {};
        private Runnable beforeUpdate = () -> {};

        RecordingWrapper() {
            super(DocumentContentSynchronizerDisposalTest.this.getProject(),
                    new MockLanguageServerDefinition("diagnostic-disposal", false));
            Disposer.register(getTestRootDisposable(), this);
            server = ((MockLanguageServerDefinition) getServerDefinition()).getServer();
            server.setTextDocumentService(new MockTextDocumentService(CompletableFuture::completedFuture) {
                @Override
                public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
                    requests.incrementAndGet();
                    firstPull.complete(null);
                    return reply;
                }
            });
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

        @Override
        public CompletableFuture<LanguageServer> sendNotification(Function<LanguageServer, LanguageServer> notification) {
            beforeNotification.run();
            return CompletableFuture.completedFuture(notification.apply(server));
        }

        @Override
        public void updateDiagnostics(VirtualFile file, String identifier, List<Diagnostic> diagnostics, Integer version) {
            beforeUpdate.run();
            updates.incrementAndGet();
        }
    }

    private static class DiagnosticReply extends CompletableFuture<DocumentDiagnosticReport> {
        private final CompletableFuture<Void> applied = new CompletableFuture<>();

        @Override
        public CompletableFuture<Void> thenAcceptAsync(Consumer<? super DocumentDiagnosticReport> action) {
            // Observe the actual callback completion, including failures, before asserting publication.
            var dependent = super.thenAcceptAsync(action);
            dependent.whenComplete((result, failure) -> {
                if (failure == null) {
                    applied.complete(null);
                } else {
                    applied.completeExceptionally(failure);
                }
            });
            return dependent;
        }
    }
}
