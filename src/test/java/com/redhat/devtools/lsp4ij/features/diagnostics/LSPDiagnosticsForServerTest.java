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
import java.util.concurrent.CompletableFuture;
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

        // Keep the textDocument/codeAction request of the editor's quick fix pending: the server holds its reply until
        // the test releases it, and a delay could end before the diagnostics are published again.
        CompletableFuture<Void> codeActionReplies = MockLanguageServer.INSTANCE.holdCodeActions();
        Future<?> pending;
        try {
            pending = ApplicationManager.getApplication().executeOnPooledThread(editorQuickFix::getText);
            PlatformTestUtil.waitWithEventsDispatching("textDocument/codeAction must be sent",
                    () -> textDocumentService.codeActionRequests.get() > 0, 30);
            assertFalse("The request must still wait for the held reply when the diagnostics are published again",
                    pending.isDone());

            // The server publishes the same diagnostics again, as servers commonly do after each change
            assertFalse(diagnostics.update("test", List.of(createDiagnostic())));
        } finally {
            // Also when the test fails: the mock language server is shared by the next tests
            codeActionReplies.complete(null);
        }
        try {
            pending.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // A cancelled request may fail the pending load; the assertions below tell
        }

        assertEquals(QUICK_FIX_TITLE, editorQuickFix.getText());
        assertEquals("The pending request must not be cancelled and sent again", 1,
                textDocumentService.codeActionRequests.get());
        assertSame("The editor keeps its quick fix, so the update must keep it current",
                editorQuickFix, diagnostics.getQuickFixesFor(createDiagnostic(), file).get(0));
    }

    public void testAListThatRepeatsADiagnosticDropsTheOneItReplaces() throws Exception {
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1; const bar = 2;");
        LanguageServerItem languageServer = getLanguageServer(file);
        var diagnostics = new LSPDiagnosticsForServer(languageServer, file.getVirtualFile(), null);
        Diagnostic foo = diagnostic(6, 9, "'foo' is declared but its value is never read.");
        Diagnostic bar = diagnostic(21, 24, "'bar' is declared but its value is never read.");
        assertTrue(diagnostics.update("test", List.of(foo, bar)));
        assertFalse(diagnostics.getQuickFixesFor(bar, file).isEmpty());

        // As many diagnostics as before, each of them known, but bar is gone
        assertTrue("A removed diagnostic is a change", diagnostics.update("test", List.of(foo, foo)));
        assertTrue("The removed diagnostic has no quick fixes", diagnostics.getQuickFixesFor(bar, file).isEmpty());
    }

    private LanguageServerItem getLanguageServer(PsiFile file) throws Exception {
        List<LanguageServerItem> languageServers = LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS);
        assertSize(1, languageServers);
        return languageServers.get(0);
    }

    private static Diagnostic createDiagnostic() {
        return diagnostic(6, 9, "'foo' is declared but its value is never read.");
    }

    private static Diagnostic diagnostic(int start, int end, String message) {
        Diagnostic diagnostic = new Diagnostic(new Range(new Position(0, start), new Position(0, end)), message);
        diagnostic.setSeverity(DiagnosticSeverity.Hint);
        return diagnostic;
    }
}
