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

package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * These verify that editor improvements are disabled by default in non-user-defined language servers.
 */
abstract class AbstractTypeScriptEditorImprovementsTest extends LSPCodeInsightFixtureTestCase {

    protected static final String TEST_FILE_NAME = "test.ts";
    protected static final char BACKSLASH = '\\';
    protected static final String CARET = "<caret>";

    protected AbstractTypeScriptEditorImprovementsTest() {
        super("*.ts");
    }

    @NotNull
    protected final LanguageServerItem initializeLanguageServer() {
        List<LanguageServerItem> languageServers = new LinkedList<>();
        try {
            Project project = myFixture.getProject();
            PsiFile file = myFixture.getFile();
            ContainerUtil.addAllNotNull(languageServers, LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file.getVirtualFile(), null, null)
                    .get(5000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            fail(e.getMessage());
        }
        LanguageServerItem languageServer = ContainerUtil.getFirstItem(languageServers);
        assertNotNull(languageServer);
        return languageServer;
    }

    @NotNull
    protected final ClientConfigurationSettings getClientConfigurationSettings(@NotNull LanguageServerItem languageServer) {
        LanguageServerDefinition languageServerDefinition = languageServer.getServerDefinition();
        assertInstanceOf(languageServerDefinition, ClientConfigurableLanguageServerDefinition.class);
        ClientConfigurableLanguageServerDefinition configurableLanguageServerDefinition = (ClientConfigurableLanguageServerDefinition) languageServerDefinition;
        ClientConfigurationSettings clientConfigurationSettings = configurableLanguageServerDefinition.getLanguageServerClientConfiguration();
        assertNotNull(clientConfigurationSettings);
        return clientConfigurationSettings;
    }
}
