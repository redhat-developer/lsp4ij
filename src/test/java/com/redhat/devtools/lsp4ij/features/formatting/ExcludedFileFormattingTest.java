/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.excludedFiles.GlobPatternDescriptor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * When IntelliJ reformats files as "Reformat code" before a commit does, a file which matches a "Do not format"
 * pattern of Settings > Editor > Code Style > Formatter isn't formatted by its language server.
 */
public class ExcludedFileFormattingTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "const a=1;";

    public ExcludedFileFormattingTest() {
        super("*.ts");
    }

    public void testExcludedFileIsNotFormatted() throws Exception {
        var service = new EqualsFormattingService();
        start(service);

        assertEquals(TEXT, reformat("*.ts"));
        assertEmpty(service.formattingRequests);
    }

    public void testFileWhichIsNotExcludedIsFormatted() throws Exception {
        var service = new EqualsFormattingService();
        start(service);

        assertEquals("const a = 1;", reformat("*.properties"));
        assertSize(1, service.formattingRequests);
    }

    private static void start(MockTextDocumentService service) {
        MockLanguageServer.reset();
        MockLanguageServer.INSTANCE.setTextDocumentService(service);
    }

    private String reformat(String doNotFormat) throws Exception {
        var project = myFixture.getProject();
        var file = myFixture.configureByText("test.ts", TEXT);
        LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, null, null).get(5, TimeUnit.SECONDS);
        CodeStyle.doWithTemporarySettings(project, CodeStyle.getSettings(project), settings -> {
            settings.getExcludedFiles().addDescriptor(new GlobPatternDescriptor(doNotFormat));
            // The processor which "Reformat code" before a commit runs, for one file
            new ReformatCodeProcessor(file, false).runWithoutProgress();
        });
        return myFixture.getEditor().getDocument().getText();
    }

    /** Puts spaces around the '=' of TEXT, and keeps the 'textDocument/formatting' requests it gets. */
    private static class EqualsFormattingService extends MockTextDocumentService {
        private final List<DocumentFormattingParams> formattingRequests = new CopyOnWriteArrayList<>();

        private EqualsFormattingService() {
            super(CompletableFuture::completedFuture);
        }

        @Override
        public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
            formattingRequests.add(params);
            return CompletableFuture.completedFuture(
                    List.of(new TextEdit(new Range(new Position(0, 7), new Position(0, 8)), " = ")));
        }
    }
}
