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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokenPsiElement;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokensFileViewProvider;
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
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base test fixture for the semantic tokens-based file view provider.
 */
@SuppressWarnings("unused")
public abstract class LSPSemanticTokensFileViewProviderFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    // Token type verifiers
    protected static final Consumer<LSPSemanticTokenPsiElement> isKeyword = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isKeyword(offset),
            false,
            ThreeState.NO,
            ThreeState.NO
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isOperator = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isOperator(offset),
            false,
            ThreeState.NO,
            ThreeState.NO
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isStringLiteral = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isStringLiteral(offset),
            false,
            ThreeState.NO,
            ThreeState.NO
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isNumericLiteral = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isNumericLiteral(offset),
            false,
            ThreeState.NO,
            ThreeState.NO
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isRegularExpression = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isRegularExpression(offset),
            false,
            ThreeState.NO,
            ThreeState.NO
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isComment = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isComment(offset),
            false,
            ThreeState.NO,
            ThreeState.NO
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isTypeDeclaration = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isDeclaration(offset),
            true,
            ThreeState.YES,
            ThreeState.YES
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isNonTypeDeclaration = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isDeclaration(offset),
            true,
            ThreeState.YES,
            ThreeState.NO
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isTypeReference = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isReference(offset),
            false,
            ThreeState.YES,
            ThreeState.YES
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isNonTypeReference = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isReference(offset),
            false,
            ThreeState.YES,
            ThreeState.NO
    );
    protected static final Consumer<LSPSemanticTokenPsiElement> isUnknown = element -> assertSemanticToken(
            element,
            (fileViewProvider, offset) -> fileViewProvider.isUnknown(offset),
            false,
            ThreeState.UNSURE,
            ThreeState.UNSURE
    );

    private interface TokenTypeVerifier {
        boolean test(@NotNull LSPSemanticTokensFileViewProvider fileViewProvider, @NotNull Integer offset);
    }

    private static void assertSemanticToken(
            @NotNull LSPSemanticTokenPsiElement element,
            @NotNull TokenTypeVerifier tokenTypeVerifier,
            boolean isNameIdentifier,
            @NotNull ThreeState isIdentifier,
            @NotNull ThreeState isType
    ) {
        LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider = LSPSemanticTokensFileViewProvider.getInstance(element);
        assertNotNull(semanticTokensFileViewProvider);
        assertTrue(tokenTypeVerifier.test(semanticTokensFileViewProvider, element.getTextOffset()));
        int offset = element.getTextOffset();
        assertEquals(isNameIdentifier, element.getNameIdentifier() != null);
        assertEquals(isIdentifier, semanticTokensFileViewProvider.isIdentifier(offset));
        assertEquals(isType, semanticTokensFileViewProvider.isType(offset));
        assertFalse(semanticTokensFileViewProvider.isWhitespace(offset));
    }

    protected LSPSemanticTokensFileViewProviderFixtureTestCase(@NotNull String fileNamePattern,
                                                               @NotNull String languageId) {
        super(fileNamePattern);
        setLanguageId(languageId);
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
            ContainerUtil.addAllNotNull(languageServers, LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file, null, null)
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
        clientConfiguration.editor.enableSemanticTokensFileViewProvider = enabled;

        // Force a highlighting pass to populate the view provider's semantic tokens
        myFixture.doHighlighting();

        return file;
    }

    protected void assertViewProviderEnabled(@NotNull String fileName,
                                             @NotNull String fileBody,
                                             @Nullable String mockSemanticTokensProviderJson,
                                             @Nullable String mockSemanticTokensJson,
                                             @NotNull Map<Function<String, Integer>, Consumer<LSPSemanticTokenPsiElement>> tokenVerifiers) {
        PsiFile file = initialize(fileName, fileBody, mockSemanticTokensProviderJson, mockSemanticTokensJson, true);

        // Confirm the token element types
        for (Map.Entry<Function<String, Integer>, Consumer<LSPSemanticTokenPsiElement>> tokenVerifier : tokenVerifiers.entrySet()) {
            Function<String, Integer> tokenSearchFunction = tokenVerifier.getKey();
            Consumer<LSPSemanticTokenPsiElement> tokenElementVerifier = tokenVerifier.getValue();

            Integer tokenOffset = tokenSearchFunction.apply(fileBody);
            assertNotNull(tokenOffset);
            assertSemanticTokenElement(file, tokenElementVerifier, tokenOffset);
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
                                                   @NotNull Consumer<LSPSemanticTokenPsiElement> tokenElementVerifier,
                                                   int offset) {
        PsiElement element = file.findElementAt(offset);
        assertInstanceOf(element, LSPSemanticTokenPsiElement.class);
        assertSemanticTokenElement(file, (LSPSemanticTokenPsiElement) element, tokenElementVerifier);
    }

    private static void assertSemanticTokenElement(@NotNull PsiFile file,
                                                   @NotNull LSPSemanticTokenPsiElement element) {
        assertSemanticTokenElement(file, element, null);
    }

    private static void assertSemanticTokenElement(@NotNull PsiFile file,
                                                   @NotNull LSPSemanticTokenPsiElement semanticTokenElement,
                                                   @Nullable Consumer<LSPSemanticTokenPsiElement> tokenElementVerifier) {
        // Verify the element type if appropriate
        ASTNode semanticTokenNode = semanticTokenElement.getNode();
        assertNotNull(semanticTokenNode);
        IElementType tokenElementType = semanticTokenNode.getElementType();
        assertInstanceOf(tokenElementType, IElementType.class);
        if (tokenElementVerifier != null) {
            tokenElementVerifier.accept(semanticTokenElement);
        }

        // Verify references are only present for reference elements
        PsiReference reference = file.findReferenceAt(semanticTokenElement.getTextOffset());
        LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider = LSPSemanticTokensFileViewProvider.getInstance(semanticTokenElement);
        assertNotNull(semanticTokensFileViewProvider);
        if (semanticTokensFileViewProvider.isReference(semanticTokenElement.getTextOffset())) {
            assertNotNull(reference);
        } else {
            assertNull(reference);
        }
    }
}
