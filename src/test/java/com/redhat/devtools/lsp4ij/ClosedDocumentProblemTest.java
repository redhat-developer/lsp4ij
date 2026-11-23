/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.codeInsight.daemon.impl.MockWolfTheProblemSolver;
import com.intellij.codeInsight.daemon.impl.WolfTheProblemSolverImpl;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Verifies that the Project view's problem files follow the errors of a file as it opens and closes: LSP4IJ drops the
 * file's closed document when it opens, and clears its problem when it closes.
 */
public class ClosedDocumentProblemTest extends LSPCodeInsightFixtureTestCase {

    // set if the clear that testAClearMadeWhileTheProjectIsDisposedNeverRuns submits runs
    private final AtomicBoolean clearRan = new AtomicBoolean();

    public ClosedDocumentProblemTest() {
        super("*.foo");
    }

    public void testAFileThatOpensWithItsErrorsFixedIsNoProblemFile() throws Exception {
        var problems = problemFiles();
        // A first file starts the language server; the second stays closed. The bound only ends a hang.
        var main = myFixture.configureByText("main.foo", "main");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(main, null, null).get(1, TimeUnit.HOURS).get(0).getServerWrapper();
        var other = myFixture.addFileToProject("other.foo", "other").getVirtualFile();

        // The server's error for the closed file makes it a problem file, which LSP4IJ reports in a read action of
        // its own
        var error = new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "an error", DiagnosticSeverity.Error, "mock");
        wrapper.updateDiagnostics(other, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assertTrue("other.foo must be a problem file", problems.isProblemFile(other));

        // An edit elsewhere fixed the error, and the file opens before the server publishes for it again: its closed
        // document is dropped, and its opened document has no errors
        wrapper.connect(other, new LanguageServerWrapper.LSPFileConnectionInfo(null, null, null, true))
                .get(1, TimeUnit.HOURS);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assertFalse("other.foo must be no problem file once it opens without errors", problems.isProblemFile(other));
    }

    public void testAFileWhoseOpenedDocumentHeldOnlyErrorsIsNoProblemFileOnceItConnectsWithoutErrors() throws Exception {
        var problems = problemFiles();
        var main = myFixture.configureByText("main.foo", "main");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(main, null, null).get(1, TimeUnit.HOURS).get(0).getServerWrapper();
        var other = myFixture.addFileToProject("other.foo", "other").getVirtualFile();

        // The server's error for a file that is open in an editor, but not connected yet: an opened document without
        // synchronization holds it, and the file is a problem file
        wrapper.getOpenedDocument(wrapper.toUri(other), true);
        var error = new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "an error", DiagnosticSeverity.Error, "mock");
        wrapper.updateDiagnostics(other, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assertTrue("other.foo must be a problem file", problems.isProblemFile(other));

        // The connection replaces that opened document with one that has no errors
        wrapper.connect(other, new LanguageServerWrapper.LSPFileConnectionInfo(null, null, null, true))
                .get(1, TimeUnit.HOURS);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assertFalse("other.foo must be no problem file once it connects without errors", problems.isProblemFile(other));
    }

    public void testAFileThatClosesIsNoProblemFileUntilTheServerPublishesErrorsForItsClosedDocument() throws Exception {
        var problems = problemFiles();
        var main = myFixture.configureByText("main.foo", "main");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(main, null, null).get(1, TimeUnit.HOURS).get(0).getServerWrapper();
        var other = myFixture.addFileToProject("other.foo", "other").getVirtualFile();
        var error = new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "an error", DiagnosticSeverity.Error, "mock");

