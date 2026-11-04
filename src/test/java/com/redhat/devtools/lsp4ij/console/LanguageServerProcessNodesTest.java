/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerExplorer;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerProcessTreeNode;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Tests the process nodes of the Language Servers view when one definition has several language servers.
 */
public class LanguageServerProcessNodesTest extends LSPCodeInsightFixtureTestCase {

    private Project project;
    private MockLanguageServerDefinition definition;
    private LSPConsoleToolWindowPanel panel;
    private LanguageServerExplorer explorer;
    private LanguageServerWrapper first;
    private LanguageServerWrapper second;

    public LanguageServerProcessNodesTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = myFixture.getProject();
        definition = new MockLanguageServerDefinition("process-nodes-server", false);
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.process-nodes"), "plaintext")));
        panel = new LSPConsoleToolWindowPanel(project);
        explorer = panel.getExplorer();
        PlatformTestUtil.waitWithEventsDispatching("The view must show the language server",
                () -> explorer.findNodeForServer(definition) != null, 10);
        var accessor = LanguageServiceAccessor.getInstance(project);
        first = accessor.forceCreateServer(definition);
        second = accessor.forceCreateServer(definition);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Disposer.dispose(panel);
            LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
        } finally {
            super.tearDown();
        }
    }

    public void testEachLanguageServerHasItsOwnProcessNode() {
        startBoth();

        assertEquals(2, explorer.findNodeForServer(definition).getChildCount());
        assertSame(first, processNode(first).getLanguageServer());
        assertSame(second, processNode(second).getLanguageServer());
    }

    public void testAProcessNodeShowsTheStatusOfItsLanguageServer() {
        first.updateStatus(ServerStatus.starting);
        second.updateStatus(ServerStatus.starting);
        first.updateStatus(ServerStatus.started);
        second.updateStatus(ServerStatus.stopped);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(ServerStatus.started, processNode(first).getServerStatus());
        assertEquals(ServerStatus.stopped, processNode(second).getServerStatus());
    }

    public void testALanguageServerKeepsItsProcessNodeWhenItRestarts() {
        startBoth();
        var node = processNode(first);

        first.updateStatus(ServerStatus.stopped);
        first.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(2, explorer.findNodeForServer(definition).getChildCount());
        assertSame(node, processNode(first));
    }

    public void testTheTreeShowsTheProcessNodeOfALaterLanguageServer() {
        first.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        second.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        var tree = explorer.getTree();
        assertTrue(tree.getRowForPath(new TreePath(processNode(first).getPath())) >= 0);
        assertTrue(tree.getRowForPath(new TreePath(processNode(second).getPath())) >= 0);
    }

    public void testTwoLanguageServersThatStartAtOnceHaveOneRowEach() throws Exception {
        // Both nodes exist before the tree hears about the first one
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            first.updateStatus(ServerStatus.starting);
            second.updateStatus(ServerStatus.starting);
        }).get();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(1, rowsOf(processNode(first)));
        assertEquals(1, rowsOf(processNode(second)));
    }

    public void testTheViewShowsTheLanguageServersStartedBeforeItOpened() {
        first.updateStatus(ServerStatus.started);
        second.updateStatus(ServerStatus.stopped);

        var laterPanel = new LSPConsoleToolWindowPanel(project);
        try {
            var laterExplorer = laterPanel.getExplorer();
            PlatformTestUtil.waitWithEventsDispatching("The view must show both language servers", () -> {
                var node = laterExplorer.findNodeForServer(definition);
                return node != null && node.getChildCount() == 2;
            }, 10);
            var node = laterExplorer.findNodeForServer(definition);
            assertEquals(ServerStatus.started, node.getProcessTreeNode(first).getServerStatus());
            assertEquals(ServerStatus.stopped, node.getProcessTreeNode(second).getServerStatus());
        } finally {
            Disposer.dispose(laterPanel);
        }
    }

    public void testATraceGoesToTheProcessNodeOfItsLanguageServer() {
        startBoth();
        var settings = ProjectLanguageServerSettings.getInstance(project);
        settings.updateSettings(definition.getId(),
                new LanguageServerSettings.LanguageServerDefinitionSettings().setServerTrace(ServerTrace.verbose));
        try {
            var message = new NotificationMessage();
            message.setMethod("test/traceOfTheSecond");
            var consumer = new StreamMessageConsumer(new ByteArrayOutputStream(), new MessageJsonHandler(Map.of()));

            LanguageServerLifecycleManager.getInstance(project).logLSPMessage(message, consumer, second);

            PlatformTestUtil.waitWithEventsDispatching("The trace must reach the second language server",
                    () -> textOf(panel.getTracesConsoleView(processNode(second))).contains("test/traceOfTheSecond"), 5);
            assertEquals("", textOf(panel.getTracesConsoleView(processNode(first))));
        } finally {
            settings.updateSettings(definition.getId(),
                    new LanguageServerSettings.LanguageServerDefinitionSettings().setServerTrace(ServerTrace.off));
        }
    }

    public void testAnErrorGoesToTheProcessNodeOfItsLanguageServer() {
        startBoth();

        LanguageServerLifecycleManager.getInstance(project).onError(second, new Exception("Error of the second"));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertTrue(textOf(panel.getTracesConsoleView(processNode(second))).contains("Error of the second"));
        assertEquals("", textOf(panel.getTracesConsoleView(processNode(first))));
    }

    private void startBoth() {
        first.updateStatus(ServerStatus.starting);
        second.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private static @NotNull String textOf(ConsoleView view) {
        assertNotNull(view);
        var console = (ConsoleViewImpl) view;
        console.flushDeferredText();
        return console.getText();
    }

    private int rowsOf(@NotNull LanguageServerProcessTreeNode node) {
        var tree = explorer.getTree();
        int rows = 0;
        for (int row = 0; row < tree.getRowCount(); row++) {
            if (tree.getPathForRow(row).getLastPathComponent() == node) {
                rows++;
            }
        }
        return rows;
    }

    private @NotNull LanguageServerProcessTreeNode processNode(@NotNull LanguageServerWrapper languageServer) {
        var node = explorer.findNodeForServer(definition).getProcessTreeNode(languageServer);
        assertNotNull(node);
        return node;
    }
}
