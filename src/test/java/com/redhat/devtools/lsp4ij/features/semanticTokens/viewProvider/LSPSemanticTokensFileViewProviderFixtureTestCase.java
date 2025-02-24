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

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Base test fixture for the semantic tokens-based file view provider.
 */
public abstract class LSPSemanticTokensFileViewProviderFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    protected LSPSemanticTokensFileViewProviderFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
        setClientConfigurable(true);
    }

    private PsiFile initialize(@NotNull String fileName,
                               @NotNull String fileBody,
                               @Nullable String mockSemanticTokensProviderJson,
                               @Nullable String mockSemanticTokensJson,
                               boolean enabled) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);

        // Must both be non-null or null together
        assertEquals(mockSemanticTokensProviderJson != null, mockSemanticTokensJson != null);

        ServerCapabilities serverCapabilities = MockLanguageServer.defaultServerCapabilities();
        SemanticTokensWithRegistrationOptions mockSemanticTokensProvider = mockSemanticTokensProviderJson != null ?
                JSONUtils.getLsp4jGson().fromJson(mockSemanticTokensProviderJson, SemanticTokensWithRegistrationOptions.class) :
                null;
        serverCapabilities.setSemanticTokensProvider(mockSemanticTokensProvider);
        MockLanguageServer.reset(() -> serverCapabilities);

        SemanticTokens mockSemanticTokens = mockSemanticTokensJson != null ?
                JSONUtils.getLsp4jGson().fromJson(mockSemanticTokensJson, SemanticTokens.class) :
                null;
        MockLanguageServer.INSTANCE.setSemanticTokens(mockSemanticTokens);

        PsiFile file = myFixture.configureByText(fileName, fileBody);

        // Initialize the language server
        List<LanguageServerItem> languageServers = new LinkedList<>();
        try {
            Project project = file.getProject();
            VirtualFile virtualFile = file.getVirtualFile();
            ContainerUtil.addAllNotNull(languageServers, LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(virtualFile, null, null)
                    .get(5000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            fail(e.getMessage());
        }
        LanguageServerItem languageServer = ContainerUtil.getFirstItem(languageServers);
        assertNotNull(languageServer);

        // Enable or disable the view provider as requested
        LanguageServerDefinition languageServerDefinition = languageServer.getServerDefinition();
        assertInstanceOf(languageServerDefinition, ClientConfigurableLanguageServerDefinition.class);
        ClientConfigurableLanguageServerDefinition configurableLanguageServerDefinition = (ClientConfigurableLanguageServerDefinition) languageServerDefinition;
        ClientConfigurationSettings clientConfiguration = configurableLanguageServerDefinition.getLanguageServerClientConfiguration();
        assertNotNull(clientConfiguration);
        clientConfiguration.editor.enableTextMateFileViewProvider = enabled;

        // Force a highlighting pass to populate the view provider's semantic tokens
        myFixture.doHighlighting();

        return file;
    }

    protected void assertViewProviderEnabled(@NotNull String fileName,
                                             @NotNull String fileBody,
                                             @Nullable String mockSemanticTokensProviderJson,
                                             @Nullable String mockSemanticTokensJson,
                                             @NotNull Map<Function<String, Integer>, IElementType> tokenVerifiers) {
        PsiFile file = initialize(fileName, fileBody, mockSemanticTokensProviderJson, mockSemanticTokensJson, true);

        // Confirm the token element types
        for (Map.Entry<Function<String, Integer>, IElementType> tokenVerifier : tokenVerifiers.entrySet()) {
            Function<String, Integer> tokenSearchFunction = tokenVerifier.getKey();
            IElementType tokenElementType = tokenVerifier.getValue();

            Integer tokenOffset = tokenSearchFunction.apply(fileBody);
            assertNotNull(tokenOffset);
            assertSemanticTokenElement(file, tokenElementType, tokenOffset);
        }

        // Verify all tokens including those that weren't explicitly specified via verifiers
        int offset = 0;
        while (offset < fileBody.length()) {
            PsiElement element = file.findElementAt(offset);
            if (element instanceof LSPSemanticTokenPsiElement semanticTokenElement) {
                assertSemanticTokenElement(file, semanticTokenElement);
                offset += element.getTextLength() + 1;
            } else {
                offset++;
            }
        }
    }

    protected void assertViewProviderDisabled(@NotNull String fileName,
                                              @NotNull String fileBody,
                                              @Nullable String mockSemanticTokensProviderJson,
                                              @Nullable String mockSemanticTokensJson) {
        PsiFile file = initialize(fileName, fileBody, mockSemanticTokensProviderJson, mockSemanticTokensJson, false);

        // Verify that the file does not include any semantic token-based elements or references
        for (int offset = 0; offset < fileBody.length(); offset++) {
            PsiElement element = file.findElementAt(offset);
            assertFalse(element instanceof LSPSemanticTokenPsiElement);
            assertNull(file.findReferenceAt(offset));
        }
    }

    private static void assertSemanticTokenElement(@NotNull PsiFile file,
                                                   @NotNull IElementType expectedTokenElementType,
                                                   int offset) {
        PsiElement element = file.findElementAt(offset);
        assertInstanceOf(element, LSPSemanticTokenPsiElement.class);
        assertSemanticTokenElement(file, (LSPSemanticTokenPsiElement) element, expectedTokenElementType);
    }

    private static void assertSemanticTokenElement(@NotNull PsiFile file,
                                                   @NotNull LSPSemanticTokenPsiElement element) {
        assertSemanticTokenElement(file, element, null);
    }

    private static void assertSemanticTokenElement(@NotNull PsiFile file,
                                                   @NotNull LSPSemanticTokenPsiElement semanticTokenElement,
                                                   @Nullable IElementType expectedTokenElementType) {
        // Verify the element type if appropriate
        ASTNode semanticTokenNode = semanticTokenElement.getNode();
        assertNotNull(semanticTokenNode);
        IElementType tokenElementType = semanticTokenNode.getElementType();
        assertInstanceOf(tokenElementType, IElementType.class);
        if (expectedTokenElementType != null) {
            assertEquals(expectedTokenElementType, tokenElementType);
        }

        // Verify references are only present for reference elements
        PsiReference reference = file.findReferenceAt(semanticTokenElement.getTextOffset());
        if (tokenElementType == LSPSemanticTokenElementType.REFERENCE) {
            assertInstanceOf(reference, LSPSemanticTokenPsiReference.class);
        } else {
            assertNull(reference);
        }
    }
}
