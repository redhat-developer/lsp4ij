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

import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedFormattingFeature;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class test case for client-side on-type formatting tests by emulating LSP 'textDocument/selectionRange',
 * 'textDocument/foldingRange', and 'textDocument/rangeFormatting' responses from the typescript-language-server.
 */
public abstract class LSPClientSideOnTypeFormattingFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    private static final Pattern TYPE_INFO_PATTERN = Pattern.compile("(?ms)//\\s*type\\s*(\\S)[\\t ]*");

    private boolean supportsRangeFormatting = true;

    public LSPClientSideOnTypeFormattingFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
        setClientConfigurable(true);
    }

    /**
     * Sets whether or not the tested language server supports range formatting. The default value is true.
     *
     * @param supportsRangeFormatting true if the tested language server supports range formatting, otherwise false
     */
    protected void setSupportsRangeFormatting(boolean supportsRangeFormatting) {
        this.supportsRangeFormatting = supportsRangeFormatting;
    }

    /**
     * Asserts that <code>fileBodyBefore</code> is formatted into <code>fileBodyAfter</code> when the typing imperative
     * embedded in <code>fileBodyBefore</code> is applied. The typing imperative is just a simple comment of the form
     * <code>// type &lt;character&gt;</code> at the offset at which <code>&lt;character&gt;</code> should be typed.
     * Mock LSP responses must be provided for <code>textDocument/selectionRange</code> and
     * <code>textDocument/foldingRange</code> so that the correct code block can be computed, and a mock LSP response
     * must also be provided for the <code>textDocument/rangeFormatting</code> response.
     *
     * @param fileName                the file name
     * @param fileBodyBefore          the file body before including the embedded typing imperative comment
     * @param fileBodyAfter           the file body after the character has been typed and formatting applied
     * @param mockSelectionRangeJson  the mock selection range JSON response
     * @param mockFoldingRangeJson    the mock folding range JSON response
     * @param mockRangeFormattingJson the mock range formatting JSON response
     * @param clientConfigCustomizer  a function that allows the caller to customize the client configuration as needed
     *                                for the test scenario
     */
    protected void assertOnTypeFormatting(@NotNull String fileName,
                                          @NotNull String fileBodyBefore,
                                          @NotNull String fileBodyAfter,
                                          @NotNull String mockSelectionRangeJson,
                                          @NotNull String mockFoldingRangeJson,
                                          @NotNull String mockRangeFormattingJson,
                                          @Nullable Consumer<ClientConfigurationSettings> clientConfigCustomizer) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);

        List<FoldingRange> mockFoldingRanges = JSONUtils.getLsp4jGson().fromJson(mockFoldingRangeJson, new TypeToken<List<FoldingRange>>() {
        }.getType());
        MockLanguageServer.INSTANCE.setFoldingRanges(mockFoldingRanges);

        List<SelectionRange> mockSelectionRanges = JSONUtils.getLsp4jGson().fromJson(mockSelectionRangeJson, new TypeToken<List<SelectionRange>>() {
        }.getType());
        MockLanguageServer.INSTANCE.setSelectionRanges(mockSelectionRanges);

        List<TextEdit> mockTextEdits = JSONUtils.getLsp4jGson().fromJson(mockRangeFormattingJson, new TypeToken<List<TextEdit>>() {
        }.getType());
        MockLanguageServer.INSTANCE.setFormattingTextEdits(mockTextEdits);

        Project project = myFixture.getProject();
        PsiFile file = myFixture.configureByText(fileName, removeTypeInfo(fileBodyBefore));
        Editor editor = myFixture.getEditor();

        // Initialize the language server
        List<LanguageServerItem> languageServers = new LinkedList<>();
        try {
            ContainerUtil.addAllNotNull(languageServers, LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file.getVirtualFile(), null, null)
                    .get(5000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // Configure the language server for client-side on-type formatting
        LanguageServerItem languageServer = ContainerUtil.getFirstItem(languageServers);
        assertNotNull(languageServer);

        // Use a configurable formatting feature
        LSPClientFeatures clientFeatures = languageServer.getClientFeatures();
        clientFeatures.setFormattingFeature(new UserDefinedFormattingFeature());

        // Enable range formatting if supported by the language server
        if (supportsRangeFormatting) {
            languageServer.getServerCapabilities().setDocumentRangeFormattingProvider(true);
        }

        // Enable dot as a completion trigger character
        languageServer.getServerCapabilities().getCompletionProvider().setTriggerCharacters(List.of("."));

        // Update client configuration as required for this test scenario
        if (clientConfigCustomizer != null) {
            LanguageServerDefinition languageServerDefinition = languageServer.getServerDefinition();
            assertInstanceOf(languageServerDefinition, ClientConfigurableLanguageServerDefinition.class);
            ClientConfigurableLanguageServerDefinition configurableLanguageServerDefinition = (ClientConfigurableLanguageServerDefinition) languageServerDefinition;
            ClientConfigurationSettings clientConfiguration = configurableLanguageServerDefinition.getLanguageServerClientConfiguration();
            assertNotNull(clientConfiguration);
            clientConfigCustomizer.accept(clientConfiguration);
        }

        EditorTestUtil.buildInitialFoldingsInBackground(editor);

        // Derive the offset and character that should be typed
        Pair<Integer, Character> typeInfo = getTypeInfo(fileBodyBefore);
        assertNotNull("No type information found in file body before.", typeInfo);
        int offset = typeInfo.getFirst();
        char character = typeInfo.getSecond();

        // Move to the offset and type the character
        CaretModel caretModel = editor.getCaretModel();
        caretModel.moveToOffset(offset);
        EditorTestUtil.performTypingAction(editor, character);

        // Confirm that the file body has been reformatted as expected
        assertEquals(fileBodyAfter, editor.getDocument().getText());
    }

    @NotNull
    private String removeTypeInfo(@NotNull String fileBodyBefore) {
        return TYPE_INFO_PATTERN.matcher(fileBodyBefore).replaceFirst("");
    }

    @Nullable
    private Pair<Integer, Character> getTypeInfo(@NotNull String fileBodyBefore) {
        Matcher typeInfoMatcher = TYPE_INFO_PATTERN.matcher(fileBodyBefore);
        if (typeInfoMatcher.find()) {
            int offset = typeInfoMatcher.start();
            char character = typeInfoMatcher.group(1).charAt(0);
            return Pair.create(offset, character);
        }

        return null;
    }
}
