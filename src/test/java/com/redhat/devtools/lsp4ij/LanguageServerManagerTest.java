/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;

import java.util.List;

/**
 * Test for {@link LanguageServerManager}.
 */
public class LanguageServerManagerTest extends UsefulTestCase {

    private LSPCodeInsightTestFixture myFixture;
    private MockLanguageServerDefinition serverDefinition;

    public void testStartServer() {

        var manager = LanguageServerManager.getInstance(myFixture.getProject());
        asserServerStatus(ServerStatus.none, manager, serverDefinition);

        // Try to start server (without forcing)
        var startOptions = new LanguageServerManager.StartOptions();
        manager.start(serverDefinition.getId(), startOptions);
        asserServerStatus(ServerStatus.none, manager, serverDefinition);

        // Try to start server (with forcing)
        var forceStartOptions = new LanguageServerManager.StartOptions();
        forceStartOptions.setForceStart(true);
        manager.start(serverDefinition.getId(), forceStartOptions);
        asserServerStatus(ServerStatus.started, manager, serverDefinition);
    }

    public void testStopServer() {

        var manager = LanguageServerManager.getInstance(myFixture.getProject());
        asserServerStatus(ServerStatus.none, manager, serverDefinition);

        // Try to start server (with forcing)
        var forceStartOptions = new LanguageServerManager.StartOptions();
        forceStartOptions.setForceStart(true);
        manager.start(serverDefinition.getId(), forceStartOptions);
        asserServerStatus(ServerStatus.started, manager, serverDefinition);

        // Try to stop server
        var stopOptions = new LanguageServerManager.StopOptions();
        manager.stop(serverDefinition.getId(), stopOptions);
        asserServerStatus(ServerStatus.stopped, manager, serverDefinition);
        assertFalse(serverDefinition.isEnabled(myFixture.getProject()));

        // Try to restart server (with forcing)
        forceStartOptions = new LanguageServerManager.StartOptions();
        forceStartOptions.setForceStart(true);
        manager.start(serverDefinition.getId(), forceStartOptions);
        asserServerStatus(ServerStatus.started, manager, serverDefinition);
        assertTrue(serverDefinition.isEnabled(myFixture.getProject()));
    }

    private static void asserServerStatus(ServerStatus actual, LanguageServerManager manager, MockLanguageServerDefinition serverDefinition) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        var expected = manager.getServerStatus(serverDefinition.getId());
        assertEquals("Check of server status", expected, actual);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        serverDefinition = new MockLanguageServerDefinition("test-server-status");
        List<ServerMappingSettings> mappings = List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null));
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition, mappings);
    }

    @Override
    protected void tearDown() throws Exception {
        MockLanguageServer.INSTANCE.waitBeforeTearDown();
        LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), serverDefinition);
        try {
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }

    }

}
