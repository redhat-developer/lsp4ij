/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link LSPDiagnosticsForServer}.
 */
public class LSPDiagnosticsForServerTest extends LSPCodeInsightFixtureTestCase {

    private static final String QUICK_FIX_TITLE = "Remove unused declaration for: 'foo'";

    public LSPDiagnosticsForServerTest() {
        super("*.ts");
    }

    public void testAnUnchangedUpdateKeepsTheQuickFixesAndTheirPendingRequest() throws Exception {
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1;");
        LanguageServerItem languageServer = getLanguageServer(file);
        CodeAction quickFix = new CodeAction(QUICK_FIX_TITLE);
        quickFix.setKind(CodeActionKind.QuickFix);
        quickFix.setDiagnostics(List.of(createDiagnostic()));
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(quickFix)));
        var textDocumentService = MockLanguageServer.INSTANCE.getTextDocumentService();
        var diagnostics = new LSPDiagnosticsForServer(languageServer, file.getVirtualFile(), null);
        assertTrue(diagnostics.update("test", List.of(createDiagnostic())));
        IntentionAction editorQuickFix = diagnostics.getQuickFixesFor(createDiagnostic(), file).get(0);

        // Keep the textDocument/codeAction request of the editor's quick fix pending
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(2_000);
        Future<?> pending = ApplicationManager.getApplication().executeOnPooledThread(editorQuickFix::getText);
        PlatformTestUtil.waitWithEventsDispatching("textDocument/codeAction must be sent",
                () -> textDocumentService.codeActionRequests > 0, 5);

        // The server publishes the same diagnostics again, as servers commonly do after each change
        assertFalse(diagnostics.update("test", List.of(createDiagnostic())));
        try {
            pending.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // A cancelled request may fail the pending load; the assertions below tell
        }
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(0);

        assertEquals(QUICK_FIX_TITLE, editorQuickFix.getText());
        assertEquals("The pending request must not be cancelled and sent again", 1,
                textDocumentService.codeActionRequests);
        assertSame("The editor keeps its quick fix, so the update must keep it current",
                editorQuickFix, diagnostics.getQuickFixesFor(createDiagnostic(), file).get(0));
    }

    private LanguageServerItem getLanguageServer(PsiFile file) throws Exception {
        List<LanguageServerItem> languageServers = LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file, null, null)
                .get(5000, TimeUnit.MILLISECONDS);
        assertSize(1, languageServers);
        return languageServers.get(0);
    }

    private static Diagnostic createDiagnostic() {
        Diagnostic diagnostic = new Diagnostic(new Range(new Position(0, 6), new Position(0, 9)),
                "'foo' is declared but its value is never read.");
        diagnostic.setSeverity(DiagnosticSeverity.Hint);
        return diagnostic;
    }
}
