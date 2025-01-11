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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedCompletionFeature;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Base class test case for client-side on-type formatting tests by emulating LSP 'textDocument/selectionRange',
 * 'textDocument/foldingRange', and 'textDocument/rangeFormatting' responses from the typescript-language-server.
 */
public abstract class LSPCompletionClientConfigFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    private static final String CARET = "<caret>";

    public LSPCompletionClientConfigFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    /**
     * Asserts that <code>fileBodyBefore</code> is transformed into <code>fileBodyAfter</code> when triggering and
     * accepting the first completion. <code>fileBodyBefore</code> should include a <code>&lt;caret&gt;</code>
     * placeholder for where completion should be triggered, and <code>fileBodyAfter</code> should include either
     * a <code>&lt;template&gt;</code> placeholder or a <code>&lt;caret&gt;</code> placeholder based on whether or
     * not a template or just the caret is expected at the offset corresponding to that placeholder.
     *
     * @param fileName                      the file name
     * @param fileBodyBefore                the file body before including the embedded typing imperative comment
     * @param fileBodyAfter                 the file body after the character has been typed and formatting applied
     * @param mockTextCompletionJson        the mock text/completion JSON response
     * @param mockCompletionItemResolveJson the mock completionItem/resolve JSON response
     * @param clientConfigCustomizer        a function that allows the caller to customize the client configuration as
     *                                      needed for the test scenario
     */
    protected void assertTemplateForArguments(@NotNull String fileName,
                                              @NotNull String fileBodyBefore,
                                              @NotNull String fileBodyAfter,
                                              @NotNull String mockTextCompletionJson,
                                              @NotNull String mockCompletionItemResolveJson,
                                              @Nullable Consumer<ClientConfigurationSettings> clientConfigCustomizer) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(200);

        CompletionList mockCompletionList = JSONUtils.getLsp4jGson().fromJson(mockTextCompletionJson, CompletionList.class);
        MockLanguageServer.INSTANCE.setCompletionList(mockCompletionList);

        CompletionItem mockCompletionItem = JSONUtils.getLsp4jGson().fromJson(mockCompletionItemResolveJson, CompletionItem.class);
        MockLanguageServer.INSTANCE.setCompletionItem(mockCompletionItem);

        Project project = myFixture.getProject();
        PsiFile file = myFixture.configureByText(fileName, removeCaret(fileBodyBefore));
        Editor editor = myFixture.getEditor();
        CaretModel caretModel = editor.getCaretModel();

        // Initialize the language server
        List<LanguageServerItem> languageServers = new LinkedList<>();
        try {
            ContainerUtil.addAllNotNull(languageServers, LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file.getVirtualFile(), null, null)
                    .get(5000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // Configure the language server's completion client configuration
        LanguageServerItem languageServer = ContainerUtil.getFirstItem(languageServers);
        assertNotNull(languageServer);

        // Use a configurable completion feature
        LSPClientFeatures clientFeatures = languageServer.getClientFeatures();
        clientFeatures.setCompletionFeature(new UserDefinedCompletionFeature());

        // Enable completion item resolution
        languageServer.getServerCapabilities().setCompletionProvider(new CompletionOptions(true, null));

        // Update client configuration as required for this test scenario
        if (clientConfigCustomizer != null) {
            LanguageServerDefinition languageServerDefinition = languageServer.getServerDefinition();
            assertInstanceOf(languageServerDefinition, ClientConfigurableLanguageServerDefinition.class);
            ClientConfigurableLanguageServerDefinition configurableLanguageServerDefinition = (ClientConfigurableLanguageServerDefinition) languageServerDefinition;
            ClientConfigurationSettings clientConfiguration = configurableLanguageServerDefinition.getLanguageServerClientConfiguration();
            assertNotNull(clientConfiguration);
            clientConfigCustomizer.accept(clientConfiguration);
        }

        // Move to the offset at which completion should be triggered
        int completionTriggerOffset = caretOffset(fileBodyBefore);
        assertTrue(completionTriggerOffset > -1);
        caretModel.moveToOffset(completionTriggerOffset);

        // Trigger completion
        LookupElement[] lookupElements = myFixture.completeBasic();
        assertFalse(ArrayUtil.isEmpty(lookupElements));

        // Find the completion which should be selected
        LookupElement lookupElement = ContainerUtil.find(
                lookupElements,
                lookupElementCandidate -> mockCompletionItem.getLabel().equals(lookupElementCandidate.getLookupString())
        );
        assertNotNull(lookupElement);

        // Select the completion
        myFixture.selectItem(lookupElement);

        // Confirm that the file body has been reformatted as expected
        assertEquals(removeCaret(fileBodyAfter), editor.getDocument().getText());

        // And if a final caret was specified, confirm it
        int caretOffset = caretOffset(fileBodyAfter);
        if (caretOffset > -1) {
            assertEquals(caretOffset, caretModel.getOffset());
        }
    }

    @NotNull
    private static String removeCaret(@NotNull String fileBody) {
        return fileBody.replace(CARET, "");
    }

    private static int caretOffset(@NotNull String fileBody) {
        return fileBody.indexOf(CARET);
    }
}
