/*******************************************************************************
 * Copyright (c) 2026 Contributors to the LSP4IJ project.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileNamePatternMapping;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real JSON-RPC and IntelliJ diagnostic-cache integration, independent of any Lua server. */
public class WorkspaceDiagnosticsTest extends UsefulTestCase {
    private LSPCodeInsightTestFixture fixture;
    private ScopedServer definition;
    private LanguageServerWrapper wrapper;
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();

    @Override protected void setUp() throws Exception {
        super.setUp();
        var builder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        fixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(builder.getFixture());
        fixture.setUp();
        definition = new ScopedServer();
        definition.getServer().getWorkspaceService().setDiagnosticHandler(params -> {
            var future = new CompletableFuture<WorkspaceDiagnosticReport>();
            requests.add(new Request(params, future));
            return future;
        });
        LanguageServersRegistry.getInstance().addServerDefinition(fixture.getProject(), definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.workspace"), null)));
    }

    @Override protected void tearDown() throws Exception {
        try {
            try {
                if (fixture != null && definition != null) {
                    LanguageServersRegistry.getInstance().removeServerDefinition(fixture.getProject(), definition);
                }
            } finally {
                if (fixture != null) {
                    fixture.tearDown();
                }
            }
        } finally {
            super.tearDown();
        }
    }

    private void start(boolean workspace) throws Exception {
        var options = new DiagnosticRegistrationOptions();
        options.setIdentifier("test");
        options.setWorkspaceDiagnostics(workspace);
        definition.getServer().getInitializeResult().getCapabilities().setDiagnosticProvider(options);
        wrapper = LanguageServerManager.getInstance(fixture.getProject()).getLanguageServer(definition.getId())
                .get(10, TimeUnit.SECONDS).getServerWrapper();
    }

    public void testClosedFilesStreamAndClearWithoutTouchingOtherModules() throws Exception {
        var owned = fixture.addFileToProject("owned.workspace", "value").getVirtualFile();
        var foreign = fixture.addFileToProject("foreign.workspace", "value").getVirtualFile();
        start(true);
        var request = next();
        stream(request, report(owned, "old", null), report(foreign, "foreign", null));
        await(() -> wrapper.getClosedDocument(LSPIJUtils.toUri(owned), false) != null);
        await(() -> wrapper.getClosedDocument(LSPIJUtils.toUri(owned), false).getDiagnostics().size() == 1);
        assertNull(wrapper.getClosedDocument(LSPIJUtils.toUri(foreign), false));
        stream(request, report(owned, null, null));
        await(() -> wrapper.getClosedDocument(LSPIJUtils.toUri(owned), false).getDiagnostics().isEmpty());
    }

    public void testEmptyReportRetainsResultIdWithoutCreatingClosedCache() throws Exception {
        assertEmptyReportRetainsResultIdWithoutCreatingClosedCache(false);
    }

    public void testNullDiagnosticItemsAreTreatedAsEmpty() throws Exception {
        assertEmptyReportRetainsResultIdWithoutCreatingClosedCache(true);
    }

    private void assertEmptyReportRetainsResultIdWithoutCreatingClosedCache(boolean nullItems) throws Exception {
        var file = fixture.addFileToProject("owned.workspace", "value").getVirtualFile();
        var uri = LSPIJUtils.toUri(file);
        var empty = report(file, null, null);
        if (nullItems) {
            // Malformed JSON can bypass the non-null setter in LSP4J's report class.
            empty = new WorkspaceDocumentDiagnosticReport(JSONUtils.getLsp4jGson().fromJson(
                    "{\"kind\":\"full\",\"items\":null,\"uri\":\"" + uri + "\"}", WorkspaceFullDocumentDiagnosticReport.class));
            assertNull(empty.getLeft().getItems());
        }
        empty.getLeft().setResultId("empty");
        start(true);
        next().future.complete(new WorkspaceDiagnosticReport(List.of(empty)));
        var second = next();
        assertEquals(List.of(new PreviousResultId(uri.toString(), "empty")), second.params.getPreviousResultIds());
        assertNull(wrapper.getClosedDocument(uri, false));

        var nonempty = report(file, "diagnostic", null);
        nonempty.getLeft().setResultId("nonempty");
        second.future.complete(new WorkspaceDiagnosticReport(List.of(nonempty)));
        var third = next();
        var cached = wrapper.getClosedDocument(uri, false);
        assertNotNull(cached);
        assertEquals(1, cached.getDiagnostics().size());
        third.future.complete(new WorkspaceDiagnosticReport(List.of(empty)));
        assertEquals(List.of(new PreviousResultId(uri.toString(), "empty")), next().params.getPreviousResultIds());
        assertTrue(cached.getDiagnostics().isEmpty());
    }

    public void testWorkspaceReportsRespectDocumentMatcher() throws Exception {
        definition.allFilesEnabled = true; // routing is supplied only by DocumentMatcher
        LanguageServersRegistry.getInstance().registerAssociation(definition,
                new ServerFileNamePatternMapping(List.of("*.routed"), definition.getId(), null,
                        (file, project) -> file.getName().equals("owned.routed")));
        var owned = fixture.addFileToProject("owned.routed", "value").getVirtualFile();
        var rejected = fixture.addFileToProject("other.routed", "value").getVirtualFile();
        start(true);
        var request = next();
        stream(request, report(rejected, "must not leak", null), report(owned, "owned", null));
        request.future.complete(new WorkspaceDiagnosticReport(List.of()));
        next(); // prior streamed reports have been consumed
        assertNotNull(wrapper.getClosedDocument(LSPIJUtils.toUri(owned), false));
        assertNull(wrapper.getClosedDocument(LSPIJUtils.toUri(rejected), false));
        assertNull(wrapper.getOpenedDocument(LSPIJUtils.toUri(owned))); // never connect to filter
    }

    public void testWorkspaceReportsRespectAsyncDocumentMatcher() throws Exception {
        definition.allFilesEnabled = true;
        LanguageServersRegistry.getInstance().registerAssociation(definition,
                new ServerFileNamePatternMapping(List.of("*.routed"), definition.getId(), null, new DocumentMatcher() {
                    @Override public boolean match(@NotNull VirtualFile file, @NotNull Project project) {
                        throw new AssertionError("Async matcher must use matchAsync");
                    }
                    @Override public boolean shouldBeMatchedAsynchronously(@NotNull Project project) { return true; }
                    @Override public @NotNull CompletableFuture<Boolean> matchAsync(@NotNull VirtualFile file, @NotNull Project project) {
                        return CompletableFuture.completedFuture(file.getName().equals("owned.routed"));
                    }
                }));
        var owned = fixture.addFileToProject("owned.routed", "value").getVirtualFile();
        var rejected = fixture.addFileToProject("other.routed", "value").getVirtualFile();
        start(true);
        next().future.complete(new WorkspaceDiagnosticReport(List.of(
                report(rejected, "must not leak", null), report(owned, "owned", null))));
        next();
        assertNotNull(wrapper.getClosedDocument(LSPIJUtils.toUri(owned), false));
        assertNull(wrapper.getClosedDocument(LSPIJUtils.toUri(rejected), false));
    }

    public void testPendingMatcherDoesNotBlockRefreshOrPublishLateResults() throws Exception {
        var matching = new CompletableFuture<Boolean>();
        var matcherStarted = new CompletableFuture<Void>();
        registerAsyncMatcher(file -> {
            if (file.getName().equals("pending.routed")) {
                matcherStarted.complete(null);
                return matching;
            }
            return CompletableFuture.completedFuture(true);
        });
        var pending = fixture.addFileToProject("pending.routed", "value").getVirtualFile();
        var current = fixture.addFileToProject("current.routed", "value").getVirtualFile();
        start(true);
        var first = next();
        try {
            stream(first, report(pending, "stale", null));
            matcherStarted.get(5, TimeUnit.SECONDS);
            definition.client.refreshDiagnostics().get(5, TimeUnit.SECONDS);
            var refreshed = next();
            await(first.future::isCancelled);
            matching.complete(true);
            refreshed.future.complete(new WorkspaceDiagnosticReport(List.of(report(current, "current", null))));
            next(); // all reports from the completed request have been processed
            assertNull(wrapper.getClosedDocument(LSPIJUtils.toUri(pending), false));
            assertEquals("current", wrapper.getClosedDocument(LSPIJUtils.toUri(current), false)
                    .getDiagnostics().iterator().next().getMessage().getLeft());
        } finally {
            matching.complete(false); // release the old implementation when the regression fails
        }
    }

    public void testFailedMatcherDoesNotDropOtherReportsOrStopPulls() throws Exception {
        registerAsyncMatcher(file -> file.getName().equals("failed.routed")
                ? CompletableFuture.failedFuture(new IllegalStateException("matcher failed"))
                : CompletableFuture.completedFuture(true));
        var failed = fixture.addFileToProject("failed.routed", "value").getVirtualFile();
        var owned = fixture.addFileToProject("owned.routed", "value").getVirtualFile();
        start(true);
        next().future.complete(new WorkspaceDiagnosticReport(List.of(
                report(failed, "rejected", null), report(owned, "accepted", null))));
        next();
        assertNull(wrapper.getClosedDocument(LSPIJUtils.toUri(failed), false));
        assertEquals("accepted", wrapper.getClosedDocument(LSPIJUtils.toUri(owned), false)
                .getDiagnostics().iterator().next().getMessage().getLeft());
    }

    public void testLostMappingClearsOnlyRejectedFileForNamedProvider() throws Exception {
        assertLostMappingClearsOnlyRejectedFile("test", false);
    }

    public void testLostMappingClearsOnlyRejectedFileForDefaultProvider() throws Exception {
        assertLostMappingClearsOnlyRejectedFile(null, true);
    }

    private void assertLostMappingClearsOnlyRejectedFile(String identifier, boolean unchanged) throws Exception {
        var accepted = new java.util.concurrent.atomic.AtomicBoolean(true);
        registerAsyncMatcher(file -> CompletableFuture.completedFuture(
                accepted.get() || !file.getName().equals("rejected.routed")));
        var rejected = fixture.addFileToProject("rejected.routed", "value").getVirtualFile();
        var retained = fixture.addFileToProject("retained.routed", "value").getVirtualFile();
        start(false);
        wrapper.updateDiagnostics(rejected, "other", List.of(diagnostic("other provider")), null);
        var options = JSONUtils.getLsp4jGson().fromJson("{"
                + (identifier == null ? "" : "\"identifier\":\"" + identifier + "\",")
                + "\"workspaceDiagnostics\":true}", com.google.gson.JsonObject.class);
        definition.client.registerCapability(new RegistrationParams(List.of(
                new Registration("dynamic", "textDocument/diagnostic", options)))).get(5, TimeUnit.SECONDS);
        var first = next();
        assertEquals(identifier, first.params.getIdentifier());
        var errorReport = report(rejected, "stale", null);
        errorReport.getLeft().setResultId("old");
        errorReport.getLeft().getItems().get(0).setSeverity(DiagnosticSeverity.Error);
        first.future.complete(new WorkspaceDiagnosticReport(List.of(errorReport, report(retained, "retained", null))));
        var second = next(); // both reports are cached before the mapping changes
        var rejectedDocument = wrapper.getClosedDocument(LSPIJUtils.toUri(rejected), false);
        assertEquals(2, rejectedDocument.getDiagnostics().size());
        assertTrue(rejectedDocument.hasErrors());
        accepted.set(false);
        wrapper.incrementModificationCount();
        second.future.complete(new WorkspaceDiagnosticReport(List.of(unchanged
                ? new WorkspaceDocumentDiagnosticReport(new WorkspaceUnchangedDocumentDiagnosticReport(
                        "old", LSPIJUtils.toUri(rejected).toString(), null))
                : report(rejected, "must not replace", null))));
        assertTrue(next().params.getPreviousResultIds().isEmpty());
        assertEquals(1, rejectedDocument.getDiagnostics().size());
        assertEquals("other provider", rejectedDocument.getDiagnostics().iterator().next().getMessage().getLeft());
        assertFalse(rejectedDocument.hasErrors());
        assertEquals("retained", wrapper.getClosedDocument(LSPIJUtils.toUri(retained), false)
                .getDiagnostics().iterator().next().getMessage().getLeft());
    }

    public void testUnchangedReportsReuseMatchesUntilDefinitionChanges() throws Exception {
        assertUnchangedMatchInvalidation(file -> wrapper.incrementModificationCount());
    }

    public void testUnchangedReportsRecheckAfterUnsavedEdit() throws Exception {
        assertUnchangedMatchInvalidation(file -> WriteCommandAction.runWriteCommandAction(fixture.getProject(),
                () -> FileDocumentManager.getInstance().getDocument(file).setText("changed")));
    }

    public void testUnrelatedUnsavedEditsDoNotInvalidateMatching() throws Exception {
        var other = fixture.addFileToProject("other.routed", "value").getVirtualFile();
        var document = FileDocumentManager.getInstance().getDocument(other);
        assertNotNull(document);
        assertUnchangedMatchInvalidation(file -> WriteCommandAction.runWriteCommandAction(fixture.getProject(),
                () -> document.setText("changed")), false);
    }

    public void testUnchangedReportsRecheckAfterFileDeletion() throws Exception {
        assertUnchangedMatchInvalidation(file ->
                WriteCommandAction.writeCommandAction(fixture.getProject()).run(() -> file.delete(this)));
    }

    public void testUnchangedReportsRecheckAfterAccessorChanges() throws Exception {
        assertUnchangedMatchInvalidation(file -> {
            var other = new MockLanguageServerDefinition("other-workspace", false);
            LanguageServersRegistry.getInstance().addServerDefinition(fixture.getProject(), other,
                    List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.other"), null)));
            LanguageServersRegistry.getInstance().removeServerDefinition(fixture.getProject(), other);
        });
    }

    private void assertUnchangedMatchInvalidation(MatchingChange change) throws Exception {
        assertUnchangedMatchInvalidation(change, true);
    }

    private void assertUnchangedMatchInvalidation(MatchingChange change, boolean invalidated) throws Exception {
        var accepted = new java.util.concurrent.atomic.AtomicBoolean(true);
        var matches = new java.util.concurrent.atomic.AtomicInteger();
        registerAsyncMatcher(file -> {
            matches.incrementAndGet();
            return CompletableFuture.completedFuture(accepted.get());
        });
        var file = fixture.addFileToProject("cached.routed", "value").getVirtualFile();
        // Load the document before caching a match, so the unsaved-edit test
        // changes only its text, not the set of PSI files in the project.
        assertNotNull(FileDocumentManager.getInstance().getDocument(file));
        String uri = LSPIJUtils.toUri(file).toString();
        start(true);
        var full = report(file, "cached", null);
        full.getLeft().setResultId("one");
        next().future.complete(new WorkspaceDiagnosticReport(List.of(full)));
        var second = next();
        int initialMatches = matches.get();
        assertTrue(initialMatches > 0);
        var unchanged = new WorkspaceDiagnosticReport(List.of(new WorkspaceDocumentDiagnosticReport(
                new WorkspaceUnchangedDocumentDiagnosticReport("one", uri, null))));
        second.future.complete(unchanged);
        var third = next();
        assertEquals("An unchanged report must reuse matching", initialMatches, matches.get());
        assertEquals(List.of(new PreviousResultId(uri, "one")), third.params.getPreviousResultIds());
        accepted.set(false);
        change.run(file);
        third.future.complete(unchanged);
        if (!invalidated) {
            assertEquals(List.of(new PreviousResultId(uri, "one")), next().params.getPreviousResultIds());
            assertEquals("Editing another file must not invalidate this match", initialMatches, matches.get());
            return;
        }
        assertTrue("Changed/deleted files must lose their retained match and result ID",
                next().params.getPreviousResultIds().isEmpty());
        if (file.isValid()) {
            assertTrue("The matcher must be rechecked after invalidation", matches.get() > initialMatches);
            assertTrue(wrapper.getClosedDocument(LSPIJUtils.toUri(file), false).getDiagnostics().isEmpty());
        }
    }

    private interface MatchingChange {
        void run(VirtualFile file) throws Exception;
    }

    public void testNamedProviderUnregistrationClearsOpenAndClosedDiagnostics() throws Exception {
        assertProviderUnregistrationClearsOpenAndClosedDiagnostics("dynamic");
    }

    public void testDefaultProviderUnregistrationClearsOpenAndClosedDiagnostics() throws Exception {
        assertProviderUnregistrationClearsOpenAndClosedDiagnostics(null);
    }

    private void assertProviderUnregistrationClearsOpenAndClosedDiagnostics(String identifier) throws Exception {
        definition.allFilesEnabled = true;
        var first = fixture.addFileToProject("first.workspace", "value").getVirtualFile();
        var second = fixture.addFileToProject("second.workspace", "value").getVirtualFile();
        start(false);
        wrapper.updateDiagnostics(first, "other", List.of(diagnostic("retained")), null);
        // Keep document pulls on a different provider so workspace reports are
        // cached alongside them, rather than triggering another document pull.
        var documentOptions = JSONUtils.getLsp4jGson().fromJson(
                "{\"identifier\":\"document\",\"workspaceDiagnostics\":false}", com.google.gson.JsonObject.class);
        definition.client.registerCapability(new RegistrationParams(List.of(
                new Registration("document", "textDocument/diagnostic", documentOptions)))).get(5, TimeUnit.SECONDS);
        definition.getServer().setPullDiagnostics(new DocumentDiagnosticReport(
                new RelatedFullDocumentDiagnosticReport(List.of(diagnostic("document retained")))));
        var open = fixture.configureByText("open.workspace", "value").getVirtualFile();
        LanguageServiceAccessor.getInstance(fixture.getProject()).getLanguageServers(fixture.getFile(), null, null)
                .get(10, TimeUnit.SECONDS);
        await(() -> wrapper.getOpenedDocument(LSPIJUtils.toUri(open)) != null
                && wrapper.getOpenedDocument(LSPIJUtils.toUri(open)).getDiagnostics().size() == 1);
        var openDocument = wrapper.getOpenedDocument(LSPIJUtils.toUri(open));
        var options = JSONUtils.getLsp4jGson().fromJson("{"
                + (identifier == null ? "" : "\"identifier\":\"" + identifier + "\",")
                + "\"workspaceDiagnostics\":true}", com.google.gson.JsonObject.class);
        definition.client.registerCapability(new RegistrationParams(List.of(
                new Registration("dynamic", "textDocument/diagnostic", options)))).get(5, TimeUnit.SECONDS);
        var request = next();
        assertEquals(identifier, request.params.getIdentifier());
        assertEquals("document", wrapper.getClientFeatures().getDiagnosticFeature().getDiagnosticIdentifier());
        var errorReport = report(first, "removed", null);
        errorReport.getLeft().getItems().get(0).setSeverity(DiagnosticSeverity.Error);
        var openErrorReport = report(open, "removed from editor", null);
        openErrorReport.getLeft().getItems().get(0).setSeverity(DiagnosticSeverity.Error);
        stream(request, errorReport, report(second, "removed too", null), openErrorReport);
        await(() -> wrapper.getClosedDocument(LSPIJUtils.toUri(second), false) != null
                && wrapper.getClosedDocument(LSPIJUtils.toUri(second), false).getDiagnostics().size() == 1);
        await(() -> openDocument.getDiagnostics().size() == 2);
        var firstDocument = wrapper.getClosedDocument(LSPIJUtils.toUri(first), false);
        var secondDocument = wrapper.getClosedDocument(LSPIJUtils.toUri(second), false);
        assertEquals(2, firstDocument.getDiagnostics().size());
        assertTrue(firstDocument.hasErrors());
        assertTrue(openDocument.hasErrors());
        definition.client.unregisterCapability(new UnregistrationParams(List.of(
                new Unregistration("dynamic", "textDocument/diagnostic")))).get(5, TimeUnit.SECONDS);
        await(request.future::isCancelled);
        await(() -> firstDocument.getDiagnostics().size() == 1 && secondDocument.getDiagnostics().isEmpty());
        assertEquals("retained", firstDocument.getDiagnostics().iterator().next().getMessage().getLeft());
        assertFalse(firstDocument.hasErrors());
        await(() -> openDocument.getDiagnostics().size() == 1);
        assertEquals("document retained", openDocument.getDiagnostics().iterator().next().getMessage().getLeft());
        assertFalse(openDocument.hasErrors());
    }

    private void registerAsyncMatcher(java.util.function.Function<VirtualFile, CompletableFuture<Boolean>> matcher) {
        definition.allFilesEnabled = true;
        LanguageServersRegistry.getInstance().registerAssociation(definition,
                new ServerFileNamePatternMapping(List.of("*.routed"), definition.getId(), null, new DocumentMatcher() {
                    @Override public boolean match(@NotNull VirtualFile file, @NotNull Project project) {
                        throw new AssertionError("Async matcher must use matchAsync");
                    }
                    @Override public boolean shouldBeMatchedAsynchronously(@NotNull Project project) { return true; }
                    @Override public @NotNull CompletableFuture<Boolean> matchAsync(@NotNull VirtualFile file, @NotNull Project project) {
                        return matcher.apply(file);
                    }
                }));
    }

    public void testWorkspaceRemovalPreservesNamedDocumentProvider() throws Exception {
        assertWorkspaceRemovalPreservesDocumentProvider("test");
    }

    public void testWorkspaceRemovalPreservesDefaultDocumentProvider() throws Exception {
        assertWorkspaceRemovalPreservesDocumentProvider(null);
    }

    private void assertWorkspaceRemovalPreservesDocumentProvider(String identifier) throws Exception {
        definition.allFilesEnabled = true;
        var staticOptions = new DiagnosticRegistrationOptions();
        staticOptions.setIdentifier(identifier);
        staticOptions.setWorkspaceDiagnostics(false);
        definition.getServer().getInitializeResult().getCapabilities().setDiagnosticProvider(staticOptions);
        definition.getServer().setPullDiagnostics(new DocumentDiagnosticReport(
                new RelatedFullDocumentDiagnosticReport(List.of(diagnostic("document retained")))));
        wrapper = LanguageServerManager.getInstance(fixture.getProject()).getLanguageServer(definition.getId())
                .get(10, TimeUnit.SECONDS).getServerWrapper();
        var open = fixture.configureByText("open.workspace", "value").getVirtualFile();
        LanguageServiceAccessor.getInstance(fixture.getProject()).getLanguageServers(fixture.getFile(), null, null)
                .get(10, TimeUnit.SECONDS);
        await(() -> wrapper.getOpenedDocument(LSPIJUtils.toUri(open)) != null
                && wrapper.getOpenedDocument(LSPIJUtils.toUri(open)).getDiagnostics().size() == 1);
        var openDocument = wrapper.getOpenedDocument(LSPIJUtils.toUri(open));
        var closed = fixture.addFileToProject("closed.workspace", "value").getVirtualFile();
        var options = JSONUtils.getLsp4jGson().fromJson("{"
                + (identifier == null ? "" : "\"identifier\":\"" + identifier + "\",")
                + "\"workspaceDiagnostics\":true}", com.google.gson.JsonObject.class);
        definition.client.registerCapability(new RegistrationParams(List.of(
                new Registration("workspace", "textDocument/diagnostic", options)))).get(5, TimeUnit.SECONDS);
        var request = next();
        stream(request, report(closed, "workspace removed", null));
        await(() -> wrapper.getClosedDocument(LSPIJUtils.toUri(closed), false) != null
                && wrapper.getClosedDocument(LSPIJUtils.toUri(closed), false).getDiagnostics().size() == 1);
        definition.client.unregisterCapability(new UnregistrationParams(List.of(
                new Unregistration("workspace", "textDocument/diagnostic")))).get(5, TimeUnit.SECONDS);
        await(request.future::isCancelled);
        await(() -> wrapper.getClosedDocument(LSPIJUtils.toUri(closed), false).getDiagnostics().isEmpty());
        // Also check the cleanup synchronously: observing the closed cache alone
        // does not guarantee that the asynchronous open-cache loop has finished.
        wrapper.clearDocumentDiagnostics(identifier);
        assertEquals(1, openDocument.getDiagnostics().size());
        assertEquals("document retained", openDocument.getDiagnostics().iterator().next().getMessage().getLeft());
    }

    public void testWorkspaceResultsRefreshOpenDocumentWithoutReplacingDocumentDiagnostics() throws Exception {
        start(true);
        var initial = next();
        definition.getServer().setPullDiagnostics(new DocumentDiagnosticReport(
                new RelatedFullDocumentDiagnosticReport(List.of(diagnostic("document")))));
        var file = fixture.configureByText("owned.workspace", "value").getVirtualFile();
        LanguageServiceAccessor.getInstance(fixture.getProject()).getLanguageServers(fixture.getFile(), null, null)
                .get(10, TimeUnit.SECONDS);
        await(() -> wrapper.getOpenedDocument(LSPIJUtils.toUri(file)) != null
                && wrapper.getOpenedDocument(LSPIJUtils.toUri(file)).getDiagnostics().size() == 1);
        initial.future.complete(new WorkspaceDiagnosticReport(List.of()));
        var request = next(); // the new request uses the current editor cache
        definition.getServer().setPullDiagnostics(new DocumentDiagnosticReport(
                new RelatedFullDocumentDiagnosticReport(List.of(diagnostic("updated document")))));
        stream(request, report(file, "workspace", 1));
        await(() -> wrapper.getOpenedDocument(LSPIJUtils.toUri(file)).getDiagnostics().stream()
                .anyMatch(d -> "updated document".equals(d.getMessage().getLeft())));
        assertEquals(1, wrapper.getOpenedDocument(LSPIJUtils.toUri(file)).getDiagnostics().size());
    }

    public void testCloseFiltersWorkspacePullWithoutPreviousResultId() throws Exception {
        definition.keepAlive = true;
        var psi = fixture.addFileToProject("owned.workspace", "value");
        var file = psi.getVirtualFile();
        definition.getServer().setPullDiagnostics(new DocumentDiagnosticReport(
                new RelatedFullDocumentDiagnosticReport(List.of(diagnostic("document")))));
        start(false);
        var editors = FileEditorManager.getInstance(fixture.getProject());
        editors.openFile(file, false);
        LanguageServiceAccessor.getInstance(fixture.getProject()).getLanguageServers(psi, null, null)
                .get(10, TimeUnit.SECONDS);
        var options = JSONUtils.getLsp4jGson().fromJson(
                "{\"identifier\":\"workspace\",\"workspaceDiagnostics\":true}", com.google.gson.JsonObject.class);
        definition.client.registerCapability(new RegistrationParams(List.of(
                new Registration("workspace", "textDocument/diagnostic", options)))).get(5, TimeUnit.SECONDS);
        var pending = next();
        assertTrue(pending.params.getPreviousResultIds().isEmpty());
        editors.closeFile(file);
        fixture.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
                .fileClosed(editors, file);
        PlatformTestUtil.waitWithEventsDispatching("the closed editor must disconnect",
                () -> wrapper.getOpenedDocument(LSPIJUtils.toUri(file)) == null, 10);
        var stale = report(file, "stale", null);
        stale.getLeft().setResultId("stale");
        stream(pending, stale); // late progress must not populate the new closed-file cache
        pending.future.complete(new WorkspaceDiagnosticReport(List.of(stale)));
        var replacement = next();
        assertFalse(pending.future.isCancelled());
        assertTrue(replacement.params.getPreviousResultIds().isEmpty());
        var closed = wrapper.getClosedDocument(LSPIJUtils.toUri(file), false);
        assertTrue(closed == null || closed.getDiagnostics().isEmpty());
        var fresh = report(file, "fresh", null);
        fresh.getLeft().setResultId("fresh");
        replacement.future.complete(new WorkspaceDiagnosticReport(List.of(fresh)));
        assertEquals(List.of(new PreviousResultId(LSPIJUtils.toUri(file).toString(), "fresh")),
                next().params.getPreviousResultIds());
        var diagnostics = wrapper.getClosedDocument(LSPIJUtils.toUri(file), false).getDiagnostics();
        assertEquals(1, diagnostics.size());
        assertEquals("fresh", diagnostics.iterator().next().getMessage().getLeft());
    }

    public void testCacheLifetimeForSameProvider() throws Exception {
        assertCacheLifetime("document");
    }

    public void testCacheLifetimeForOtherProvider() throws Exception {
        assertCacheLifetime("workspace");
    }

    public void testCacheLifetimeForDefaultProvider() throws Exception {
        assertCacheLifetime(null);
    }

    public void testCacheLifetimeForEncodedUri() throws Exception {
        assertCacheLifetime("workspace", true, false);
    }

    public void testCacheLifetimeForUnsynchronizedCache() throws Exception {
        assertCacheLifetime("workspace", false, true);
    }

    private void assertCacheLifetime(String identifier) throws Exception {
        assertCacheLifetime(identifier, false, false);
    }

    private void assertCacheLifetime(String identifier, boolean encodedUri, boolean unsynchronized) throws Exception {
        definition.allFilesEnabled = true;
        definition.keepAlive = true;
        var psi = fixture.addFileToProject("owned.workspace", "value");
        var file = psi.getVirtualFile();
        var retained = fixture.addFileToProject("retained.workspace", "value").getVirtualFile();
        start(false);
        definition.getServer().setPullDiagnostics(new DocumentDiagnosticReport(
                new RelatedFullDocumentDiagnosticReport(List.of(diagnostic("document")))));
        var documentOptions = JSONUtils.getLsp4jGson().fromJson(
                "{\"identifier\":\"document\",\"workspaceDiagnostics\":false}", com.google.gson.JsonObject.class);
        var workspaceOptions = JSONUtils.getLsp4jGson().fromJson("{"
                + (identifier == null ? "" : "\"identifier\":\"" + identifier + "\",")
                + "\"workspaceDiagnostics\":true}", com.google.gson.JsonObject.class);
        definition.client.registerCapability(new RegistrationParams(List.of(
                new Registration("document", "textDocument/diagnostic", documentOptions),
                new Registration("workspace", "textDocument/diagnostic", workspaceOptions)))).get(5, TimeUnit.SECONDS);
        var first = next();
        var ownedReport = report(file, "workspace", null);
        ownedReport.getLeft().setResultId("owned");
        if (encodedUri) {
            ownedReport.getLeft().setUri(ownedReport.getLeft().getUri().replace("/owned.workspace", "/%6fwned.workspace"));
        }
        var retainedReport = report(retained, "retained", null);
        retainedReport.getLeft().setResultId("retained");
        first.future.complete(new WorkspaceDiagnosticReport(List.of(ownedReport, retainedReport)));
        var closedPull = next();
        assertEquals(2, closedPull.params.getPreviousResultIds().size());

        if (unsynchronized) {
            // Published diagnostics can create an editor cache before didOpen
            // connects it; replacing that cache must invalidate its IDs too.
            assertNull(wrapper.getOpenedDocument(LSPIJUtils.toUri(file), true).getSynchronizer());
            var unsynchronizedPull = next();
            await(closedPull.future::isCancelled);
            assertEquals(List.of(new PreviousResultId(LSPIJUtils.toUri(retained).toString(), "retained")),
                    unsynchronizedPull.params.getPreviousResultIds());
            unsynchronizedPull.future.complete(new WorkspaceDiagnosticReport(List.of(ownedReport)));
            closedPull = next();
            assertEquals(2, closedPull.params.getPreviousResultIds().size());
        }

        var editors = FileEditorManager.getInstance(fixture.getProject());
        editors.openFile(file, false);
        LanguageServiceAccessor.getInstance(fixture.getProject()).getLanguageServers(psi, null, null)
                .get(10, TimeUnit.SECONDS);
        var openPull = next();
        await(closedPull.future::isCancelled);
        assertEquals(List.of(new PreviousResultId(LSPIJUtils.toUri(retained).toString(), "retained")),
                openPull.params.getPreviousResultIds());
        openPull.future.complete(new WorkspaceDiagnosticReport(List.of(ownedReport)));
        var stableOpenPull = next();
        assertEquals(2, stableOpenPull.params.getPreviousResultIds().size());
        await(() -> wrapper.getOpenedDocument(LSPIJUtils.toUri(file)).getDiagnostics().size()
                == ("document".equals(identifier) ? 1 : 2));

        editors.closeFile(file);
        // The fixture has no editor provider for this extension, so deliver
        // the public close event explicitly, as FileEditorManager does in the IDE.
        fixture.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
                .fileClosed(editors, file);
        PlatformTestUtil.waitWithEventsDispatching("the closed editor must disconnect",
                () -> wrapper.getOpenedDocument(LSPIJUtils.toUri(file)) == null, 10);
        var refreshed = next();
        await(stableOpenPull.future::isCancelled);
        assertEquals(List.of(new PreviousResultId(LSPIJUtils.toUri(retained).toString(), "retained")),
                refreshed.params.getPreviousResultIds());
        refreshed.future.complete(new WorkspaceDiagnosticReport(List.of(ownedReport)));
        var stableClosedPull = next();
        assertEquals(2, stableClosedPull.params.getPreviousResultIds().size());
        stableClosedPull.future.complete(new WorkspaceDiagnosticReport(List.of(new WorkspaceDocumentDiagnosticReport(
                new WorkspaceUnchangedDocumentDiagnosticReport("owned", ownedReport.getLeft().getUri(), null)))));
        assertEquals(2, next().params.getPreviousResultIds().size());
        assertEquals("workspace", wrapper.getClosedDocument(LSPIJUtils.toUri(file), false)
                .getDiagnostics().iterator().next().getMessage().getLeft());
    }

    public void testOlderWorkspaceVersionCannotRefreshCurrentDocument() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        definition.getServer().setTextDocumentService(new com.redhat.devtools.lsp4ij.mock.MockTextDocumentService(
                definition.getServer()::buildMaybeDelayedFuture) {
            @Override public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
                count.incrementAndGet();
                return CompletableFuture.completedFuture(new DocumentDiagnosticReport(
                        new RelatedFullDocumentDiagnosticReport(List.of(WorkspaceDiagnosticsTest.diagnostic("current")))));
            }
        });
        start(true);
        var initial = next();
        var file = fixture.configureByText("owned.workspace", "value").getVirtualFile();
        LanguageServiceAccessor.getInstance(fixture.getProject()).getLanguageServers(fixture.getFile(), null, null)
                .get(10, TimeUnit.SECONDS);
        await(() -> wrapper.getOpenedDocument(LSPIJUtils.toUri(file)) != null
                && wrapper.getOpenedDocument(LSPIJUtils.toUri(file)).getDiagnostics().size() == 1);
        int before = count.get();
        initial.future.complete(new WorkspaceDiagnosticReport(List.of()));
        var request = next(); // the new request uses the current editor cache
        stream(request, report(file, "stale", 0));
        request.future.complete(new WorkspaceDiagnosticReport(List.of()));
        next(); // the previous report has been consumed before the next request
        assertEquals(before, count.get());
        assertEquals("current", wrapper.getOpenedDocument(LSPIJUtils.toUri(file)).getDiagnostics().iterator().next().getMessage().getLeft());
    }

