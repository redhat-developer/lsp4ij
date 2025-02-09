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

package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings;
import org.eclipse.lsp4j.Position;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link LSPIJUtils#toOffset(Position, Document)} when
 * {@link ClientConfigurationSettings#serverReportsOffsetOnlyPositions} is enabled.
 */
public class LSPIJUtils_toOffset_serverReportsOffsetOnlyPositionsTest extends LSPCodeInsightFixtureTestCase {

    public LSPIJUtils_toOffset_serverReportsOffsetOnlyPositionsTest() {
        super("*.txt");
        setClientConfigurable(true);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        myFixture.configureByText("test.txt", "foo\nbar\nbaz");
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

        LanguageServerDefinition languageServerDefinition = languageServer.getServerDefinition();
        assertInstanceOf(languageServerDefinition, ClientConfigurableLanguageServerDefinition.class);
        ClientConfigurableLanguageServerDefinition configurableLanguageServerDefinition = (ClientConfigurableLanguageServerDefinition) languageServerDefinition;
        ClientConfigurationSettings clientConfigurationSettings = configurableLanguageServerDefinition.getLanguageServerClientConfiguration();
        assertNotNull(clientConfigurationSettings);

        clientConfigurationSettings.serverReportsOffsetOnlyPositions = true;
    }

    public void testOffsetOnlyPositions() {
        Document document = myFixture.getDocument(myFixture.getFile());
        for (int character = 0; character < document.getTextLength(); character++) {
            int offset = LSPIJUtils.toOffset(0, character, document);
            assertEquals(character, offset);
        }
    }

    public void testInvalidOffsetOnlyPositions() {
        Document document = myFixture.getDocument(myFixture.getFile());
        assertEquals(0, LSPIJUtils.toOffset(0, -1, document));
        assertEquals(document.getTextLength(), LSPIJUtils.toOffset(0, document.getTextLength(), document));
        assertEquals(document.getTextLength(), LSPIJUtils.toOffset(0, document.getTextLength() + 1, document));
    }
}
