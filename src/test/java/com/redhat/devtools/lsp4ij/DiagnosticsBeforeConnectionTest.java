/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsApplier;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that a file's marks follow the diagnostics that LSP4IJ holds for it when its connection replaces an opened
 * document that held diagnostics reported before the connection.
 */
public class DiagnosticsBeforeConnectionTest extends LSPCodeInsightFixtureTestCase {

    public DiagnosticsBeforeConnectionTest() {
        super("*.foo");
    }

    public void testAnErrorMarkedBeforeAFileConnectsIsNoLongerMarkedOnceItConnectsWithoutErrors() throws Exception {
        registerDiagnosticsPass();
        // The server publishes no diagnostics for the files it opens
        MockLanguageServer.INSTANCE.setPublishDiagnostics(List.of());
        // A first file starts the language server; the second is not connected. The bounds only end a hang.
        var main = myFixture.configureByText("main.foo", "main");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(main, null, null).get(1, TimeUnit.HOURS).get(0).getServerWrapper();
        var other = myFixture.addFileToProject("other.foo", "other").getVirtualFile();
        var document = LSPIJUtils.getDocument(other);
        assertNotNull(document);
        var applier = LSPDiagnosticsApplier.getInstance(myFixture.getProject());

        // A report reaches a file that is open in an editor but not connected yet, as a workspace report or another
        // document's related report can: an opened document without synchronization holds it, and the file is marked
        wrapper.getOpenedDocument(wrapper.toUri(other), true);
        var error = new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "an error", DiagnosticSeverity.Error, "mock");
        wrapper.updateDiagnostics(other, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(error), null);
        awaitRefreshed(applier, other);
        assertEquals(List.of("an error"), marks(document));

        // The connection replaces that opened document with one that holds no diagnostics until the server reports
        // the file: until then the file keeps its mark, which may still be right
        wrapper.connect(other, new LanguageServerWrapper.LSPFileConnectionInfo(null, null, null, true))
                .get(1, TimeUnit.HOURS);
        awaitRefreshed(applier, other);
        assertEquals("the file must keep its mark until its connected document is reported",
                List.of("an error"), marks(document));

        // The error was fixed: the connected document's first report has no diagnostics, which changes none of its
        // own, and the file must no longer be marked
        wrapper.updateDiagnostics(other, LSPDocumentBase.PUBLISH_DIAGNOSTIC_IDENTIFIER, List.of(), null);
        awaitRefreshed(applier, other);
        assertEmpty(marks(document));
    }

    /**
     * Lets the project's registrar register the diagnostics pass, as a first highlighting does: until then, a refresh
     * waits for the pass.
     */
    private void registerDiagnosticsPass() {
        if (LSPDiagnosticsApplier.GROUP_ID == -1) {
            myFixture.configureByText("first.txt", "");
            myFixture.doHighlighting();
        }
        assertTrue(LSPDiagnosticsApplier.GROUP_ID != -1);
    }

    /**
     * Waits until no refresh of the file's marks is pending: a refresh collects in the background and applies on the
     * UI thread.
     */
    private static void awaitRefreshed(LSPDiagnosticsApplier applier, VirtualFile file) {
        PlatformTestUtil.waitWithEventsDispatching("the refresh of the file's marks must end",
                () -> !applier.isRefreshPending(file), 3600);
    }

    private List<String> marks(Document document) {
        return DaemonCodeAnalyzerImpl.getHighlights(document, null, myFixture.getProject())
                .stream()
                .map(HighlightInfo::getDescription)
                .toList();
    }
}
