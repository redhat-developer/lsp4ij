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
package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.google.gson.Gson;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    protected void assertViewProvider(@NotNull String fileName,
                                      @NotNull String fileBody,
                                      @NotNull String mockSemanticTokensProviderJson,
                                      @NotNull String mockSemanticTokensJson,
                                      @NotNull Map.Entry<Function<String, Integer>, LSPSemanticTokenElementType>... tokenVerifiers) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);
        Gson gson = JSONUtils.getLsp4jGson();

        ServerCapabilities serverCapabilities = MockLanguageServer.defaultServerCapabilities();
        SemanticTokensWithRegistrationOptions mockSemanticTokensProvider = gson.fromJson(mockSemanticTokensProviderJson, SemanticTokensWithRegistrationOptions.class);
        serverCapabilities.setSemanticTokensProvider(mockSemanticTokensProvider);
        MockLanguageServer.reset(() -> serverCapabilities);

        SemanticTokens mockSemanticTokens = gson.fromJson(mockSemanticTokensJson, SemanticTokens.class);
        MockLanguageServer.INSTANCE.setSemanticTokens(mockSemanticTokens);

        PsiFile file = myFixture.configureByText(fileName, fileBody);

        // Initialize the language server
        try {
            LanguageServiceAccessor.getInstance(file.getProject())
                    .getLanguageServers(file.getVirtualFile(), null, null)
                    .get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        // Force a highlighting pass to populate the view provider's semantic tokens
        myFixture.doHighlighting();

        // Confirm the token element types
        for (Map.Entry<Function<String, Integer>, LSPSemanticTokenElementType> tokenVerifier : tokenVerifiers) {
            Function<String, Integer> tokenSearchFunction = tokenVerifier.getKey();
            LSPSemanticTokenElementType tokenElementType = tokenVerifier.getValue();

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

    private static void assertSemanticTokenElement(@NotNull PsiFile file,
                                                   @NotNull LSPSemanticTokenElementType expectedTokenElementType,
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
                                                   @Nullable LSPSemanticTokenElementType expectedTokenElementType) {
        // Verify the element type if appropriate
        ASTNode semanticTokenNode = semanticTokenElement.getNode();
        assertNotNull(semanticTokenNode);
        IElementType tokenElementType = semanticTokenNode.getElementType();
        assertInstanceOf(tokenElementType, LSPSemanticTokenElementType.class);
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
