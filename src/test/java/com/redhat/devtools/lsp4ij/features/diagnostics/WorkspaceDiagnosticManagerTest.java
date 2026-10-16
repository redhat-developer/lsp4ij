/*******************************************************************************
 * Copyright (c) 2026 Contributors to the LSP4IJ project.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.redhat.devtools.lsp4ij.JSONUtils;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.junit.After;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class WorkspaceDiagnosticManagerTest {
    private final List<Harness> harnesses = new ArrayList<>();

    private Harness harness() {
        var harness = new Harness();
        harnesses.add(harness);
        return harness;
    }

    @After
    public void dispose() throws InterruptedException {
        for (var harness : harnesses) {
            harness.manager.dispose();
            assertTrue(harness.executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void pullsAgainAndRetainsUnchangedResults() throws Exception {
        var h = harness();
        h.manager.updateProviders(Set.of("lua"));
        var first = h.next();
        assertEquals("lua", first.params.getIdentifier());
        assertTrue(first.params.getPreviousResultIds().isEmpty());
        first.future.complete(full("file:///a.lua", "one"));
        assertTrue(h.applied.poll(5, TimeUnit.SECONDS).isLeft());
        var second = h.next();
        assertEquals(List.of(new PreviousResultId("file:///a.lua", "one")), second.params.getPreviousResultIds());
        second.future.complete(new WorkspaceDiagnosticReport(List.of(new WorkspaceDocumentDiagnosticReport(
                new WorkspaceUnchangedDocumentDiagnosticReport("two", "file:///a.lua", null)))));
        assertTrue(h.applied.poll(5, TimeUnit.SECONDS).isRight());
        assertEquals("two", h.next().params.getPreviousResultIds().get(0).getValue());
    }

    @Test
    public void acceptsStreamedReportsAndLastReportWins() throws Exception {
        var h = harness();
        h.manager.updateProviders(Collections.singleton(null));
        var first = h.next();
        h.partial(first, full("file:///a.lua", "one"));
        h.partial(first, full("file:///a.lua", "two"));
        first.future.complete(new WorkspaceDiagnosticReport(List.of()));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        var second = h.next();
        assertNull(second.params.getIdentifier());
        assertEquals(List.of(new PreviousResultId("file:///a.lua", "two")), second.params.getPreviousResultIds());
    }

    @Test
    public void refreshCancelsPendingPullAndIgnoresLateReports() throws Exception {
        var h = harness();
        h.refuseCancellation = true;
        h.manager.updateProviders(Set.of("lua"));
        var first = h.next();
        h.manager.refresh();
        var second = h.next();
        assertTrue(first.future.cancelRequested);
        assertNotEquals(first.params.getPartialResultToken(), second.params.getPartialResultToken());
        h.partial(first, full("file:///stale.lua", "old"));
        first.future.complete(full("file:///stale.lua", "old"));
        h.partial(second, full("file:///fresh.lua", "new"));
        var report = h.applied.poll(5, TimeUnit.SECONDS);
        assertNotNull(report);
        assertEquals("file:///fresh.lua", report.getLeft().getUri());
        assertNull(h.applied.poll(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void unregisterCancelsAndReregisterStartsWithoutOldResultIds() throws Exception {
        var h = harness();
        h.manager.updateProviders(Set.of("lua"));
        var first = h.next();
        h.partial(first, full("file:///a.lua", "one"));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        h.manager.updateProviders(Set.of());
        h.manager.updateProviders(Set.of("lua"));
        var next = h.next();
        assertTrue(first.future.isCancelled());
        assertTrue(next.params.getPreviousResultIds().isEmpty());
    }

    @Test
    public void connectionAndProviderResultsAreIsolated() throws Exception {
        var a = harness();
        var b = harness();
        a.manager.updateProviders(Set.of("lua", "other"));
        b.manager.updateProviders(Set.of("lua"));
        var a1 = a.next();
        var a2 = a.next();
        var b1 = b.next();
        a.partial(a1, full("file:///a.lua", "one"));
        assertNotNull(a.applied.poll(5, TimeUnit.SECONDS));
        b.partial(a1, full("file:///a.lua", "wrong-server"));
        a.manager.refresh();
        var replacements = List.of(a.next(), a.next());
        assertFalse(b1.future.isDone());
        assertNull(b.applied.poll(100, TimeUnit.MILLISECONDS));
        for (var request : replacements) {
            assertEquals(request.params.getIdentifier().equals(a1.params.getIdentifier()) ? 1 : 0,
                    request.params.getPreviousResultIds().size());
        }
        assertTrue(a1.future.isCancelled());
        assertTrue(a2.future.isCancelled());
    }

    @Test
    public void retriesServerCancellationUnlessExplicitlyDisabled() throws Exception {
        var h = harness();
        h.manager.updateProviders(Set.of("lua"));
        h.next().future.completeExceptionally(cancelled(null));
        var second = h.next();
        second.future.completeExceptionally(cancelled(new DiagnosticServerCancellationData(false)));
        assertNull(h.requests.poll(1200, TimeUnit.MILLISECONDS));
        h.manager.refresh();
        assertNotNull(h.next());
    }

    @Test
    public void rejectedReportsAreNotAdvertisedAsCached() throws Exception {
        var h = harness();
        h.accept = false;
        h.manager.updateProviders(Set.of("lua"));
        h.next().future.complete(full("file:///not-owned.lua", "one"));
        assertTrue(h.next().params.getPreviousResultIds().isEmpty());
        assertTrue(h.clearedFiles.isEmpty()); // rejected/stale reports do not invalidate the file's mapping
    }

    @Test
    public void emptyFullReportClearsOldResultId() throws Exception {
        var h = harness();
        h.manager.updateProviders(Set.of("lua"));
        var request = h.next();
        h.partial(request, full("file:///a.lua", "one"));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        request.future.complete(full("file:///a.lua", null));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        assertTrue(h.next().params.getPreviousResultIds().isEmpty());
        assertTrue(h.clearedFiles.isEmpty()); // an accepted report need not carry a result ID
    }

    @Test
    public void shutdownCancelsPendingPullWithoutRearming() throws Exception {
        var h = harness();
        h.manager.updateProviders(Set.of("lua"));
        var request = h.next();
        h.manager.dispose();
        assertTrue(h.executor.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(request.future.isCancelled());
        h.manager.refresh();
        h.manager.updateProviders(Set.of("other"));
        h.partial(request, full("file:///a.lua", "one"));
        assertTrue(h.requests.isEmpty());
        assertTrue(h.applied.isEmpty());
    }

    @Test
    public void closedFileReportsFromDifferentProvidersDoNotOverwriteEachOther() {
        var document = new com.redhat.devtools.lsp4ij.ClosedDocument();
        assertTrue(document.getDiagnostics().isEmpty());
        assertFalse(document.hasErrors());
        var first = new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), "first");
        first.setSeverity(DiagnosticSeverity.Error);
        var second = new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), "second");
        second.setSeverity(DiagnosticSeverity.Warning);
        document.updateDiagnostics("first", List.of(first), null);
        assertTrue(document.hasErrors());
        document.updateDiagnostics("second", List.of(second), null);
        assertEquals(Set.of(first, second), new HashSet<>(document.getDiagnostics()));
        assertTrue(document.hasErrors());
        var previousDiagnostics = document.getDiagnostics();
        document.updateDiagnostics("first", List.of(), null);
        assertEquals(List.of(second), new ArrayList<>(document.getDiagnostics()));
        assertFalse(document.hasErrors());
        assertEquals(Set.of(first, second), new HashSet<>(previousDiagnostics));
        document.updateDiagnostics("second", List.of(), null);
        assertTrue(document.getDiagnostics().isEmpty());
        assertFalse(document.hasErrors());
    }

    @Test
    public void pendingMatchesPreserveStreamedAndFinalReportOrder() throws Exception {
        var h = harness();
        var pending = new CompletableFuture<Boolean>();
        var started = new CompletableFuture<Void>();
        var count = new java.util.concurrent.atomic.AtomicInteger();
        h.matcher = uri -> {
            if (count.getAndIncrement() == 0) {
                started.complete(null);
                return pending;
            }
            return CompletableFuture.completedFuture(true);
        };
        h.manager.updateProviders(Set.of("lua"));
        var request = h.next();
        h.partial(request, full("file:///a.lua", "one"));
        started.get(5, TimeUnit.SECONDS);
        request.future.complete(full("file:///a.lua", "two"));
        h.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertTrue(h.applied.isEmpty());
        pending.complete(true);
        assertEquals("one", h.applied.poll(5, TimeUnit.SECONDS).getLeft().getResultId());
        assertEquals("two", h.applied.poll(5, TimeUnit.SECONDS).getLeft().getResultId());
        assertEquals(List.of(new PreviousResultId("file:///a.lua", "two")), h.next().params.getPreviousResultIds());
    }

    @Test
    public void pendingMatcherDoesNotBlockOtherProvidersOrUnregistration() throws Exception {
        var h = harness();
        var pending = new MatchFuture();
        var started = new CompletableFuture<Void>();
        h.matcher = uri -> {
            if (uri.endsWith("blocked.lua")) {
                started.complete(null);
                return pending;
            }
            return CompletableFuture.completedFuture(true);
        };
        h.manager.updateProviders(Set.of("lua", "other"));
        var first = h.next();
        var second = h.next();
        var blocked = first.params.getIdentifier().equals("lua") ? first : second;
        var other = blocked == first ? second : first;
        h.partial(blocked, full("file:///blocked.lua", "old"));
        started.get(5, TimeUnit.SECONDS);
        h.partial(other, full("file:///other.lua", "live"));
        assertEquals("live", h.applied.poll(5, TimeUnit.SECONDS).getLeft().getResultId());
        h.manager.updateProviders(Set.of("other"));
        h.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertTrue(blocked.future.isCancelled());
        assertTrue(pending.cancelRequested);
        assertEquals(List.of("lua"), h.clearedProviders);
        pending.complete(false); // a cancelled match must neither publish nor clear diagnostics
        h.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertTrue(h.applied.isEmpty());
        assertTrue(h.clearedFiles.isEmpty());
        h.manager.updateProviders(Set.of("lua", "other"));
        assertTrue(h.next().params.getPreviousResultIds().isEmpty());
    }

    @Test
    public void shutdownCancelsPendingMatcherWithoutWaitingForIt() throws Exception {
        var h = harness();
        var pending = new MatchFuture();
        var started = new CompletableFuture<Void>();
        h.matcher = uri -> { started.complete(null); return pending; };
        h.manager.updateProviders(Set.of("lua"));
        var request = h.next();
        h.partial(request, full("file:///a.lua", "old"));
        started.get(5, TimeUnit.SECONDS);
        h.manager.dispose();
        assertTrue(h.executor.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(pending.cancelRequested);
        assertTrue(request.future.isCancelled());
        pending.complete(true);
        assertTrue(h.applied.isEmpty());
    }

    @Test
    public void matcherFailuresRejectOnlyTheirReportsAndResultIds() throws Exception {
        var h = harness();
        h.manager.updateProviders(Set.of("lua"));
        h.next().future.complete(full("file:///a.lua", "old"));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        var next = h.next();
        assertEquals(1, next.params.getPreviousResultIds().size());
        h.matcher = uri -> {
            if (uri.endsWith("a.lua")) {
                throw new IllegalStateException("synchronous matcher failure");
            }
            return uri.endsWith("b.lua")
                    ? CompletableFuture.failedFuture(new IllegalStateException("asynchronous matcher failure"))
                    : CompletableFuture.completedFuture(true);
        };
        next.future.complete(new WorkspaceDiagnosticReport(List.of(
                full("file:///a.lua", "rejected").getItems().get(0),
                full("file:///b.lua", "rejected").getItems().get(0),
                full("file:///c.lua", "accepted").getItems().get(0))));
        assertEquals("accepted", h.applied.poll(5, TimeUnit.SECONDS).getLeft().getResultId());
        assertEquals(List.of(new PreviousResultId("file:///c.lua", "accepted")), h.next().params.getPreviousResultIds());
        assertTrue(h.clearedFiles.isEmpty()); // a matcher failure does not establish loss of ownership
    }

    @Test
    public void lostMappingClearsOnlyRejectedFileAndResultId() throws Exception {
        var h = harness();
        h.manager.updateProviders(Collections.singleton(null));
        var first = h.next();
        first.future.complete(new WorkspaceDiagnosticReport(List.of(
                full("file:///a.lua", "old").getItems().get(0),
                full("file:///b.lua", "retained").getItems().get(0))));
        var second = h.next();
        h.matcher = uri -> CompletableFuture.completedFuture(!uri.endsWith("a.lua"));
        second.future.complete(new WorkspaceDiagnosticReport(List.of(new WorkspaceDocumentDiagnosticReport(
                new WorkspaceUnchangedDocumentDiagnosticReport("old", "file:///a.lua", null)))));
        assertEquals(List.of(new PreviousResultId("file:///b.lua", "retained")), h.next().params.getPreviousResultIds());
        assertEquals(List.of("null:file:///a.lua"), h.clearedFiles);
        assertTrue(h.clearedProviders.isEmpty());
    }

    private static class MatchFuture extends CompletableFuture<Boolean> {
        volatile boolean cancelRequested;
        @Override public boolean cancel(boolean interrupt) {
            cancelRequested = true;
            return false;
        }
    }

    private static ResponseErrorException cancelled(Object data) {
        return new ResponseErrorException(new ResponseError(ResponseErrorCode.ServerCancelled, "cancelled", data));
    }

    private static WorkspaceDiagnosticReport full(String uri, String resultId) {
        var report = new WorkspaceFullDocumentDiagnosticReport(List.of(), uri, null);
        report.setResultId(resultId);
        return new WorkspaceDiagnosticReport(List.of(new WorkspaceDocumentDiagnosticReport(report)));
    }

    private static class Future extends CompletableFuture<WorkspaceDiagnosticReport> {
        final boolean refuse;
        volatile boolean cancelRequested;
        Future(boolean refuse) { this.refuse = refuse; }
        @Override public boolean cancel(boolean interrupt) {
            cancelRequested = true;
            return !refuse && super.cancel(interrupt);
        }
    }

    private record Request(WorkspaceDiagnosticParams params, Future future) { }

    private static class Harness implements WorkspaceService {
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
        final BlockingQueue<WorkspaceDocumentDiagnosticReport> applied = new LinkedBlockingQueue<>();
        volatile boolean accept = true;
        volatile boolean refuseCancellation;
        volatile java.util.function.Function<String, CompletableFuture<Boolean>> matcher = uri -> CompletableFuture.completedFuture(true);
        final List<String> clearedProviders = new CopyOnWriteArrayList<>();
        final List<String> clearedFiles = new CopyOnWriteArrayList<>();
        final WorkspaceDiagnosticManager manager = new WorkspaceDiagnosticManager(this, executor, uri -> matcher.apply(uri), (identifier, report) -> {
            applied.add(report);
            return accept;
        }, clearedProviders::add, (identifier, uri) -> clearedFiles.add(identifier + ":" + uri));
        @Override public CompletableFuture<WorkspaceDiagnosticReport> diagnostic(WorkspaceDiagnosticParams params) {
            var future = new Future(refuseCancellation);
            requests.add(new Request(params, future));
            return future;
        }
        @Override public void didChangeConfiguration(DidChangeConfigurationParams params) { }
        @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) { }
        Request next() throws InterruptedException {
            var request = requests.poll(5, TimeUnit.SECONDS);
            assertNotNull("Expected a workspace diagnostic request", request);
            return request;
        }
        void partial(Request request, WorkspaceDiagnosticReport report) {
            // Exercise the JSON representation delivered by the actual LSP4J transport.
            manager.notifyProgress(new ProgressParams(request.params.getPartialResultToken(),
                    Either.forRight(JSONUtils.getLsp4jGson().toJsonTree(report))));
        }
    }
}
