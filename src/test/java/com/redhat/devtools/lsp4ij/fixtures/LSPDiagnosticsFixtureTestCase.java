/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.fixtures;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class test case to test LSP 'textDocument/publishDiagnostic' feature.
 */
public abstract class LSPDiagnosticsFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    public LSPDiagnosticsFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    protected void assertDiagnostics(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull String jsonDiagnostics,
                                     @NotNull String expectedEditorContentText) {
        assertDiagnostics(fileName, editorContentText, jsonDiagnostics, null, expectedEditorContentText);
    }

    protected void assertDiagnostics(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull String publishJsonDiagnostics,
                                     @Nullable String pullJsonDiagnostics,
                                     @NotNull String expectedEditorContentText) {
        // Publish diagnostics
        PublishDiagnosticsParams params = getPublishDiagnosticsParams(publishJsonDiagnostics);
        // Pull diagnostics
        DocumentDiagnosticReport diagnosticReport = null;
        if (pullJsonDiagnostics != null) {
            var report = JSONUtils.getLsp4jGson().fromJson(pullJsonDiagnostics, RelatedFullDocumentDiagnosticReport.class);
            diagnosticReport = new DocumentDiagnosticReport(report);
        }

        assertDiagnostics(fileName, editorContentText, params.getDiagnostics(), diagnosticReport, expectedEditorContentText);
    }

    protected void assertDiagnostics(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull List<Diagnostic> publishDiagnostics,
                                     @Nullable DocumentDiagnosticReport pullDiagnostics,
                                     @NotNull String expectedEditorContentText) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);
        MockLanguageServer.INSTANCE.setPublishDiagnostics(publishDiagnostics);
        MockLanguageServer.INSTANCE.setPullDiagnostics(pullDiagnostics);
        // Open editor for a given file name and content (which declares <caret> to know where the completion is triggered).
        myFixture.configureByText(fileName, editorContentText);

        // Wait for pull diagnostics...
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertDiagnostics(expectedEditorContentText);
    }

    protected void publishAndAssertDiagnostics(@NotNull String publishJsonDiagnostics,
                                               @NotNull String expectedEditorContentText) {
        PublishDiagnosticsParams params = getPublishDiagnosticsParams(publishJsonDiagnostics);
        params.setUri(LSPIJUtils.toUriAsString(myFixture.getFile()));
        publishAndAssertDiagnostics(params, expectedEditorContentText);
    }

    protected void publishAndAssertDiagnostics(@NotNull PublishDiagnosticsParams publishDiagnostics,
                                               @NotNull String expectedEditorContentText) {
        MockLanguageServer.INSTANCE.getTextDocumentService().publishDiagnostics(publishDiagnostics);
        assertDiagnostics(expectedEditorContentText);
    }

    private void assertDiagnostics(@NotNull String expectedEditorContentText) {
        // Wait for publish diagnostics during 3000ms (since Alarm to restart Daemon is configured with 2000ms)
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // DaemonCodeAnalyzer.restart(file) can be called asynchronously by LSP4IJ to refresh IJ annotations from the LSP diagnostics.
        // To avoid having error like

        // Caused by: java.lang.AssertionError: PSI/document/model changes are not allowed during highlighting, because it leads to the daemon unnecessary restarts. If you really do need to start write action during the highlighting, you can pass `canChangeDocument=true` to the CodeInsightTestFixtureImpl#instantiateAndRun() and accept the daemon unresponsiveness/blinking/slowdowns.
        //        at com.intellij.codeInsight.daemon.impl.FileStatusMap.assertAllowModifications(FileStatusMap.java:187)
        //at com.intellij.codeInsight.daemon.impl.FileStatusMap.markFileScopeDirty(FileStatusMap.java:276)
        //at com.intellij.codeInsight.daemon.impl.FileStatusMap.markWholeFileScopeDirty(FileStatusMap.java:273)

        // or some LSP diagnostics which are updated in cachebut not used by LSPDiagnosticAnnotator because
        // myFixture.doHighlighting() could be called while calling DaemonCodeAnalyzer.restart(file)

        // we need to wait for DaemonCodeAnalyzer.restart(file) is finished before calling highlighting,
        waitForDaemonCodeAnalyzerFinished();

        // process highlighting
        myFixture.doHighlighting();
        Document expectedDocument = new DocumentImpl(expectedEditorContentText);
        ExpectedHighlightingData data = new ExpectedHighlightingData(expectedDocument, true, true, true);
        data.init();
        ((CodeInsightTestFixtureImpl) myFixture).collectAndCheckHighlighting(data);
    }

    @NotNull
    private static PublishDiagnosticsParams getPublishDiagnosticsParams(@NotNull String publishJsonDiagnostics) {
        if (publishJsonDiagnostics.trim().startsWith("[")) {
            publishJsonDiagnostics = "{\"diagnostics\":" + publishJsonDiagnostics + "}";
        }
        return JSONUtils.getLsp4jGson().fromJson(publishJsonDiagnostics, PublishDiagnosticsParams.class);
    }

}
