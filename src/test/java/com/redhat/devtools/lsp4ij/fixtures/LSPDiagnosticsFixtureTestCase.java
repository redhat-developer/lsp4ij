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

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsApplier;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Base class test case to test LSP 'textDocument/publishDiagnostic' feature.
 */
public abstract class LSPDiagnosticsFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    // The diagnostics which the language server gives when it is pulled
    private @NotNull List<Diagnostic> pulledDiagnostics = List.of();

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
        registerDiagnosticsPass();
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);
        MockLanguageServer.INSTANCE.setPublishDiagnostics(publishDiagnostics);
        MockLanguageServer.INSTANCE.setPullDiagnostics(pullDiagnostics);
        pulledDiagnostics = pullDiagnostics != null && pullDiagnostics.isLeft() ? pullDiagnostics.getLeft().getItems() : List.of();
        // Open editor for a given file name and content (which declares <caret> to know where the completion is triggered).
        myFixture.configureByText(fileName, editorContentText);

        // The language server publishes its diagnostics once it gets the didOpen of the file, which the pull follows:
        // the wait includes its start
        assertDiagnostics(publishDiagnostics, SERVER_START_TIMEOUT_SECONDS, expectedEditorContentText);
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
        assertDiagnostics(publishDiagnostics.getDiagnostics(), IN_MEMORY_TIMEOUT_SECONDS, expectedEditorContentText);
    }

    private void assertDiagnostics(@NotNull List<Diagnostic> publishedDiagnostics,
                                   int timeoutSeconds,
                                   @NotNull String expectedEditorContentText) {
        awaitDiagnostics(publishedDiagnostics, timeoutSeconds, expectedEditorContentText);
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
        ExpectedHighlightingData data = expectedHighlighting(expectedEditorContentText);
        ((CodeInsightTestFixtureImpl) myFixture).collectAndCheckHighlighting(data);
    }

    /**
     * Registers the diagnostics pass, as the first highlighting in the JVM does, which
     * LSPDiagnosticsApplierTest.registeredGroupId() does too. Until then, a refresh of the diagnostics waits for it, so
     * in a new JVM, their refresh would wait for the highlighting of the test, which comes after the wait for it.
     */
    private void registerDiagnosticsPass() {
        if (LSPDiagnosticsApplier.GROUP_ID == -1) {
            // Let the project's registrar register the pass factory, with a file which no language server gets
            myFixture.configureByText("first.txt", "");
            myFixture.doHighlighting();
        }
        assertTrue("the first highlighting must register the diagnostics pass", LSPDiagnosticsApplier.GROUP_ID != -1);
    }

    /**
     * Waits until LSP4IJ has read what the language server sent as it read the didOpen of the file, until the
     * diagnostics of the file, which reach LSP4IJ asynchronously, are the given published ones and the pulled ones,
     * until no refresh of them is pending, and until the editor shows them as expected, within the given number of
     * seconds. Without the first, a file which is to have no diagnostics would have none before its language server
     * has read its didOpen too. A refresh is pending from the time it is requested until it has applied its
     * highlights, but the diagnostics are stored just before it is requested: the highlights of the editor tell that it
     * has come.
     */
    private void awaitDiagnostics(@NotNull List<Diagnostic> publishedDiagnostics,
                                  int timeoutSeconds,
                                  @NotNull String expectedEditorContentText) {
        Set<Diagnostic> expected = new HashSet<>(publishedDiagnostics);
        expected.addAll(pulledDiagnostics);
        VirtualFile file = myFixture.getFile().getVirtualFile();
        URI uri = LSPIJUtils.toUri(file);
        var applier = LSPDiagnosticsApplier.getInstance(myFixture.getProject());
        ExpectedHighlightingData highlighting = expectedHighlighting(expectedEditorContentText);
        var afterDidOpen = new AtomicReference<CompletableFuture<?>>();
        PlatformTestUtil.waitWithEventsDispatching(
                () -> "the editor must show the diagnostics " + describe(expected) + " of the language server, but "
                        + (openedBy(uri) != null ? "LSP4IJ has opened the file" : "LSP4IJ hasn't opened the file")
                        + (didOpenRead(uri) ? ", the language server has read its didOpen" :
                        ", the language server hasn't read its didOpen")
                        + (afterDidOpen.get() != null && afterDidOpen.get().isDone() ? ", LSP4IJ has read" :
                        ", LSP4IJ hasn't read") + " what the server sent as it read it, "
                        + "the file has " + describe(diagnosticsOf(uri)) + ", "
                        + (applier.isRefreshPending(file) ? "a" : "no") + " refresh of them is pending, and the "
                        + "highlights of the editor "
                        + Objects.requireNonNullElse(highlightingMismatch(highlighting), "are those expected"),
                () -> hasReadWhatTheServerSentOnDidOpen(uri, afterDidOpen)
                        && expected.equals(diagnosticsOf(uri))
                        && !applier.isRefreshPending(file)
                        && highlightingMismatch(highlighting) == null,
                timeoutSeconds);
    }

    /**
     * Returns true once LSP4IJ has read all that the language server sent as it read the didOpen of the file with the
     * given URI, such as the diagnostics it published then. Once the server has read the didOpen, this asks it, once,
     * for the diagnostics of the file, which it answers after those, as it reads its messages in order: LSP4IJ, which
     * reads them in order too, reads those diagnostics first. The answer itself only marks that point; it doesn't
     * reach the diagnostics of LSP4IJ.
     *
     * @param uri          the URI of the file.
     * @param afterDidOpen the request that this method sends, null until it does.
     * @return true once LSP4IJ has read the answer.
     */
    private boolean hasReadWhatTheServerSentOnDidOpen(@NotNull URI uri,
                                                      @NotNull AtomicReference<CompletableFuture<?>> afterDidOpen) {
        if (afterDidOpen.get() == null) {
            var server = openedBy(uri);
            if (server == null || !didOpenRead(uri)) {
                return false;
            }
            afterDidOpen.set(server.getLanguageServer().getTextDocumentService()
                    .diagnostic(new DocumentDiagnosticParams(new TextDocumentIdentifier(uri.toString()))));
        }
        return afterDidOpen.get().isDone();
    }

    /**
     * Returns the started language server which has opened the file with the given URI, or null if none has.
     */
    private @Nullable LanguageServerWrapper openedBy(@NotNull URI uri) {
        return LanguageServiceAccessor.getInstance(myFixture.getProject()).getStartedServers().stream()
                .filter(server -> server.getOpenedDocument(uri) != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns true once the mock language server has read the didOpen of the file with the given URI.
     */
    private static boolean didOpenRead(@NotNull URI uri) {
        return MockLanguageServer.INSTANCE.getTextDocumentService().getOpenCloseNotifications().stream()
                .anyMatch(notification -> notification.open() && notification.uri().equals(uri));
    }

    /**
     * Returns the given diagnostics as "[line:character-line:character severity message, ...]", with the positions of
     * LSP, which start at 0, sorted.
     */
    private static @NotNull String describe(@NotNull Collection<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .map(diagnostic -> {
                    var range = diagnostic.getRange();
                    var message = diagnostic.getMessage();
                    return range.getStart().getLine() + ":" + range.getStart().getCharacter() + "-"
                            + range.getEnd().getLine() + ":" + range.getEnd().getCharacter() + " "
                            + diagnostic.getSeverity() + " "
                            + (message.isLeft() ? message.getLeft() : message.getRight().getValue());
                })
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Returns the highlighting which the given text with highlighting markup expects.
     */
    private static @NotNull ExpectedHighlightingData expectedHighlighting(@NotNull String expectedEditorContentText) {
        Document expectedDocument = new DocumentImpl(expectedEditorContentText);
        ExpectedHighlightingData data = new ExpectedHighlightingData(expectedDocument, true, true, true);
        data.init();
        return data;
    }

    /**
     * Returns how the highlights which the editor shows now, before any highlighting of the test, differ from the
     * expected ones, or null if they don't.
     */
    private @Nullable String highlightingMismatch(@NotNull ExpectedHighlightingData highlighting) {
        Document document = myFixture.getEditor().getDocument();
        try {
            highlighting.checkResult(myFixture.getFile(),
                    DaemonCodeAnalyzerImpl.getHighlights(document, null, myFixture.getProject()), document.getText());
            return null;
        } catch (AssertionError e) {
            return "differ: " + e.getMessage();
        }
    }

    /**
     * Returns the diagnostics of the language servers for the file with the given URI.
     */
    private @NotNull Set<Diagnostic> diagnosticsOf(@NotNull URI uri) {
        return LanguageServiceAccessor.getInstance(myFixture.getProject()).getStartedServers().stream()
                .map(server -> server.getOpenedDocument(uri))
                .filter(Objects::nonNull)
                .flatMap(document -> document.getDiagnostics().stream())
                .collect(Collectors.toSet());
    }

    @NotNull
    private static PublishDiagnosticsParams getPublishDiagnosticsParams(@NotNull String publishJsonDiagnostics) {
        if (publishJsonDiagnostics.trim().startsWith("[")) {
            publishJsonDiagnostics = "{\"diagnostics\":" + publishJsonDiagnostics + "}";
        }
        return JSONUtils.getLsp4jGson().fromJson(publishJsonDiagnostics, PublishDiagnosticsParams.class);
    }

}
