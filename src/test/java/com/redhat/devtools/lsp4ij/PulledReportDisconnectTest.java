/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.FullDocumentDiagnosticReport;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.UnchangedDocumentDiagnosticReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A pulled report applied while a disconnect disposes the synchronizer of its document doesn't make the two wait for
 * each other: the disconnect holds the documents lock of the language server while it disposes the synchronizer, and
 * the report needs that lock for the diagnostics of a document that isn't open.
 */
public class PulledReportDisconnectTest extends LSPCodeInsightFixtureTestCase {

    public PulledReportDisconnectTest() {
        super("*.foo");
    }

    public void testAReportAppliedWhileADisconnectDisposesItsSynchronizerDoesNotDeadlock() throws Exception {
        MockLanguageServer.INSTANCE.setPullDiagnostics(full("first", null));
        var file = myFixture.configureByText("main.foo", "main");
        var related = myFixture.addFileToProject("related.foo", "related");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS).get(0).getServerWrapper();
        var opened = wrapper.getOpenedDocument(wrapper.toUri(file.getVirtualFile()));
        PlatformTestUtil.waitWithEventsDispatching("the first report must be applied",
                () -> !opened.getDiagnostics().isEmpty(), 30);
        var synchronizer = opened.getSynchronizer();

        // The next report has the diagnostics of a document that isn't open, for which it takes the documents lock
        MockLanguageServer.INSTANCE.setPullDiagnostics(full("second",
                Map.of(LSPIJUtils.toUriAsString(related), Either.forLeft(new FullDocumentDiagnosticReport(
                        List.of(new Diagnostic(new Range(new Position(0, 0), new Position(0, 7)), "related")))))));
        var lock = wrapper.getDocumentsLock();
        var disposed = new CompletableFuture<Void>();
        // As a disconnect, which disposes the synchronizer holding the documents lock
        var disconnect = new Thread(() -> {
            try {
                synchronized (lock) {
                    synchronizer.refreshPullDiagnostic(DocumentContentSynchronizer.RefreshPullDiagnosticOrigin.ON_WORKSPACE_REFRESH);
                    awaitAThreadBlockedOn(lock);
                    Disposer.dispose(synchronizer);
                }
                disposed.complete(null);
            } catch (Throwable e) {
                disposed.completeExceptionally(e);
            }
        }, "disconnect");
        disconnect.setDaemon(true);
        disconnect.start();
        try {
            disposed.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            fail("the disconnect and the report must not wait for each other");
        }
    }

    /**
     * Waits until a thread, as the one that applies the report, waits for the given lock.
     */
    private static void awaitAThreadBlockedOn(@NotNull Object lock) throws InterruptedException {
        int lockHash = System.identityHashCode(lock);
        for (long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30); System.nanoTime() < end; ) {
            for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, false)) {
                if (info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null
                        && info.getLockInfo().getIdentityHashCode() == lockHash) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("the report must wait for the documents lock");
    }

    private static DocumentDiagnosticReport full(String message,
                                                 Map<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>> relatedDocuments) {
        var report = new RelatedFullDocumentDiagnosticReport(List.of(
                new Diagnostic(new Range(new Position(0, 0), new Position(0, 4)), message)));
        report.setRelatedDocuments(relatedDocuments);
        return new DocumentDiagnosticReport(report);
    }
}
