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
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.codeAction.intention.LSPIntentionAction0;
import com.redhat.devtools.lsp4ij.features.codeAction.quickfix.LSPLazyCodeActions;
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
import java.util.concurrent.TimeUnit;

/**
 * The EDT never waits for a textDocument/codeAction answer: IntelliJ asks the lazy intentions and quick fixes for their
 * text and availability there, when the lightbulb and the intention popup sort them. While the answer is pending they
 * are not available, and they are once it is in. The tests run on the EDT, and the mock server holds its answer until
 * the test releases it.
 */
public class CodeActionsOnTheEdtTest extends LSPCodeInsightFixtureTestCase {

    // Only ends a hang: an EDT that waits for the held answer gets it then, and the test fails
    private static final long HANG_SECONDS = 60;

    public CodeActionsOnTheEdtTest() {
        super("*.ts");
    }

    public void testAnIntentionOnTheEdtDoesNotWaitForItsCodeAction() throws Exception {
        CodeAction refactoring = new CodeAction("Inline constant");
        refactoring.setKind(CodeActionKind.RefactorInline);
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(refactoring)));
        PsiFile file = myFixture.configureByText("test.ts", "const foo = <caret>1;");
        IntentionAction intention = new LSPIntentionAction0();

        assertNotWaitedFor(intention, file);

        // once the answer is in, the EDT has it at once, with no other request
        PlatformTestUtil.waitWithEventsDispatching("the intention must be available once the answer is in",
                () -> intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file), 5);
        assertEquals("Inline constant", intention.getText());
        assertNoOtherRequest(intention, file);
    }

    public void testAQuickFixOnTheEdtDoesNotWaitForItsCodeAction() throws Exception {
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1;");
        Diagnostic diagnostic = new Diagnostic(new Range(new Position(0, 6), new Position(0, 9)),
                "'foo' is declared but its value is never read.");
        diagnostic.setSeverity(DiagnosticSeverity.Hint);
        CodeAction quickFix = new CodeAction("Remove unused declaration for: 'foo'");
        quickFix.setKind(CodeActionKind.QuickFix);
        quickFix.setDiagnostics(List.of(diagnostic));
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(quickFix)));
        IntentionAction intention = new LSPLazyCodeActions(List.of(diagnostic), file.getVirtualFile(), getLanguageServer(file))
                .getCodeActions()
                .get(0);

        assertNotWaitedFor(intention, file);

        PlatformTestUtil.waitWithEventsDispatching("the quick fix must be available once the answer is in",
                () -> intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file), 5);
        assertEquals("Remove unused declaration for: 'foo'", intention.getText());
        assertNoOtherRequest(intention, file);
    }

    /**
     * Asks the intention on the EDT for its availability and text while the server holds its answer, and checks that
     * they come at once: not available, and the placeholder text. Then it releases the answer.
     */
    private void assertNotWaitedFor(IntentionAction intention, PsiFile file) {
        var textDocumentService = MockLanguageServer.INSTANCE.getTextDocumentService();
        CompletableFuture<Void> release = MockLanguageServer.INSTANCE.holdCodeActions();
        var hang = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(() -> release.complete(null), HANG_SECONDS, TimeUnit.SECONDS);
        try {
            assertTrue("The test must run on the EDT", ApplicationManager.getApplication().isDispatchThread());
            assertFalse(intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file));
            assertEquals("", intention.getText());
            assertFalse("The EDT waited for the language server's answer", release.isDone());
            PlatformTestUtil.waitWithEventsDispatching("textDocument/codeAction must be sent",
                    () -> textDocumentService.codeActionRequests > 0, 5);
            assertFalse("The answer is still held", intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file));
        } finally {
            hang.cancel(false);
            release.complete(null);
        }
    }

    /**
     * Checks that the highlighting restart that the answer brings, and the intention pass's own ask, off the EDT,
     * send no other request.
     */
    private void assertNoOtherRequest(IntentionAction intention, PsiFile file) throws Exception {
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        boolean available = ApplicationManager.getApplication()
                .executeOnPooledThread(() -> ReadAction.compute(() ->
                        intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file)))
                .get(HANG_SECONDS, TimeUnit.SECONDS);
        assertTrue(available);
        assertEquals(1, MockLanguageServer.INSTANCE.getTextDocumentService().codeActionRequests);
    }

    private LanguageServerItem getLanguageServer(PsiFile file) throws Exception {
        List<LanguageServerItem> languageServers = LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file, null, null)
                .get(5000, TimeUnit.MILLISECONDS);
        assertSize(1, languageServers);
        return languageServers.get(0);
    }
}
