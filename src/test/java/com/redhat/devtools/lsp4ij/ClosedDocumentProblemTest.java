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
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that the Project view's problem files follow the errors of a file as it opens and closes: LSP4IJ drops the
 * file's closed document when it opens, and clears its problem when it closes.
 */
public class ClosedDocumentProblemTest extends LSPCodeInsightFixtureTestCase {

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
