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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.EditorTracker;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditor;
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
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The EDT never waits for a textDocument/codeAction answer: IntelliJ asks the lazy intentions and quick fixes for their
 * text and availability there, when the lightbulb and the intention popup sort them. While the answer is pending they
 * are not available, and they are once it is in; the file's highlighting restarts once for the answers the EDT found
 * pending, unless the file changed meanwhile. The tests run on the EDT, and the mock server holds its answers until
 * the test releases them.
 */
public class CodeActionsOnTheEdtTest extends LSPCodeInsightFixtureTestCase {

    // Only ends a hang: an EDT that waits for a held answer gets it then, and the test fails
    private static final long HANG_SECONDS = 60;

    public CodeActionsOnTheEdtTest() {
        super("*.ts");
    }

    public void testAnIntentionOnTheEdtDoesNotWaitForItsCodeAction() throws Exception {
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(refactoring())));
        PsiFile file = myFixture.configureByText("test.ts", "const foo = <caret>1;");
        IntentionAction intention = new LSPIntentionAction0();
        var passes = passesOf(file);
        int restarts = RestartWhenCodeActionsLoaded.getRestartCount();
        var daemon = (DaemonCodeAnalyzerImpl) DaemonCodeAnalyzer.getInstance(myFixture.getProject());
        var editors = EditorTracker.getInstance(myFixture.getProject());
        // the daemon highlights the active editors once its timer runs, as when the editor is shown
        editors.setActiveEditors(List.of(myFixture.getEditor()));
        daemon.setUpdateByTimerEnabled(true);
        try {
            CompletableFuture<Void> release = hold();
            try {
                assertNotWaitedFor(intention, file, release);
            } finally {
                release.complete(null);
            }

            assertRestarts(file, restarts + 1);
            // the restarted highlighting runs a pass, which shows the intention
            passes.set(0);
            PlatformTestUtil.waitWithEventsDispatching("a highlighting pass must run after the restart",
                    () -> passes.get() > 0, 10);
        } finally {
            daemon.setUpdateByTimerEnabled(false);
            editors.setActiveEditors(List.of());
            waitForDaemonCodeAnalyzerFinished();
        }
        // once the answer is in, the EDT has it at once
        assertTrue(intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file));
        assertEquals("Inline constant", intention.getText());
        assertAvailableWithNoOtherRequest(intention, file, 1);
    }

    public void testAQuickFixOnTheEdtDoesNotWaitForItsCodeAction() throws Exception {
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(quickFix())));
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1;");
        IntentionAction intention = quickFixOf(file);
        int restarts = RestartWhenCodeActionsLoaded.getRestartCount();
        CompletableFuture<Void> release = hold();
        try {
            assertNotWaitedFor(intention, file, release);
        } finally {
            release.complete(null);
        }

        assertRestarts(file, restarts + 1);
        assertTrue(intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file));
        assertEquals("Remove unused declaration for: 'foo'", intention.getText());
        assertAvailableWithNoOtherRequest(intention, file, 1);
    }

    public void testAnEditWhileTheAnswerIsPendingRestartsNothing() throws Exception {
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(refactoring())));
        PsiFile file = myFixture.configureByText("test.ts", "const foo = <caret>1;");
        IntentionAction intention = new LSPIntentionAction0();
        int restarts = RestartWhenCodeActionsLoaded.getRestartCount();
        CompletableFuture<Void> release = hold();
        try {
            assertNotWaitedFor(intention, file, release);
            // The edit restarts the file's highlighting itself, whose pass asks for code actions of its own
            WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                    () -> myFixture.getEditor().getDocument().insertString(0, " "));
        } finally {
            release.complete(null);
        }

        assertRestarts(file, restarts);
    }

    public void testTheAnswersOfAFileRestartItOnce() throws Exception {
        MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(refactoring()), Either.forRight(quickFix())));
        PsiFile file = myFixture.configureByText("test.ts", "const foo = <caret>1;");
        IntentionAction intention = new LSPIntentionAction0();
        IntentionAction quickFix = quickFixOf(file);
        var textDocumentService = MockLanguageServer.INSTANCE.getTextDocumentService();
        int restarts = RestartWhenCodeActionsLoaded.getRestartCount();
        CompletableFuture<Void> release = hold();
        try {
            assertFalse(intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file));
            assertFalse(quickFix.isAvailable(myFixture.getProject(), myFixture.getEditor(), file));
            PlatformTestUtil.waitWithEventsDispatching("both textDocument/codeAction requests must be sent",
                    () -> textDocumentService.codeActionRequests == 2, 5);
        } finally {
            release.complete(null);
        }

        // Both answers come at once, and the file restarts once
        assertRestarts(file, restarts + 1);
        assertAvailableWithNoOtherRequest(intention, file, 2);
        assertAvailableWithNoOtherRequest(quickFix, file, 2);
    }

    /**
     * Holds the server's code action answers until the returned future is completed; a test whose EDT waits for them
     * gets them after the hang bound, and fails.
     */
    private static CompletableFuture<Void> hold() {
        CompletableFuture<Void> release = MockLanguageServer.INSTANCE.holdCodeActions();
        var hang = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(() -> release.complete(null), HANG_SECONDS, TimeUnit.SECONDS);
        release.whenComplete((result, failure) -> hang.cancel(false));
        return release;
    }

    /**
     * Asks the intention on the EDT for its availability and text while the server holds its answer, and checks that
     * they come at once: not available, and the placeholder text.
     */
    private void assertNotWaitedFor(IntentionAction intention, PsiFile file, CompletableFuture<Void> release) {
        var textDocumentService = MockLanguageServer.INSTANCE.getTextDocumentService();
        assertTrue("The test must run on the EDT", ApplicationManager.getApplication().isDispatchThread());
        assertFalse(intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file));
        assertEquals("", intention.getText());
        assertFalse("The EDT waited for the language server's answer", release.isDone());
        PlatformTestUtil.waitWithEventsDispatching("textDocument/codeAction must be sent",
                () -> textDocumentService.codeActionRequests > 0, 5);
        assertFalse("The answer is still held", intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file));
    }

    /**
     * Waits until the answers that the EDT found pending for the file have completed, and the restart they make, if
     * any, has run, and checks the number of restarts so far.
     */
    private void assertRestarts(PsiFile file, int expected) {
        PlatformTestUtil.waitWithEventsDispatching("the answers must complete",
                () -> !RestartWhenCodeActionsLoaded.isPending(myFixture.getProject(), file.getVirtualFile()), 5);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assertEquals(expected, RestartWhenCodeActionsLoaded.getRestartCount());
    }

    /**
     * Checks that an ask off the EDT, as the intention pass makes, finds the intention available, and that the server
     * has had the given number of code action requests.
     */
    private void assertAvailableWithNoOtherRequest(IntentionAction intention, PsiFile file, int requests) throws Exception {
        boolean available = ApplicationManager.getApplication()
                .executeOnPooledThread(() -> ReadAction.compute(() ->
                        intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), file)))
                .get(HANG_SECONDS, TimeUnit.SECONDS);
        assertTrue(available);
        assertEquals(requests, MockLanguageServer.INSTANCE.getTextDocumentService().codeActionRequests);
    }

    /**
     * Counts the highlighting passes that finish for the given file.
     */
    private AtomicInteger passesOf(@NotNull PsiFile file) {
        var passes = new AtomicInteger();
        myFixture.getProject().getMessageBus().connect(getTestRootDisposable())
                .subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
                    @Override
                    public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
                        if (fileEditors.stream().anyMatch(editor -> file.getVirtualFile().equals(editor.getFile()))) {
                            passes.incrementAndGet();
                        }
                    }
                });
        return passes;
    }

    private IntentionAction quickFixOf(PsiFile file) throws Exception {
        var diagnostic = diagnostic();
        return new LSPLazyCodeActions(List.of(diagnostic), file.getVirtualFile(), getLanguageServer(file))
                .getCodeActions()
                .get(0);
    }

    private static CodeAction refactoring() {
        CodeAction refactoring = new CodeAction("Inline constant");
        refactoring.setKind(CodeActionKind.RefactorInline);
        return refactoring;
    }

    private static CodeAction quickFix() {
        CodeAction quickFix = new CodeAction("Remove unused declaration for: 'foo'");
        quickFix.setKind(CodeActionKind.QuickFix);
        quickFix.setDiagnostics(List.of(diagnostic()));
        return quickFix;
    }

    private static Diagnostic diagnostic() {
        Diagnostic diagnostic = new Diagnostic(new Range(new Position(0, 6), new Position(0, 9)),
                "'foo' is declared but its value is never read.");
        diagnostic.setSeverity(DiagnosticSeverity.Hint);
        return diagnostic;
    }

    private LanguageServerItem getLanguageServer(PsiFile file) throws Exception {
        List<LanguageServerItem> languageServers = LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file, null, null)
                .get(5000, TimeUnit.MILLISECONDS);
        assertSize(1, languageServers);
        return languageServers.get(0);
    }
}