    public void testDynamicRegistrationStartsAndUnregistrationCancels() throws Exception {
        start(false);
        assertNull(requests.poll(150, TimeUnit.MILLISECONDS));
        var options = JSONUtils.getLsp4jGson().fromJson(
                "{\"identifier\":\"dynamic\",\"workspaceDiagnostics\":true,\"interFileDependencies\":true}",
                com.google.gson.JsonObject.class);
        definition.client.registerCapability(new RegistrationParams(List.of(
                new Registration("dynamic", "textDocument/diagnostic", options)))).get(5, TimeUnit.SECONDS);
        var request = next();
        assertEquals("dynamic", request.params.getIdentifier());
        definition.client.unregisterCapability(new UnregistrationParams(List.of(
                new Unregistration("dynamic", "textDocument/diagnostic")))).get(5, TimeUnit.SECONDS);
        await(request.future::isCancelled);
        assertNull(requests.poll(150, TimeUnit.MILLISECONDS));
    }

    public void testRefreshAndStopCancelRequestsThroughTransport() throws Exception {
        start(true);
        var first = next();
        definition.getServer().getRemoteProxies().get(0).refreshDiagnostics().get(5, TimeUnit.SECONDS);
        var second = next();
        await(first.future::isCancelled);
        wrapper.stop().get(10, TimeUnit.SECONDS);
        await(second.future::isCancelled);
    }

