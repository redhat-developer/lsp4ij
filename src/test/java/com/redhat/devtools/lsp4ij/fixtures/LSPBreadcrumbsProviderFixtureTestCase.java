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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.breadcrumbs.BreadcrumbsUtil;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPBreadcrumbsProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings;
import org.eclipse.lsp4j.DocumentSymbol;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Base test fixture for the document symbol-based breadcrumbs info provider.
 */
@SuppressWarnings("unused")
public abstract class LSPBreadcrumbsProviderFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    protected LSPBreadcrumbsProviderFixtureTestCase(@NotNull String... fileNamePatterns) {
        super(fileNamePatterns);
        setClientConfigurable(true);
    }

    private PsiFile initialize(@NotNull String fileName,
                               @NotNull String fileBody,
                               @NotNull String mockDocumentSymbolJson,
                               boolean enabled) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);

        List<DocumentSymbol> mockDocumentSymbols = JSONUtils.getLsp4jGson().fromJson(mockDocumentSymbolJson, new TypeToken<List<DocumentSymbol>>() {
        }.getType());
        MockLanguageServer.INSTANCE.setDocumentSymbols(mockDocumentSymbols.toArray(new DocumentSymbol[0]));

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

        // Enable or disable the breadcrumbs feature as requested
        LanguageServerDefinition languageServerDefinition = languageServer.getServerDefinition();
        assertInstanceOf(languageServerDefinition, ClientConfigurableLanguageServerDefinition.class);
        ClientConfigurableLanguageServerDefinition configurableLanguageServerDefinition = (ClientConfigurableLanguageServerDefinition) languageServerDefinition;
        ClientConfigurationSettings clientConfiguration = configurableLanguageServerDefinition.getLanguageServerClientConfiguration();
        assertNotNull(clientConfiguration);
        clientConfiguration.breadcrumbs.enabled = enabled;

        return file;
    }

    protected void assertBreadcrumbs(@NotNull String fileName,
                                     @NotNull String fileBody,
                                     @NotNull String mockDocumentSymbolJson,
                                     @NotNull Map<String, List<String>> expectedBreadcrumbNamesBySearchText) {
        PsiFile file = initialize(fileName, fileBody, mockDocumentSymbolJson, true);

        BreadcrumbsProvider breadcrumbsProvider = BreadcrumbsUtil.getInfoProvider(file.getLanguage());
        assertInstanceOf(breadcrumbsProvider, LSPBreadcrumbsProvider.class);

        // Confirm the expected breadcrumbs
        for (Map.Entry<String, List<String>> entry : expectedBreadcrumbNamesBySearchText.entrySet()) {
            String searchText = entry.getKey();
            List<String> expectedBreadcrumbNames = entry.getValue();

            // Find the offset for which we want to verify breadcrumbs
            int offset = fileBody.indexOf(searchText);
            assertTrue(offset > -1);

            // Search for the element at that offset
            PsiElement element = file.findElementAt(offset);
            assertNotNull(element);

            // Verify the element's breadcrumb ancestry
            List<PsiElement> breadcrumbAncestorElements = new LinkedList<>();
            for (PsiElement parent = breadcrumbsProvider.getParent(element);
                 (parent != null) && !(parent instanceof PsiFile);
                 parent = breadcrumbsProvider.getParent(parent)) {
                breadcrumbAncestorElements.add(parent);
            }
            // Reverse the ancestors so that we can verify them top-to-bottom
            Collections.reverse(breadcrumbAncestorElements);

            // Now verify them
            assertEquals(expectedBreadcrumbNames.size(), breadcrumbAncestorElements.size());

            for (int i = 0; i < expectedBreadcrumbNames.size(); i++) {
                String expectedBreadcrumbName = expectedBreadcrumbNames.get(i);
                PsiElement breadcrumbAncestorElement = breadcrumbAncestorElements.get(i);

                // Verify that it's an element that would be included in sticky lines
                assertTrue(breadcrumbsProvider.acceptElement(breadcrumbAncestorElement));

                // Verify parent/child relationships
                if (i < (breadcrumbAncestorElements.size() - 1)) {
                    PsiElement nextBreadcrumbAncestorElement = breadcrumbAncestorElements.get(i + 1);
                    assertSame(breadcrumbAncestorElement, breadcrumbsProvider.getParent(nextBreadcrumbAncestorElement));
                    List<PsiElement> children = breadcrumbsProvider.getChildren(breadcrumbAncestorElement);
                    assertTrue(children.contains(nextBreadcrumbAncestorElement));
                }

                // Verify that the expected name matches
                String actualBreadcrumbName = breadcrumbsProvider.getElementInfo(breadcrumbAncestorElement);
                assertEquals(expectedBreadcrumbName, actualBreadcrumbName);

                // Very that we have other presentation info
                assertNotNull(breadcrumbsProvider.getElementTooltip(element));
                assertNotNull(breadcrumbsProvider.getElementIcon(element));
            }
        }
    }

    protected void assertBreadcrumbsDisabled(@NotNull String fileName,
                                             @NotNull String fileBody,
                                             @NotNull String mockDocumentSymbolJson) {
        PsiFile file = initialize(fileName, fileBody, mockDocumentSymbolJson, false);

        // It'll still be our provider
        BreadcrumbsProvider breadcrumbsProvider = BreadcrumbsUtil.getInfoProvider(file.getLanguage());
        assertInstanceOf(breadcrumbsProvider, LSPBreadcrumbsProvider.class);

        // But it won't actually provide any information
        for (int offset = 0; offset < fileBody.length(); offset++) {
            PsiElement element = file.findElementAt(offset);
            assertNotNull(element);

            // Shouldn't be accepted as a sticky line or have any info
            assertFalse(breadcrumbsProvider.acceptElement(element));
            assertNull(breadcrumbsProvider.getParent(element));
            assertTrue(breadcrumbsProvider.getChildren(element).isEmpty());
            assertTrue(breadcrumbsProvider.getElementInfo(element).isEmpty());
            assertNull(breadcrumbsProvider.getElementIcon(element));
            assertNull(breadcrumbsProvider.getElementTooltip(element));
        }
    }
}
