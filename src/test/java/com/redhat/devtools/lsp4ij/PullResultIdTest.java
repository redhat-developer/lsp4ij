/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.RelatedUnchangedDocumentDiagnosticReport;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A pull for a document sends the result ID of the last report applied to it as its previousResultId,
 * so that the server can answer that the result is still accurate.
 */
public class PullResultIdTest extends LSPCodeInsightFixtureTestCase {

    public PullResultIdTest() {
        super("*.foo");
    }

    public void testEachPullSendsTheResultIdOfTheLastReportApplied() throws Exception {
        MockLanguageServer.INSTANCE.setPullDiagnostics(full("result-1", "first"));
        var file = myFixture.configureByText("main.foo", "main");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS).get(0).getServerWrapper();
        var opened = wrapper.getOpenedDocument(wrapper.toUri(file.getVirtualFile()));
        PlatformTestUtil.waitWithEventsDispatching("the first report must be applied",
                () -> messages(opened).equals(List.of("first")), 30);
        assertNull(lastPull().getPreviousResultId());

        // the server finds its result still accurate
        MockLanguageServer.INSTANCE.setPullDiagnostics(new DocumentDiagnosticReport(new RelatedUnchangedDocumentDiagnosticReport("result-2")));
        pullAfter(" edited");
        assertEquals("result-1", lastPull().getPreviousResultId());
        PlatformTestUtil.waitWithEventsDispatching("the unchanged report must be applied",
                () -> "result-2".equals(opened.getSynchronizer().getPullResultId()), 30);
        // an unchanged report keeps the diagnostics of the report it refers to
        assertEquals(List.of("first"), messages(opened));

        pullAfter(" again");
        assertEquals("result-2", lastPull().getPreviousResultId());
    }

    private static List<String> messages(OpenedDocument opened) {
        return opened.getDiagnostics().stream().map(d -> d.getMessage().getLeft()).toList();
    }

    /** Types the text, and waits for the pull that the change causes. */
    private void pullAfter(String text) {
        int pulls = MockLanguageServer.INSTANCE.getTextDocumentService().getDiagnosticRequests().size();
        type(text);
        PlatformTestUtil.waitWithEventsDispatching("the document must be pulled again",
                () -> MockLanguageServer.INSTANCE.getTextDocumentService().getDiagnosticRequests().size() > pulls, 30);
    }

    private static DocumentDiagnosticReport full(String resultId, String message) {
        var report = new RelatedFullDocumentDiagnosticReport(List.of(
                new Diagnostic(new Range(new Position(0, 0), new Position(0, 4)), message)));
        report.setResultId(resultId);
        return new DocumentDiagnosticReport(report);
    }

    private static DocumentDiagnosticParams lastPull() {
        var pulls = MockLanguageServer.INSTANCE.getTextDocumentService().getDiagnosticRequests();
        assertFalse("the document must have been pulled", pulls.isEmpty());
        return pulls.get(pulls.size() - 1);
    }

    private void type(String text) {
        var document = myFixture.getEditor().getDocument();
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                () -> document.insertString(document.getTextLength(), text));
    }
}
