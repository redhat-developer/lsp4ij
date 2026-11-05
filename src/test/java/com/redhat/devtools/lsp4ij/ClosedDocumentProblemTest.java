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
import com.intellij.openapi.util.Disposer;
import com.intellij.problems.WolfTheProblemSolver;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that the Project view's problem files follow the errors of a file whose closed document LSP4IJ drops when
 * the file opens.
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
