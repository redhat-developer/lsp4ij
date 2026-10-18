/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.DocumentConnectionScope;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.io.File;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Formatting must work for save and closed files, which have no editor. */
public class ClosedDocumentFormattingTest extends LSPCodeInsightFixtureTestCase {

    public ClosedDocumentFormattingTest() {
        super("*.ts");
    }

    public void testFullFormattingWithoutEditor() throws Exception {
        assertClosedDocumentFormatting(null);
    }

    public void testRangeFormattingWithoutEditor() throws Exception {
        assertClosedDocumentFormatting(new TextRange(0, 13));
    }

    private void assertClosedDocumentFormatting(TextRange range) throws Exception {
        var service = new OpenDocumentFormattingService();
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setDocumentRangeFormattingProvider(true);
            return capabilities;
        });
        MockLanguageServer.INSTANCE.setTextDocumentService(service);
        var file = myFixture.addFileToProject("closed.ts", "const value=0;");
        var source = file.getVirtualFile();
        var document = LSPIJUtils.getDocument(source);
        assertNotNull(document);
        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(5, TimeUnit.SECONDS);
        // Keep the server alive while closing this file, as when another file is open.
        var otherFile = myFixture.addFileToProject("other.ts", "const other=1;");
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(otherFile, null, null).get(5, TimeUnit.SECONDS);
        var closed = new CompletableFuture<DidCloseTextDocumentParams>();
        service.setDidCloseCallback(closed);
        // The headless fixture has no editor tabs. Deliver the public close event emitted
        // by FileEditorManager in an IDE, and verify it reaches the mock server over LSP.
        myFixture.getProject().getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
                .fileClosed(FileEditorManager.getInstance(myFixture.getProject()), source);
        PlatformTestUtil.waitWithEventsDispatching("the file must disconnect before save formatting", closed::isDone, 5);
        var wrapper = servers.get(0).getServerWrapper();
        assertNull(wrapper.getOpenedDocument(wrapper.toUri(source)));
        assertEquals(0, LSPIJUtils.editorsForFile(source, myFixture.getProject()).length);

        // The disk and the previous didOpen both contain 0. Save formatting must reconnect
        // the current dirty buffer, even though no editor owns it any more.
        String original = "const value=1;";
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> document.setText(original));
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
        var request = new FormattingResult(original, new CompletableFuture<>());
        new LSPFormattingSupport(file).format(document, file, null, range, request);

        assertEquals("const value = 1;", request.result().get(5, TimeUnit.SECONDS));
        assertEquals(original, service.formattedText.get());
        assertEquals(range != null, service.rangeFormatting);
        assertEquals("Formatting returns text for the platform to apply", original, document.getText());

        PlatformTestUtil.waitWithEventsDispatching("save formatting must release its closed buffer",
                () -> wrapper.getOpenedDocument(wrapper.toUri(source)) == null, 5);
        assertEquals("Only the other persistent document remains", 1, wrapper.getOpenedDocuments().size());
        int openedCount = service.openedCount.get();
        var repeated = new FormattingResult(original, new CompletableFuture<>());
        new LSPFormattingSupport(file).format(document, file, null, range, repeated);
        assertEquals("const value = 1;", repeated.result().get(5, TimeUnit.SECONDS));
        PlatformTestUtil.waitWithEventsDispatching("repeated formatting must also release its connection",
                () -> wrapper.getOpenedDocument(wrapper.toUri(source)) == null, 5);
        assertEquals(openedCount + 1, service.openedCount.get());
        assertEquals(1, wrapper.getOpenedDocuments().size());
    }

    public void testChangedBufferRejectsOldFormattingReply() throws Exception {
        assertStaleReplyIgnored(false);
    }

    public void testChangedAndRestoredBufferRejectsOldFormattingReply() throws Exception {
        assertStaleReplyIgnored(true);
    }

    private void assertStaleReplyIgnored(boolean restoreOriginal) throws Exception {
        String original = "const value=1;";
        var file = myFixture.configureByText("stale.ts", original);
        var document = myFixture.getEditor().getDocument();
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(5, TimeUnit.SECONDS);
        var request = new FormattingResult(original, new CompletableFuture<>());
        var support = new LSPFormattingSupport(file) {
            @Override
            protected CompletableFuture<List<? extends TextEdit>> getFormatting(
                    LSPFormattingParams params, CancellationSupport cancellationSupport) {
                // Change the buffer after request construction and before its reply is consumed.
                WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
                    document.setText("const other=2;");
                    if (restoreOriginal) {
                        document.setText(original);
                    }
                });
                return CompletableFuture.completedFuture(List.of(
                        new TextEdit(new Range(new Position(0, 11), new Position(0, 12)), " = ")));
            }
        };
        support.format(document, file, null, null, request);
        assertNull("A stale reply must not replace the current buffer", request.result().get(5, TimeUnit.SECONDS));
        assertEquals(restoreOriginal ? original : "const other=2;", document.getText());
    }

    public void testAlreadyStaleRequestDoesNotAskServerForEdits() throws Exception {
        var file = myFixture.configureByText("changed.ts", "const value=2;");
        var request = new FormattingResult("const value=1;", new CompletableFuture<>());
        var support = new LSPFormattingSupport(file) {
            @Override
            protected CompletableFuture<List<? extends TextEdit>> getFormatting(
                    LSPFormattingParams params, CancellationSupport cancellationSupport) {
                fail("The request text no longer owns the current document");
                return null;
            }
        };
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(5, TimeUnit.SECONDS);
        support.format(myFixture.getEditor().getDocument(), file, null, null, request);
        assertNull(request.result().get(5, TimeUnit.SECONDS));
    }

    public void testCancelledFormattingDoesNotReturnOldText() throws Exception {
        String original = "const value=1;";
        var file = myFixture.configureByText("cancelled.ts", original);
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(5, TimeUnit.SECONDS);
        var request = new FormattingResult(original, new CompletableFuture<>());
        var support = new LSPFormattingSupport(file) {
            @Override
            protected CompletableFuture<List<? extends TextEdit>> getFormatting(
                    LSPFormattingParams params, CancellationSupport cancellationSupport) {
                return CompletableFuture.failedFuture(new CancellationException());
            }
        };
        support.format(myFixture.getEditor().getDocument(), file, null, null, request);
        assertNull(request.result().get(5, TimeUnit.SECONDS));
    }

    public void testCancellationDuringReconnectDoesNotStartFormatting() throws Exception {
        String original = "const value=1;";
        var file = myFixture.configureByText("reconnecting.ts", original);
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(5, TimeUnit.SECONDS);
        var lookup = new CompletableFuture<List<LanguageServerItem>>();
        var request = new FormattingResult(original, new CompletableFuture<>());
        var support = new LSPFormattingSupport(file) {
            @Override
            CompletableFuture<List<LanguageServerItem>> findFormattingServers(PsiFile file, TextRange range, DocumentConnectionScope connections) {
                // Cancel after lookup starts but before its future is registered with the request.
                cancel();
                return lookup;
            }

            @Override
            protected CompletableFuture<List<? extends TextEdit>> getFormatting(
                    LSPFormattingParams params, CancellationSupport cancellationSupport) {
                fail("A cancelled reconnect must not submit a formatting request");
                return null;
            }
        };
        assertThrows(ProcessCanceledException.class,
                () -> support.format(myFixture.getEditor().getDocument(), file, null, null, request));
        assertTrue("The connection wait must also be cancelled", lookup.isCancelled());
    }

    public void testStoppedServerReplyIsIgnored() throws Exception {
        String original = "const value=1;";
        var file = myFixture.configureByText("stopped.ts", original);
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(5, TimeUnit.SECONDS);
        var request = new FormattingResult(original, new CompletableFuture<>());
        var support = new LSPFormattingSupport(file) {
            @Override
            protected CompletableFuture<List<? extends TextEdit>> getFormatting(
                    LSPFormattingParams params, CancellationSupport cancellationSupport) {
                return params.formattingServer().getServerWrapper().stop().thenApply(ignored -> List.of(
                        new TextEdit(new Range(new Position(0, 11), new Position(0, 12)), " = ")));
            }
        };
        support.format(myFixture.getEditor().getDocument(), file, null, null, request);
        assertNull("A retired server no longer owns the formatting request", request.result().get(5, TimeUnit.SECONDS));
    }

    public void testNoFormattingServerCompletesWithoutChanges() throws Exception {
        var file = myFixture.configureByText("unmapped.txt", "text");
        var request = new FormattingResult("text", new CompletableFuture<>());
        new LSPFormattingSupport(file).format(myFixture.getEditor().getDocument(), file, null, null, request);
        assertNull(request.result().get(5, TimeUnit.SECONDS));
    }

    /** A formatter whose edits require textDocument/didOpen, as real LSP servers commonly do. */
    private static class OpenDocumentFormattingService extends MockTextDocumentService {
        private final ConcurrentMap<String, String> openDocuments = new ConcurrentHashMap<>();
        private final AtomicReference<String> formattedText = new AtomicReference<>();
        private final AtomicInteger openedCount = new AtomicInteger();
        private volatile boolean rangeFormatting;

        private OpenDocumentFormattingService() {
            super(CompletableFuture::completedFuture);
        }

        @Override
        public void didOpen(DidOpenTextDocumentParams params) {
            openedCount.incrementAndGet();
            openDocuments.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
            super.didOpen(params);
        }

        @Override
        public void didClose(DidCloseTextDocumentParams params) {
            openDocuments.remove(params.getTextDocument().getUri());
            super.didClose(params);
        }

        @Override
        public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
            return format(params.getTextDocument().getUri());
        }

        @Override
        public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
            rangeFormatting = true;
            return format(params.getTextDocument().getUri());
        }

        private CompletableFuture<List<? extends TextEdit>> format(String uri) {
            String text = openDocuments.get(uri);
            formattedText.set(text);
            return CompletableFuture.completedFuture(text == null ? List.of() : List.of(
                    new TextEdit(new Range(new Position(0, 11), new Position(0, 12)), " = ")));
        }
    }

    private record FormattingResult(String text, CompletableFuture<String> result) implements AsyncFormattingRequest {
        @Override
        public String getDocumentText() {
            return text;
        }

        @Override
        public File getIOFile() {
            return null;
        }

        @Override
        public List<TextRange> getFormattingRanges() {
            return List.of();
        }

        @Override
        public boolean canChangeWhitespaceOnly() {
            return true;
        }

        @Override
        public boolean isQuickFormat() {
            return false;
        }

        @Override
        public FormattingContext getContext() {
            throw new UnsupportedOperationException("The document and file are passed directly to format");
        }

        @Override
        public void onTextReady(String formatted) {
            result.complete(formatted);
        }

        @Override
        public void onError(String title, String message) {
            result.completeExceptionally(new AssertionError(title + ": " + message));
        }

        @Override
        public void onError(String title, String message, int offset) {
            onError(title, message);
        }
    }
}