        // The opened file's error makes it a problem file
        wrapper.connect(other, new LanguageServerWrapper.LSPFileConnectionInfo(null, null, null, true))
                .get(1, TimeUnit.HOURS);
        wrapper.updateDiagnostics(other, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
        waitForProblemReports();
        assertTrue("other.foo must be a problem file", problems.isProblemFile(other));

        // As it closes, nothing holds an error for it
        wrapper.disconnect(other, false);
        waitForProblemReports();
        assertFalse("other.foo must be no problem file once it closes", problems.isProblemFile(other));

        // The server publishes the error again, for the closed document
        wrapper.updateDiagnostics(other, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
        waitForProblemReports();
        assertTrue("other.foo must be a problem file once its closed document has an error", problems.isProblemFile(other));
    }

    public void testAClosedFileWithErrorsStaysAProblemFileWhenTheClearOfItsCloseRunsAfterTheirReport() throws Exception {
        var problems = problemFiles();
        var main = myFixture.configureByText("main.foo", "main");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(main, null, null).get(1, TimeUnit.HOURS).get(0).getServerWrapper();
        var other = myFixture.addFileToProject("other.foo", "other").getVirtualFile();

        // The clear that a file's close submits, while its closed document has no errors yet. It asks the client's
        // canReportProblem in its background read action, which holds it there until it is released; the wait gives
        // way to a write action, after which the clear runs again.
        var release = new CountDownLatch(1);
        var holdingFeatures = new LSPClientFeatures().setDiagnosticFeature(new LSPDiagnosticFeature() {
            @Override
            public boolean canReportProblem(@NotNull VirtualFile file) {
                ProgressIndicatorUtils.awaitWithCheckCanceled(release);
                return true;
            }
        });
        try {
            LSPDiagnosticUtils.clearProblem(Set.of(wrapper.toUri(other)), holdingFeatures, myFixture.getProject());

            // The server publishes an error for the closed document, whose report makes the file a problem file while
            // the clear is still pending. The bound only ends a hang.
            var error = new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "an error", DiagnosticSeverity.Error, "mock");
            wrapper.updateDiagnostics(other, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
            PlatformTestUtil.waitWithEventsDispatching("other.foo must be a problem file",
                    () -> problems.isProblemFile(other), 3600);
        } finally {
            release.countDown();
        }

        // The clear runs last, as nothing orders it before the report
        waitForProblemReports();
        assertTrue("other.foo must stay a problem file while its closed document has an error", problems.isProblemFile(other));
    }

    public void testAFileWhoseErrorIsPublishedWhileAClearReportsAnEarlierFileStaysAProblemFileWhenTheClearReachesIt() throws Exception {
        var problems = problemFiles();
        var project = myFixture.getProject();
        var first = myFixture.addFileToProject("first.foo", "first").getVirtualFile();
        var second = myFixture.addFileToProject("second.foo", "second").getVirtualFile();
        var third = myFixture.addFileToProject("third.foo", "third").getVirtualFile();
        // A second server for the files, whose client doesn't report their problems. Each report of a file that no
        // server holds errors for asks it whether it does: it holds the report of first.foo until it is released, and
        // tells when third.foo is reported. The wait gives way to a write action, after which the report runs again.
        var firstHeld = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var thirdReported = new CountDownLatch(1);
        var observing = new MockLanguageServerDefinition("problem-reports-observed", false) {
            @Override
            public @NotNull LSPClientFeatures createClientFeatures() {
                return new LSPClientFeatures().setDiagnosticFeature(new LSPDiagnosticFeature() {
                    @Override
                    public boolean canReportProblem(@NotNull VirtualFile file) {
                        if (file.equals(first)) {
                            firstHeld.countDown();
                            ProgressIndicatorUtils.awaitWithCheckCanceled(release);
                        } else if (file.equals(third)) {
                            thirdReported.countDown();
                        }
                        return false;
                    }
                });
            }
        };
        LanguageServersRegistry.getInstance().addServerDefinition(project, observing,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null)));
        try {
            var main = myFixture.configureByText("main.foo", "main");
            var wrapper = LanguageServiceAccessor.getInstance(project).getLanguageServers(main, null, null)
                    .get(1, TimeUnit.HOURS).stream().map(LanguageServerItem::getServerWrapper)
                    .filter(w -> w.getServerDefinition() != observing).findFirst().orElseThrow();
            try {
                // A clear of the three files, which reports them in this order. The bounds only end a hang.
                var fileUris = new LinkedHashSet<>(List.of(wrapper.toUri(first), wrapper.toUri(second), wrapper.toUri(third)));
                LSPDiagnosticUtils.clearProblem(fileUris, wrapper.getClientFeatures(), project);
                PlatformTestUtil.waitWithEventsDispatching("the clear must report first.foo",
                        () -> firstHeld.getCount() == 0, 3600);

                // While the clear reports first.foo, the server publishes an error for the closed document of
                // second.foo, whose report makes it a problem file
                var error = new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "an error", DiagnosticSeverity.Error, "mock");
                wrapper.updateDiagnostics(second, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
                PlatformTestUtil.waitWithEventsDispatching("second.foo must be a problem file",
                        () -> problems.isProblemFile(second), 3600);
            } finally {
                release.countDown();
            }

            // The clear goes on with second.foo, and is done with it once it reports third.foo
            PlatformTestUtil.waitWithEventsDispatching("the clear must report third.foo",
                    () -> thirdReported.getCount() == 0, 3600);
            waitForProblemReports();
            assertTrue("second.foo must stay a problem file while its closed document has an error", problems.isProblemFile(second));
        } finally {
            observing.getServer().waitBeforeTearDown();
            LanguageServersRegistry.getInstance().removeServerDefinition(project, observing);
        }
    }

    public void testAServerThatStopsWithManyClosedDocumentsWithErrorsClearsTheirProblemsOneFileAfterAnother() throws Exception {
        var problems = problemFiles();
        var project = myFixture.getProject();
        // More closed files than IntelliJ lets non-blocking read actions be submitted at once
        var files = IntStream.range(0, 150)
                .mapToObj(i -> myFixture.addFileToProject("closed" + i + ".foo", "closed").getVirtualFile())
                .toList();
        var fileUris = files.stream()
                .map(file -> FileUriSupport.DEFAULT.getFileUri(file).toASCIIString())
                .collect(Collectors.toUnmodifiableSet());
        // While the server stops, its client counts the files that the clear of its closed documents resolves, and
        // holds each report of the files until the clear has resolved them all: a report asks each started server,
        // this one among them, for the URI of the file. A clear that submitted a report of each file as it resolved
        // it would have them all active at once, as on a busy machine. The wait gives way to a write action.
        var stopping = new AtomicBoolean();
        var resolved = new CountDownLatch(files.size());
        var running = new AtomicInteger();
        var mostRunning = new AtomicInteger();
        var stoppingServer = new MockLanguageServerDefinition("stops-with-closed-documents", false) {
            @Override
            public @NotNull LSPClientFeatures createClientFeatures() {
                return new LSPClientFeatures() {
                    @Override
                    public @Nullable VirtualFile findFileByUri(@NotNull String fileUri) {
                        if (stopping.get() && fileUris.contains(fileUri)) {
                            resolved.countDown();
                        }
                        return super.findFileByUri(fileUri);
                    }

                    @Override
                    public @Nullable URI getFileUri(@NotNull VirtualFile file) {
                        if (stopping.get() && files.contains(file)) {
                            mostRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                            try {
                                ProgressIndicatorUtils.awaitWithCheckCanceled(resolved);
                            } finally {
                                running.decrementAndGet();
                            }
                        }
                        return super.getFileUri(file);
                    }
                };
            }
        };
        LanguageServersRegistry.getInstance().addServerDefinition(project, stoppingServer,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null)));
        try {
            var main = myFixture.configureByText("main.foo", "main");
            var wrapper = LanguageServiceAccessor.getInstance(project).getLanguageServers(main, null, null)
                    .get(1, TimeUnit.HOURS).stream().map(LanguageServerItem::getServerWrapper)
                    .filter(w -> w.getServerDefinition() == stoppingServer).findFirst().orElseThrow();

            // The server's error for each closed file makes it a problem file. Each error is reported before the next
            // one is published, so that their reports are never many at once
            var error = new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "an error", DiagnosticSeverity.Error, "mock");
            for (var file : files) {
                wrapper.updateDiagnostics(file, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
                NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
            }
            assertTrue("each closed file must be a problem file", files.stream().allMatch(problems::isProblemFile));

            // The server stops, and its clear runs. An error logged meanwhile, on any thread, is kept rather than
            // thrown: thrown where the clear submits a report, it would end the clear before it resolves all the files.
            // The bounds only end a hang.
            var errors = new ConcurrentLinkedQueue<String>();
            LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
                @Override
                public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message,
                                                         String @NotNull [] details, @Nullable Throwable t) {
                    errors.add(message);
                    return EnumSet.of(Action.LOG, Action.STDERR);
                }
            }, () -> {
                stopping.set(true);
                wrapper.stop().get(1, TimeUnit.HOURS);
                PlatformTestUtil.waitWithEventsDispatching("no closed file must stay a problem file once the server stops",
                        () -> files.stream().noneMatch(problems::isProblemFile), 3600);
                waitForProblemReports();
            });
            assertEmpty("no error must be logged as the server stops", errors);
            assertEquals("the clear must report its files one after another", 1, mostRunning.get());
        } finally {
            stoppingServer.getServer().waitBeforeTearDown();
            LanguageServersRegistry.getInstance().removeServerDefinition(project, stoppingServer);
        }
    }

    public void testAServerThatDoesNotReportAFilesProblemsKeepsNoMarkForItWhenItCloses() throws Exception {
        var problems = problemFiles();
        var project = myFixture.getProject();
        // A second server for the files, whose client doesn't report their problems in the Project view
        var notReporting = new MockLanguageServerDefinition("problems-not-reported", false) {
            @Override
            public LSPClientFeatures createClientFeatures() {
                return new LSPClientFeatures().setDiagnosticFeature(new LSPDiagnosticFeature() {
                    @Override
                    public boolean canReportProblem(@NotNull VirtualFile file) {
                        return false;
                    }
                });
            }
        };
        LanguageServersRegistry.getInstance().addServerDefinition(project, notReporting,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null)));
        try {
            var main = myFixture.configureByText("main.foo", "main");
            var wrappers = LanguageServiceAccessor.getInstance(project).getLanguageServers(main, null, null)
                    .get(1, TimeUnit.HOURS).stream().map(LanguageServerItem::getServerWrapper).toList();
            var wrapper = wrappers.stream().filter(w -> w.getServerDefinition() != notReporting).findFirst().orElseThrow();
            var notReportingWrapper = wrappers.stream().filter(w -> w.getServerDefinition() == notReporting).findFirst().orElseThrow();
            var other = myFixture.addFileToProject("other.foo", "other").getVirtualFile();
            var error = new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "an error", DiagnosticSeverity.Error, "mock");

            // The second server holds an error for the closed file; the first one's error for the opened file makes
            // it a problem file
            notReportingWrapper.updateDiagnostics(other, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
            wrapper.connect(other, new LanguageServerWrapper.LSPFileConnectionInfo(null, null, null, true))
                    .get(1, TimeUnit.HOURS);
            wrapper.updateDiagnostics(other, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
            waitForProblemReports();
            assertTrue("other.foo must be a problem file", problems.isProblemFile(other));

            // As it closes, only a server that doesn't report its problems holds an error for it
            wrapper.disconnect(other, false);
            waitForProblemReports();
            assertFalse("other.foo must be no problem file once it closes", problems.isProblemFile(other));
        } finally {
            notReporting.getServer().waitBeforeTearDown();
            LanguageServersRegistry.getInstance().removeServerDefinition(project, notReporting);
        }
    }

    public void testAReportMadeWhileTheProjectIsDisposedNeverRuns() {
        var project = myFixture.getProject();
        var other = myFixture.addFileToProject("other.foo", "other").getVirtualFile();
        // A language server is a child of its project, and stopping it as the project is disposed reports the files
        // that it disconnects. That report waits for the write action which disposes the project, and must not run
        // after it: the project's services are gone. The tear down disposes the project, then waits for the pending
        // read actions and fails on one that failed
        Disposer.register(project, () -> LSPDiagnosticUtils.reportProblem(other, null, project));
    }

    public void testAClearMadeWhileTheProjectIsDisposedNeverRuns() {
        var project = myFixture.getProject();
        // The clear's first step, before it submits any report, which is guarded on its own
        var features = new LSPClientFeatures() {
            @Override
            public @Nullable VirtualFile findFileByUri(@NotNull String fileUri) {
                clearRan.set(true);
                return null;
            }
        };
        // As for a report, the clear that stopping a language server submits as the project is disposed waits for the
        // write action which disposes it, and must not run after it. The tear down checks that it didn't
        Disposer.register(project, () -> LSPDiagnosticUtils.clearProblem(Set.of(URI.create("file:///cleared.foo")), features, project));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            // The tear down disposed the project and waited for the pending read actions, a clear's among them
            assertFalse("a clear submitted as the project is disposed must not run after it", clearRan.get());
        }
    }

    /**
     * Waits for the problem reports submitted so far. A clear submits a report of each of its files as it runs, so a
     * second wait takes the reports that the first one's clears submitted.
     */
    private static void waitForProblemReports() {
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }

    /**
     * Returns the project's problem files. A light test's problem solver ignores every report unless it has a real
     * one behind it.
     */
    private WolfTheProblemSolver problemFiles() {
        var project = myFixture.getProject();
        var problems = WolfTheProblemSolver.getInstance(project);
        if (problems instanceof MockWolfTheProblemSolver mock) {
            mock.setDelegate(WolfTheProblemSolverImpl.createTestInstance(project));
            Disposer.register(getTestRootDisposable(), mock::resetDelegate);
        }
        return problems;
    }
}
