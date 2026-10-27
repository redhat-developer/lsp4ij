/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.FullDocumentDiagnosticReport;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.RelatedUnchangedDocumentDiagnosticReport;
import org.eclipse.lsp4j.UnchangedDocumentDiagnosticReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that a textDocument/diagnostic report applies the diagnostics of its related documents.
 */
public class PullRelatedDocumentsTest extends LSPCodeInsightFixtureTestCase {

    public PullRelatedDocumentsTest() {
        super("*.foo");
    }

    public void testFullReportAppliesTheDiagnosticsOfRelatedDocuments() throws Exception {
        var related = myFixture.addFileToProject("related.foo", "related");
        var report = new RelatedFullDocumentDiagnosticReport(List.of());
        report.setRelatedDocuments(relatedReport(related));
        assertRelatedDiagnosticsApplied(related, new DocumentDiagnosticReport(report));
    }

    public void testUnchangedReportAppliesTheDiagnosticsOfRelatedDocuments() throws Exception {
        var related = myFixture.addFileToProject("related.foo", "related");
        var report = new RelatedUnchangedDocumentDiagnosticReport("result-1");
        report.setRelatedDocuments(relatedReport(related));
        assertRelatedDiagnosticsApplied(related, new DocumentDiagnosticReport(report));
    }

    private Map<String, Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>> relatedReport(PsiFile related) {
        var diagnostic = new Diagnostic(new Range(new Position(0, 0), new Position(0, 7)), "reported for the related document");
        return Map.of(LSPIJUtils.toUriAsString(related), Either.forLeft(new FullDocumentDiagnosticReport(List.of(diagnostic))));
    }

    private void assertRelatedDiagnosticsApplied(PsiFile related, DocumentDiagnosticReport report) throws Exception {
        // The mock answers the pull for the opened document; the related document stays closed.
        MockLanguageServer.INSTANCE.setPullDiagnostics(report);
        var opened = myFixture.configureByText("main.foo", "main");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(opened, null, null).get(30, TimeUnit.SECONDS).get(0).getServerWrapper();

        PlatformTestUtil.waitWithEventsDispatching("the related document's diagnostics must be applied",
                () -> diagnostics(wrapper, related).stream()
                        .anyMatch(d -> "reported for the related document".equals(d.getMessage().getLeft())), 30);
    }

    private static Collection<Diagnostic> diagnostics(LanguageServerWrapper wrapper, PsiFile file) {
        var uri = wrapper.toUri(file.getVirtualFile());
        var opened = wrapper.getOpenedDocument(uri);
        if (opened != null) {
            return opened.getDiagnostics();
        }
        var closed = wrapper.getClosedDocument(uri, false);
        return closed != null ? closed.getDiagnostics() : List.of();
    }
}
