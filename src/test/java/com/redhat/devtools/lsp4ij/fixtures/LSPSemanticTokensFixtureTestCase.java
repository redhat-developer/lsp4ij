/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.fixtures;

import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.features.semanticTokens.inspector.SemanticTokensInspectorData;
import com.redhat.devtools.lsp4ij.features.semanticTokens.inspector.SemanticTokensInspectorListener;
import com.redhat.devtools.lsp4ij.features.semanticTokens.inspector.SemanticTokensInspectorManager;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class test case to test LSP 'textDocument/semanticTokens' feature.
 */
public abstract class LSPSemanticTokensFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    public LSPSemanticTokensFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    /**
     * Test LSP semanticTokens.
     *
     * @param fileName           the file name used to match registered language servers.
     * @param editorContentText  the editor content text.
     * @param jsonSemanticProvider the LSP SemanticTokensWithRegistrationOptions as JSON string.
     * @param jsonSemanticTokens the LSP SemanticTokens as JSON string.
     * @param expected      the expected IJ SemanticTokens inspector data.
     */
    public void assertSemanticTokens(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull String jsonSemanticProvider,
                                     @NotNull String jsonSemanticTokens,
                                     @NotNull String expected) {
        var semanticProvider = JSONUtils.getLsp4jGson().fromJson(jsonSemanticProvider, SemanticTokensWithRegistrationOptions.class);
        var semanticTokens = JSONUtils.getLsp4jGson().fromJson(jsonSemanticTokens, SemanticTokens.class);
        assertSemanticTokens(fileName, editorContentText, semanticProvider, semanticTokens, expected);
    }

    /**
     * Test LSP semanticTokens.
     *
     * @param fileName           the file name used to match registered language servers.
     * @param editorContentText  the editor content text.
     * @param semanticProvider the LSP SemanticTokensWithRegistrationOptions.
     * @param semanticTokens the LSP SemanticTokens.
     * @param expected      the expected IJ SemanticTokens inspector data.
     */
    public void assertSemanticTokens(@NotNull String fileName,
                                     @NotNull String editorContentText,
                                     @NotNull SemanticTokensWithRegistrationOptions semanticProvider,
                                     @NotNull SemanticTokens semanticTokens,
                                     @NotNull String expected) {

        var project = myFixture.getProject();
        var refData = new AtomicReference<SemanticTokensInspectorData>();
        SemanticTokensInspectorListener listener = data -> refData.set(data);
        SemanticTokensInspectorManager.getInstance(project).addSemanticTokensInspectorListener(listener);
        try {

            MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);

            var serverCapabilities = MockLanguageServer.INSTANCE.defaultServerCapabilities();
            serverCapabilities.setSemanticTokensProvider(semanticProvider);
            MockLanguageServer.reset(() -> serverCapabilities);
            MockLanguageServer.INSTANCE.setSemanticTokens(semanticTokens);

            // Open editor for a given file name and content (which declares <caret> to know where the completion is triggered).
            myFixture.configureByText(fileName, editorContentText);
            myFixture.doHighlighting();

            assertNotNull(refData.get());
            String actual = SemanticTokensInspectorManager.format(refData.get(), true, false, false, project);
            assertEquals(expected, actual);

        }
        finally {
            SemanticTokensInspectorManager.getInstance(project).removeSemanticTokensInspectorListener(listener);
        }
    }

}
