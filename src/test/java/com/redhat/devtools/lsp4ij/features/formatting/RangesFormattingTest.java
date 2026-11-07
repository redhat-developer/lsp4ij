/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.capabilities.ClientCapabilitiesFactory;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.DocumentRangeFormattingOptions;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentRangesFormattingParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * When IntelliJ formats several ranges of a file at once, as Reformat Code does for "Only VCS changed text", every
 * range is formatted: by one 'textDocument/rangesFormatting' (LSP 3.18) when the language server supports it, and
 * otherwise by one 'textDocument/rangeFormatting' per range.
 */
public class RangesFormattingTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = """
            const a=1;
            const b=2;
            const c=3;
            """;
    // The first and the last line of TEXT
    private static final List<TextRange> RANGES = List.of(new TextRange(0, 10), new TextRange(22, 32));
    private static final Range FIRST_LINE = new Range(new Position(0, 0), new Position(0, 10));
    private static final Range LAST_LINE = new Range(new Position(2, 0), new Position(2, 10));

    public RangesFormattingTest() {
        super("*.ts");
    }

    public void testEachRangeIsFormattedByAServerWithoutRangesSupport() throws Exception {
        var service = new LineFormattingService(0);
        start(service, false);

        assertEquals("""
                const a = 1;
                const b=2;
                const c = 3;
                """, reformat());
        assertEquals(List.of(FIRST_LINE, LAST_LINE), service.rangeRequests);
        assertEmpty(service.rangesRequests);
    }

    public void testAServerWithRangesSupportFormatsAllRangesAtOnce() throws Exception {
        var service = new LineFormattingService(0);
        start(service, true);

        assertEquals("""
                const a = 1;
                const b=2;
                const c = 3;
                """, reformat());
        assertEquals(List.of(List.of(FIRST_LINE, LAST_LINE)), service.rangesRequests);
        assertEmpty(service.rangeRequests);
    }

    public void testRangesWhoseEditsOverlapAreFormattedAsOneRange() throws Exception {
        // The server formats a line more on each side of a range, so both ranges' edits change the middle line.
        var service = new LineFormattingService(1);
        start(service, false);

        assertEquals("""
                const a = 1;
                const b = 2;
                const c = 3;
                """, reformat());
        assertEquals(List.of(FIRST_LINE, LAST_LINE, new Range(new Position(0, 0), new Position(2, 10))),
                service.rangeRequests);
    }

    public void testTheClientDeclaresRangesSupport() {
        assertEquals(Boolean.TRUE,
                ClientCapabilitiesFactory.create(null).getTextDocument().getRangeFormatting().getRangesSupport());
    }

    private static void start(MockTextDocumentService service, boolean rangesSupport) {
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            var options = new DocumentRangeFormattingOptions();
            options.setRangesSupport(rangesSupport);
            capabilities.setDocumentRangeFormattingProvider(options);
            return capabilities;
        });
        MockLanguageServer.INSTANCE.setTextDocumentService(service);
    }

    private String reformat() throws Exception {
        var file = myFixture.configureByText("test.ts", TEXT);
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(5, TimeUnit.SECONDS);
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                () -> CodeStyleManager.getInstance(myFixture.getProject()).reformatText(file, RANGES));
        return myFixture.getEditor().getDocument().getText();
    }

    /** Puts spaces around the '=' of every line of TEXT it is asked to format, and of the given number of lines around them. */
    private static class LineFormattingService extends MockTextDocumentService {
        private final int extraLines;
        private final List<Range> rangeRequests = new CopyOnWriteArrayList<>();
        private final List<List<Range>> rangesRequests = new CopyOnWriteArrayList<>();

        private LineFormattingService(int extraLines) {
            super(CompletableFuture::completedFuture);
            this.extraLines = extraLines;
        }

        @Override
        public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
            rangeRequests.add(params.getRange());
            return CompletableFuture.completedFuture(format(List.of(params.getRange())));
        }

        @Override
        public CompletableFuture<List<? extends TextEdit>> rangesFormatting(DocumentRangesFormattingParams params) {
            rangesRequests.add(params.getRanges());
            return CompletableFuture.completedFuture(format(params.getRanges()));
        }

        private List<TextEdit> format(List<Range> ranges) {
            var edits = new ArrayList<TextEdit>();
            for (var range : ranges) {
                int first = Math.max(0, range.getStart().getLine() - extraLines);
                int last = Math.min(2, range.getEnd().getLine() + extraLines);
                for (int line = first; line <= last; line++) {
                    edits.add(new TextEdit(new Range(new Position(line, 7), new Position(line, 8)), " = "));
                }
            }
            return edits;
        }
    }
}
