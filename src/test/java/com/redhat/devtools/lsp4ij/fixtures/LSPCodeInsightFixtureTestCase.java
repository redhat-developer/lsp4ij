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

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;

import java.util.List;

/**
 * Base class test case to test LSP feature.
 */
public abstract class LSPCodeInsightFixtureTestCase extends UsefulTestCase {

    private final String[] fileNamePatterns;
    private String languageId;
    private boolean clientConfigurable = false;

    protected LSPCodeInsightTestFixture myFixture;
    private MockLanguageServerDefinition serverDefinition;

    public LSPCodeInsightFixtureTestCase(String... fileNamePatterns) {
        this.fileNamePatterns = fileNamePatterns;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        registerServer();
    }

    @Override
    protected void tearDown() throws Exception {
        MockLanguageServer.INSTANCE.waitBeforeTearDown();
        unregisterServer();
        try {
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }
    }

    public void setLanguageId(String languageId) {
        this.languageId = languageId;
    }

    public void setClientConfigurable(boolean clientConfigurable) {
        this.clientConfigurable = clientConfigurable;
    }

    private void registerServer() {
        serverDefinition = new MockLanguageServerDefinition(clientConfigurable);
        List<ServerMappingSettings> mappings = List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of(fileNamePatterns), languageId));
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition, mappings);
    }

    private void unregisterServer() {
        LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), serverDefinition);
    }
}
