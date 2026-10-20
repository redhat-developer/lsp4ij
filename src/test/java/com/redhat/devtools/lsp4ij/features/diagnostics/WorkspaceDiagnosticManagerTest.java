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

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    public void cacheInvalidationRestartsOnlyAffectedProviders() throws Exception {
        var h = harness();
        h.refuseCancellation = true;
        h.manager.updateProviders(Set.of("lua", "other"));
        var first = h.next();
        var other = h.next();
        h.partial(first, full("file:///lost.lua", "lost"));
        h.partial(first, full("file:///retained.lua", "retained"));
        h.partial(other, full("file:///other.lua", "other"));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));

        h.manager.invalidate(URI.create("file:///lost.lua"), "file:///lost.lua"::equals);
        var replacement = h.next();
        assertEquals(first.params.getIdentifier(), replacement.params.getIdentifier());
        assertFalse(other.future.cancelRequested);
        other.future.complete(full("file:///other.lua", "other-updated"));
        var otherReplacement = h.next();
        assertEquals(other.params.getIdentifier(), otherReplacement.params.getIdentifier());
        assertEquals(List.of(new PreviousResultId("file:///other.lua", "other-updated")),
                otherReplacement.params.getPreviousResultIds());
        assertEquals("other-updated", h.applied.remove().getLeft().getResultId());
        assertEquals(List.of(new PreviousResultId("file:///retained.lua", "retained")),
                replacement.params.getPreviousResultIds());
        assertTrue(first.future.cancelRequested);
        // Even a server that refuses cancellation cannot restore the lost ID.
        h.partial(first, full("file:///lost.lua", "late"));
        first.future.complete(full("file:///lost.lua", "late"));
        h.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertTrue(h.requests.isEmpty());
        assertTrue(h.applied.isEmpty());
        assertFalse(replacement.future.cancelRequested);
    }

    @Test
    public void unrelatedInvalidationsKeepPendingPullAndFilterLateReports() throws Exception {
        var h = harness();
        var matchedUris = new CopyOnWriteArrayList<String>();
        h.matcher = uri -> { matchedUris.add(uri); return CompletableFuture.completedFuture(true); };
        h.manager.updateProviders(Set.of("lua"));
        var first = h.next();
        for (int i = 0; i < 3; i++) {
            h.manager.invalidate(URI.create("file:///lost.lua"), "file:///lost.lua"::equals);
        }
        h.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertFalse(first.future.cancelRequested);
        assertTrue(h.requests.isEmpty());
        h.partial(first, full("file:///lost.lua", "stale-partial"));
        h.partial(first, full("file:///retained.lua", "retained"));
        first.future.complete(full("file:///lost.lua", "stale-final"));
        var second = h.next();
        assertEquals(List.of(new PreviousResultId("file:///retained.lua", "retained")),
                second.params.getPreviousResultIds());
        assertEquals(List.of("file:///retained.lua"), matchedUris);
        assertEquals("retained", h.applied.remove().getLeft().getResultId());
        assertTrue(h.applied.isEmpty());
        // Invalidation belongs to the old request, not subsequent pulls.
        second.future.complete(full("file:///lost.lua", "fresh"));
        assertEquals("fresh", h.applied.poll(5, TimeUnit.SECONDS).getLeft().getResultId());
    }

    @Test
    public void repeatedCacheInvalidationsReplaceSupersededFilters() throws Exception {
        var h = harness();
        h.manager.updateProviders(Set.of("lua"));
        var first = h.next();
        var supersededChecks = new AtomicInteger();
        var currentChecks = new AtomicInteger();
        // Reopening the same file must not accumulate URI lookups while a
        // server keeps one workspace request open and streams partial reports.
        for (int i = 0; i < 20; i++) {
            h.manager.invalidate(URI.create("file:///lost.lua"), uri -> {
                supersededChecks.incrementAndGet();
                return uri.equals("file:///lost.lua");
            });
        }
        h.manager.invalidate(URI.create("file:///lost.lua"), uri -> {
            currentChecks.incrementAndGet();
            return uri.equals("file:///lost.lua");
        });
        h.manager.invalidate(URI.create("file:///other.lua"), "file:///other.lua"::equals);
        h.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        supersededChecks.set(0);
        currentChecks.set(0);

        h.partial(first, full("file:///lost.lua", "stale"));
        h.partial(first, full("file:///other.lua", "also-stale"));
        h.partial(first, full("file:///retained.lua", "retained"));
        first.future.complete(new WorkspaceDiagnosticReport(List.of()));
        var second = h.next();
        assertFalse(first.future.cancelRequested);
        assertEquals(List.of(new PreviousResultId("file:///retained.lua", "retained")),
                second.params.getPreviousResultIds());
        assertEquals("retained", h.applied.remove().getLeft().getResultId());
        assertTrue(h.applied.isEmpty());
        assertEquals("Superseded filters must not resolve subsequent report URIs", 0, supersededChecks.get());
        assertTrue("The current filter must still reject reports for its cache", currentChecks.get() > 0);
    }

    @Test
    public void invalidationFiltersQueuedReportsWithoutCancellingUnrelatedMatcher() throws Exception {
        var h = harness();
        var pending = new MatchFuture();
        var started = new CompletableFuture<Void>();
        var matchedUris = new CopyOnWriteArrayList<String>();
        h.matcher = uri -> {
            matchedUris.add(uri);
            if (uri.equals("file:///pending.lua")) {
                started.complete(null);
                return pending;
            }
            return CompletableFuture.completedFuture(false);
        };
        h.manager.updateProviders(Set.of("lua"));
        var first = h.next();
        h.partial(first, full("file:///pending.lua", "pending"));
        started.get(5, TimeUnit.SECONDS);
        h.partial(first, full("file:///lost.lua", "queued-partial"));
        first.future.complete(full("file:///lost.lua", "queued-final"));
        h.manager.invalidate(URI.create("file:///lost.lua"), "file:///lost.lua"::equals);
        h.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertFalse(pending.cancelRequested);
        assertTrue(h.requests.isEmpty());
        pending.complete(true);
        assertEquals(List.of(new PreviousResultId("file:///pending.lua", "pending")),
                h.next().params.getPreviousResultIds());
        assertEquals(List.of("file:///pending.lua"), matchedUris);
        assertEquals("pending", h.applied.remove().getLeft().getResultId());
        assertTrue(h.applied.isEmpty());
        assertTrue(h.clearedFiles.isEmpty());
    }

    @Test
    public void cacheInvalidationCancelsPendingFullMatchWithoutCachedResultId() throws Exception {
        var h = harness();
        var pending = new MatchFuture();
        var started = new CompletableFuture<Void>();
        h.matcher = uri -> { started.complete(null); return pending; };
        h.manager.updateProviders(Collections.singleton(null));
        h.next().future.complete(full("file:///a.lua", "stale"));
        started.get(5, TimeUnit.SECONDS);
        h.manager.invalidate(URI.create("file:///a.lua"), "file:///a.lua"::equals);
        var replacement = h.next();
        assertTrue(pending.cancelRequested);
        assertTrue(replacement.params.getPreviousResultIds().isEmpty());
        h.matcher = uri -> CompletableFuture.completedFuture(true);
        pending.complete(true); // cancellation-refusing matcher must not restore the stale result
        replacement.future.complete(full("file:///a.lua", "fresh"));
        var applied = h.applied.poll(5, TimeUnit.SECONDS);
        assertNotNull(applied);
        assertEquals("fresh", applied.getLeft().getResultId());
        assertEquals(List.of(new PreviousResultId("file:///a.lua", "fresh")),
                h.next().params.getPreviousResultIds());
        assertTrue(h.applied.isEmpty());
        assertTrue(h.clearedFiles.isEmpty());
    }

    @Test
    public void cacheInvalidationRetainsIdleProvidersWithUnrelatedResults() throws Exception {
        var h = harness();
        h.manager.updateProviders(Set.of("lua"));
        var first = h.next();
        h.partial(first, full("file:///retained.lua", "retained"));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        first.future.completeExceptionally(cancelled(new DiagnosticServerCancellationData(false)));
        h.manager.invalidate(URI.create("file:///lost.lua"), "file:///lost.lua"::equals);
        h.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertTrue(h.requests.isEmpty());
        h.manager.refresh();
        assertEquals(List.of(new PreviousResultId("file:///retained.lua", "retained")),
                h.next().params.getPreviousResultIds());
    }

    @Test
    public void cacheInvalidationCancelsPendingUnchangedMatch() throws Exception {
        var h = harness();
        h.manager.updateProviders(Collections.singleton(null));
        h.next().future.complete(full("file:///a.lua", "old"));
        var second = h.next();
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        var pending = new MatchFuture();
        var started = new CompletableFuture<Void>();
        h.matcher = uri -> { started.complete(null); return pending; };
        second.future.complete(new WorkspaceDiagnosticReport(List.of(new WorkspaceDocumentDiagnosticReport(
                new WorkspaceUnchangedDocumentDiagnosticReport("old", "file:///a.lua", null)))));
        started.get(5, TimeUnit.SECONDS);
        h.manager.invalidate(URI.create("file:///a.lua"), "file:///a.lua"::equals);
        var replacement = h.next();
        assertTrue(replacement.params.getPreviousResultIds().isEmpty());
        assertTrue(pending.cancelRequested);
        pending.complete(true);
        h.executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertTrue(h.applied.isEmpty());
        assertTrue(h.clearedFiles.isEmpty());
        assertTrue(h.clearedProviders.isEmpty());
    }

    @Test
    public void cacheInvalidationDuringPublicationDropsNewlyRetainedId() throws Exception {
        var h = harness();
        h.beforeApply = () -> h.manager.invalidate(URI.create("file:///a.lua"), "file:///a.lua"::equals);
        h.manager.updateProviders(Set.of("lua"));
        h.next().future.complete(full("file:///a.lua", "old"));
        assertNotNull(h.applied.poll(5, TimeUnit.SECONDS));
        assertTrue(h.next().params.getPreviousResultIds().isEmpty());
    }

    @Test
    public void providerCleanupFailureDoesNotPreventRemainingChanges() throws Exception {
        var h = harness();
        h.manager.updateProviders(Set.of("first", "second"));
        var first = h.next();
        var second = h.next();
        h.failNextProviderClear = true;
        h.manager.updateProviders(Set.of("replacement"));
        var replacement = h.next();
        assertEquals("replacement", replacement.params.getIdentifier());
        assertTrue(replacement.params.getPreviousResultIds().isEmpty());
        assertTrue(first.future.cancelRequested);
        assertTrue(second.future.cancelRequested);
        assertEquals(2, h.clearedProviders.size());
        assertEquals(Set.of("first", "second"), new HashSet<>(h.clearedProviders));
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
        volatile boolean failNextProviderClear;
        volatile Runnable beforeApply = () -> {};
        volatile java.util.function.Function<String, CompletableFuture<Boolean>> matcher = uri -> CompletableFuture.completedFuture(true);
        final List<String> clearedProviders = new CopyOnWriteArrayList<>();
        final List<String> clearedFiles = new CopyOnWriteArrayList<>();
        final WorkspaceDiagnosticManager manager = new WorkspaceDiagnosticManager(this, executor, uri -> matcher.apply(uri), (identifier, report) -> {
            beforeApply.run();
            applied.add(report);
            return accept;
        }, identifier -> {
            clearedProviders.add(identifier);
            if (failNextProviderClear) {
                failNextProviderClear = false;
                throw new IllegalStateException("provider cleanup failure");
            }
        }, (identifier, uri) -> clearedFiles.add(identifier + ":" + uri));
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
