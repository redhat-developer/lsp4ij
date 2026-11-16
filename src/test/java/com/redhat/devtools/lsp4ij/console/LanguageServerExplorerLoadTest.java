/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.console;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerTreeRenderer;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.ServerInfo;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tests what the Language Servers view shows when it opens.
 */
public class LanguageServerExplorerLoadTest extends LSPCodeInsightFixtureTestCase {

    private Project project;
    private MockLanguageServerDefinition definition;

    public LanguageServerExplorerLoadTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = myFixture.getProject();
        definition = new MockLanguageServerDefinition("explorer-load-server", false);
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.explorer-load"), "plaintext")));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
        } finally {
            super.tearDown();
        }
    }

    public void testTheViewShowsALanguageServerStartedBeforeItOpened() {
        var languageServer = LanguageServiceAccessor.getInstance(project).forceCreateServer(definition);
        languageServer.updateStatus(ServerStatus.started);

        // The loading steps used to race, so a single opening says little
        for (int opening = 0; opening < 10; opening++) {
            var panel = new LSPConsoleToolWindowPanel(project);
            try {
                var explorer = panel.getExplorer();
                PlatformTestUtil.waitWithEventsDispatching("The view must show the language server", () -> {
                    var node = explorer.findNodeForServer(definition);
                    return node != null && node.getActiveProcessTreeNode() != null;
                }, 10);
                assertEquals(ServerStatus.started, explorer.findNodeForServer(definition).getActiveProcessTreeNode().getServerStatus());
            } finally {
                Disposer.dispose(panel);
            }
        }
    }

    public void testTheViewShowsServerInfoOnHoverAndRetainsAfterShutdown() throws Exception {
        definition.getServer().getInitializeResult().setServerInfo(new ServerInfo("mock-ls", "1.2.3"));

        var languageServer = LanguageServiceAccessor.getInstance(project).forceCreateServer(definition);
        languageServer.start();
        languageServer.getInitializedServer().get(10, TimeUnit.SECONDS);

        assertNotNull(languageServer.getServerInfo());
        assertEquals("mock-ls", languageServer.getServerInfo().getName());
        assertEquals("1.2.3", languageServer.getServerInfo().getVersion());

        var panel = new LSPConsoleToolWindowPanel(project);
        try {
            var explorer = panel.getExplorer();
            PlatformTestUtil.waitWithEventsDispatching("The view must show the language server", () -> {
                var node = explorer.findNodeForServer(definition);
                return node != null && node.getActiveProcessTreeNode() != null;
            }, 10);

            var serverNode = explorer.findNodeForServer(definition);
            assertNotNull(serverNode.getServerInfo());
            assertEquals("mock-ls", serverNode.getServerInfo().getName());
            assertEquals("1.2.3", serverNode.getServerInfo().getVersion());

            // Test renderer tooltip on server node
            var renderer = (LanguageServerTreeRenderer) explorer.getTree().getCellRenderer();
            renderer.customizeCellRenderer(explorer.getTree(), serverNode, false, false, false, 0, false);
            assertEquals("mock-ls 1.2.3", renderer.getToolTipText());

            // Stop server and verify metadata retention
            languageServer.stop().get(10, TimeUnit.SECONDS);
            assertEquals(ServerStatus.stopped, languageServer.getServerStatus());
            assertNotNull("ServerInfo must remain accessible after server shutdown", languageServer.getServerInfo());
            assertEquals("mock-ls", languageServer.getServerInfo().getName());
            assertEquals("1.2.3", languageServer.getServerInfo().getVersion());
            assertNotNull("ServerInfo must remain accessible on tree node after shutdown", serverNode.getServerInfo());
        } finally {
            Disposer.dispose(panel);
            languageServer.stop();
        }
    }
}
