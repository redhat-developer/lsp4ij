/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction.quickfix;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/**
 * Tests for {@link LSPLazyCodeActions}.
 */
public class LSPLazyCodeActionsTest extends LSPCodeInsightFixtureTestCase {

    public LSPLazyCodeActionsTest() {
        super("*.ts");
    }

    public void testCreationDoesNotWaitForWriteAction() throws Exception {
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1;");
        LanguageServerItem languageServer = getLanguageServer(file);
        VirtualFile virtualFile = file.getVirtualFile();
        List<Diagnostic> diagnostics = List.of(createDiagnostic());

        // textDocument/publishDiagnostics creates the lazy code actions on the LSP message thread, which also
        // delivers the responses the EDT may be waiting for inside a write action (ex: completionItem/resolve
        // while typing). Waiting for that write action here would deadlock both threads.
        WriteAction.run(() -> {
            Future<LSPLazyCodeActions> creation = ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> new LSPLazyCodeActions(diagnostics, virtualFile, languageServer));
            try {
                creation.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                fail("LSPLazyCodeActions creation waited for the write action held by the EDT");
            }
        });
    }

    public void testCodeActionRequestTargetsFile() throws Exception {
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1;");
        LanguageServerItem languageServer = getLanguageServer(file);
        Diagnostic diagnostic = createDiagnostic();
        CodeAction quickFix = new CodeAction("Remove unused declaration for: 'foo'");
        quickFix.setKind(CodeActionKind.QuickFix);
        quickFix.setDiagnostics(List.of(diagnostic));
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(quickFix)));

        var codeActions = new LSPLazyCodeActions(List.of(diagnostic), file.getVirtualFile(), languageServer);
        var codeAction = codeActions.getCodeActionAt(0);

        assertNotNull(codeAction);
        assertTrue(codeAction.isLeft());
        assertEquals("Remove unused declaration for: 'foo'", codeAction.getLeft().codeAction().getRight().getTitle());
        CodeActionParams params = MockLanguageServer.INSTANCE.getTextDocumentService().lastCodeActionParams;
        assertNotNull(params);
        assertEquals(FileUriSupport.toString(file.getVirtualFile(), languageServer.getClientFeatures()),
                params.getTextDocument().getUri());
        assertEquals(diagnostic.getRange(), params.getRange());
    }

    public void testParallelFirstUsesSendOneRequest() throws Exception {
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1;");
        LanguageServerItem languageServer = getLanguageServer(file);
        Diagnostic diagnostic = createDiagnostic();
        CodeAction quickFix = new CodeAction("Remove unused declaration for: 'foo'");
        quickFix.setKind(CodeActionKind.QuickFix);
        quickFix.setDiagnostics(List.of(diagnostic));
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(quickFix)));
        var textDocumentService = MockLanguageServer.INSTANCE.getTextDocumentService();
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);

        // Highlighting passes run in parallel and can ask the same quick fixes for their code actions at once.
        // A second load used to cancel the first request before it was sent, and could leave it stored.
        var codeActions = new LSPLazyCodeActions(List.of(diagnostic), file.getVirtualFile(), languageServer);
        var start = new CountDownLatch(1);
        var loads = IntStream.range(0, 8)
                .mapToObj(i -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    start.await();
                    return codeActions.getCodeActionAt(0);
                }))
                .toList();
        start.countDown();
        for (var load : loads) {
            var codeAction = load.get(10, TimeUnit.SECONDS);
            assertNotNull(codeAction);
            assertTrue(codeAction.isLeft());
        }
        assertEquals(1, textDocumentService.codeActionRequests);
    }

    public void testCodeActionsLoadAgainAfterAPendingRequestWasCancelled() throws Exception {
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1;");
        LanguageServerItem languageServer = getLanguageServer(file);
        Diagnostic diagnostic = createDiagnostic();
        CodeAction quickFix = new CodeAction("Remove unused declaration for: 'foo'");
        quickFix.setKind(CodeActionKind.QuickFix);
        quickFix.setDiagnostics(List.of(diagnostic));
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(quickFix)));
        var textDocumentService = MockLanguageServer.INSTANCE.getTextDocumentService();
        // Keep the first textDocument/codeAction request pending until it is cancelled.
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(10_000);

        var codeActions = new LSPLazyCodeActions(List.of(diagnostic), file.getVirtualFile(), languageServer);
        Future<?> pending = ApplicationManager.getApplication().executeOnPooledThread(() -> codeActions.getCodeActionAt(0));
        PlatformTestUtil.waitWithEventsDispatching("textDocument/codeAction must be sent",
                () -> textDocumentService.codeActionRequests > 0, 5);
        // LSPDiagnosticsForServer cancels the lazy code actions whenever diagnostics are published again, also
        // when they are unchanged, and the editor then keeps the quick fixes that use these code actions.
        codeActions.cancel();
        try {
            pending.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // The cancelled request may fail the pending load; only the next load matters here.
        }
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(0);

        var codeAction = codeActions.getCodeActionAt(0);

        assertNotNull(codeAction);
        assertTrue(codeAction.isLeft());
        assertEquals("Remove unused declaration for: 'foo'", codeAction.getLeft().codeAction().getRight().getTitle());
    }

    public void testCodeActionsLoadAgainAfterARequestWasCancelledBeforeItWasSent() throws Exception {
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1;");
        LanguageServerItem languageServer = getLanguageServer(file);
        Diagnostic diagnostic = createDiagnostic();
        CodeAction quickFix = new CodeAction("Remove unused declaration for: 'foo'");
        quickFix.setKind(CodeActionKind.QuickFix);
        quickFix.setDiagnostics(List.of(diagnostic));
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(quickFix)));
        var textDocumentService = MockLanguageServer.INSTANCE.getTextDocumentService();
        // The request waits for the server to initialize: delay initialize, then stop the server, so that
        // the request starts it again and waits. Anything that asks for the server can start it again
        // before the request does, and then stop() never reports stopped; either way the server is not
        // initialized, and it initializes slowly.
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(2_000);
        languageServer.getServerWrapper().stop();
        // longer than the 5 seconds stop() waits for the shutdown reply
        PlatformTestUtil.waitWithEventsDispatching("server must stop or start again", () -> {
            ServerStatus status = languageServer.getServerWrapper().getServerStatus();
            return status == ServerStatus.stopped || status == ServerStatus.starting;
        }, 10);

        var codeActions = new LSPLazyCodeActions(List.of(diagnostic), file.getVirtualFile(), languageServer);
        Future<?> pending = ApplicationManager.getApplication().executeOnPooledThread(() -> codeActions.getCodeActionAt(0));
        PlatformTestUtil.waitWithEventsDispatching("server must start",
                () -> languageServer.getServerWrapper().getServerStatus() == ServerStatus.starting, 5);
        codeActions.cancel();
        try {
            pending.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // The request cancelled before it was sent fails the pending load; only the next load matters here.
        }
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(0);

        var codeAction = codeActions.getCodeActionAt(0);

        assertNotNull(codeAction);
        assertTrue(codeAction.isLeft());
        // The request cancelled while the server initialized must not be sent; only the new load's request is.
        assertEquals(1, textDocumentService.codeActionRequests);
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
