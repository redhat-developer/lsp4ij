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
import org.eclipse.lsp4j.util.Ranges;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    public void testManyDiagnosticsWithNestedOverlappingAndEqualRangesAreAllKept() throws Exception {
        PsiFile file = myFixture.configureByText("test.ts", "const foo = 1;");
        LanguageServerItem languageServer = getLanguageServer(file);
        var diagnostics = new LSPDiagnosticsForServer(languageServer, file.getVirtualFile(), null);
        List<Diagnostic> published = manyDiagnostics();

        assertTrue(diagnostics.update("test", published));

        assertSameElements(diagnostics.getDiagnostics(), published);
    }

    public void testADiagnosticComesBeforeTheDiagnosticsWhoseRangeItsRangeContains() {
        List<Diagnostic> sorted = manyDiagnostics().stream()
                .sorted(LSPDiagnosticsForServer.CONTAINING_RANGE_FIRST)
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                Range earlier = sorted.get(i).getRange();
                Range later = sorted.get(j).getRange();
                assertFalse(later + " contains " + earlier + ", so it must come first",
                        Ranges.containsRange(later, earlier) && !later.equals(earlier));
            }
        }

        // Whichever of the two comes first in the list
        Diagnostic outer = diagnostic(0, 10, "outer");
        Diagnostic inner = diagnostic(2, 5, "inner");
        assertEquals(List.of(outer, inner),
                Stream.of(inner, outer).sorted(LSPDiagnosticsForServer.CONTAINING_RANGE_FIRST).toList());
        assertEquals(List.of(outer, inner),
                Stream.of(outer, inner).sorted(LSPDiagnosticsForServer.CONTAINING_RANGE_FIRST).toList());
    }

    /**
     * Returns 32 diagnostics of one line whose ranges are as a language server may publish them: nested, equal,
     * overlapping, apart and empty. Sorted by the containment of their ranges alone, as the diagnostics were, their sort
     * fails with "Comparison method violates its general contract!" (#2323).
     */
    private static List<Diagnostic> manyDiagnostics() {
        int[][] ranges = {
                {33, 33}, {13, 13}, {6, 12}, {37, 45}, {21, 25}, {3, 12}, {31, 39}, {15, 15}, {6, 9}, {13, 21}, {13, 22},
                {35, 43}, {15, 24}, {14, 14}, {28, 28}, {28, 30}, {32, 38}, {37, 42}, {30, 30}, {35, 38}, {19, 26},
                {32, 34}, {28, 37}, {38, 42}, {1, 4}, {25, 26}, {11, 16}, {1, 10}, {8, 10}, {28, 36}, {0, 4}, {16, 25}
        };
        return IntStream.range(0, ranges.length)
                .mapToObj(i -> diagnostic(ranges[i][0], ranges[i][1], "diagnostic " + i))
                .toList();
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
