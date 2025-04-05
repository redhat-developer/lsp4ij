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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base class test case to test LSP 'textDocument/publishDiagnostic' feature.
 */
public abstract class LSPDiagnosticsFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    public LSPDiagnosticsFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    protected void assertDiagnostics(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull String jsonDiagnostics,
                                     @NotNull String expectedEditorContentText) {
        if (jsonDiagnostics.trim().startsWith("[")) {
            jsonDiagnostics = "{\"diagnostics\":" + jsonDiagnostics + "}";
        }
        PublishDiagnosticsParams params = JSONUtils.getLsp4jGson().fromJson(jsonDiagnostics, PublishDiagnosticsParams.class);
        assertDiagnostics(fileName, editorContentText, params.getDiagnostics(), expectedEditorContentText);
    }

    protected void assertDiagnostics(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull List<Diagnostic> diagnostics,
                                     @NotNull String expectedEditorContentText) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);
        MockLanguageServer.INSTANCE.setDiagnostics(diagnostics);
        // Open editor for a given file name and content (which declares <caret> to know where the completion is triggered).
        myFixture.configureByText(fileName, editorContentText);
        // process highlighting
        myFixture.doHighlighting();

        Document expectedDocument = new DocumentImpl(expectedEditorContentText);
        ExpectedHighlightingData data = new ExpectedHighlightingData(expectedDocument, true, true, true);
        data.init();
        ((CodeInsightTestFixtureImpl) myFixture).collectAndCheckHighlighting(data);
    }


}