    private Request next() throws InterruptedException {
        var request = requests.poll(10, TimeUnit.SECONDS);
        assertNotNull("Expected workspace diagnostic request", request);
        return request;
    }

    private void stream(Request request, WorkspaceDocumentDiagnosticReport... reports) {
        definition.getServer().getRemoteProxies().get(0).notifyProgress(new ProgressParams(
                request.params.getPartialResultToken(), Either.forRight(new WorkspaceDiagnosticReport(List.of(reports)))));
    }

    private static Diagnostic diagnostic(String message) {
        return new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), message, DiagnosticSeverity.Warning, "test");
    }

    private static WorkspaceDocumentDiagnosticReport report(VirtualFile file, String message, Integer version) {
        var report = new WorkspaceFullDocumentDiagnosticReport(
                message == null ? List.of() : List.of(diagnostic(message)), LSPIJUtils.toUri(file).toString(), version);
        return new WorkspaceDocumentDiagnosticReport(report);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            assertTrue("Timed out waiting for diagnostic update", System.nanoTime() < deadline);
            Thread.sleep(10);
        }
    }

    private record Request(WorkspaceDiagnosticParams params, CompletableFuture<WorkspaceDiagnosticReport> future) { }

    private static class ScopedServer extends MockLanguageServerDefinition {
        LanguageClientImpl client;
        boolean allFilesEnabled;
        boolean keepAlive;
        ScopedServer() { super("workspace-diagnostics", false); }
        @Override public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
            return client = new LanguageClientImpl(project);
        }
        @Override public @NotNull LSPClientFeatures createClientFeatures() {
            return new LSPClientFeatures() {
                @Override public boolean isEnabled(@NotNull VirtualFile file) {
                    return allFilesEnabled || file.getName().equals("owned.workspace");
                }
                @Override public boolean keepServerAlive() { return keepAlive; }
            };
        }
    }
}
