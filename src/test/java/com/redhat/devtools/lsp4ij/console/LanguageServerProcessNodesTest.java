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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerExplorer;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerProcessTreeNode;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerTreeNode;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.actions.ShowErrorLogAction;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.console.LSPConsoleToolWindowPanel.LANGUAGE_SERVERS_TOOL_WINDOW_ID;

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
        // The static methods of the panel find it through the tool window
        var toolWindow = ToolWindowManager.getInstance(project)
                .registerToolWindow(RegisterToolWindowTask.notClosable(LANGUAGE_SERVERS_TOOL_WINDOW_ID));
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(panel, "", false));
        var accessor = LanguageServiceAccessor.getInstance(project);
        first = accessor.forceCreateServer(definition);
        second = accessor.forceCreateServer(definition);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ToolWindowManager.getInstance(project).unregisterToolWindow(LANGUAGE_SERVERS_TOOL_WINDOW_ID);
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

    public void testAProcessNodeShowsTheFolderOfItsInstance() {
        var instance = LanguageServerWrapper.forInstanceRoot(project, definition, new WorkspaceFolder("file:///web-tests", "web-tests"));
        Disposer.register(getTestRootDisposable(), instance);

        instance.updateStatus(ServerStatus.starting);
        first.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("web-tests: starting", processNode(instance).getDisplayName());
        assertEquals("starting", processNode(first).getDisplayName());
    }

    public void testTheProcessNodeOfADisposedLanguageServerGoes() {
        startBoth();
        waitForTheConsolePanels();
        var serverNode = explorer.findNodeForServer(definition);
        var node = processNode(second);
        assertSame(node, selectedNode());
        assertTrue(panel.hasConsolePanel(node));

        Disposer.dispose(second);
        waitForTheConsolePanels();

        assertEquals(1, serverNode.getChildCount());
        assertNull(serverNode.getProcessTreeNode(second));
        assertSame(first, processNode(first).getLanguageServer());
        assertSame(serverNode, selectedNode());
        assertFalse(panel.hasConsolePanel(node));

        panel.showTrace(node, "A late trace");
        assertFalse(panel.hasConsolePanel(node));
        panel.showError(node, new IllegalStateException("A late error"));
        assertFalse(panel.hasConsolePanel(node));

        second.updateStatus(ServerStatus.stopped);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertNull(serverNode.getProcessTreeNode(second));
    }

    public void testARemovedDefinitionGetsNoConsolePanelAgain() {
        startBoth();
        waitForTheConsolePanels();
        var serverNode = explorer.findNodeForServer(definition);
        var node = processNode(second);
        assertSame(node, selectedNode());

        // In the IDE, the accessor hears about the removal first, because the view opens later: the language servers
        // are disposed before the view removes the definition
        Disposer.dispose(first);
        Disposer.dispose(second);
        LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
        waitForTheConsolePanels();

        assertNull(explorer.findNodeForServer(definition));
        assertFalse(panel.hasConsolePanel(serverNode));
        assertFalse(panel.hasConsolePanel(node));
    }

    public void testAStatusStillOnItsWayIgnoresTheNodeOfADisposedLanguageServer() throws Exception {
        // From a background thread, the status reaches the tree later, on the UI thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> second.updateStatus(ServerStatus.starting)).get();

        Disposer.dispose(second);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertNull(explorer.findNodeForServer(definition).getProcessTreeNode(second));
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

    public void testALogGoesToTheProcessNodeOfItsLanguageServer() {
        startBoth();

        ServerMessageHandler.logMessage(second, new MessageParams(MessageType.Info, "Log of the second"));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("", logOf(first));
        assertEquals("Log of the second\n", logOf(second));
    }

    public void testALanguageClientLogsToTheProcessNodeOfItsLanguageServer() {
        startBoth();
        var client = definition.createLanguageClient(project);
        try {
            client.setServerWrapper(second);

            client.logMessage(new MessageParams(MessageType.Info, "Log of the second"));

            PlatformTestUtil.waitWithEventsDispatching("The log must reach the second language server",
                    () -> logOf(second).contains("Log of the second"), 5);
            assertEquals("", logOf(first));
        } finally {
            client.dispose();
        }
    }

    public void testTheLogTabOfALanguageServerSelectsItsProcessNode() {
        startBoth();

        LSPConsoleToolWindowPanel.selectLogTab(first);
        assertSame(processNode(first), selectedNode());

        LSPConsoleToolWindowPanel.selectLogTab(second);
        assertSame(processNode(second), selectedNode());
    }

    public void testShowLogsInAnErrorNotificationSelectsTheProcessNodeOfItsLanguageServer() {
        startBoth();
        var notification = CancellationSupport.showNotificationError(second, "Test", new Exception("Error of the second"));
        try {
            var action = ContainerUtil.find(notification.getActions(), a -> a instanceof ShowErrorLogAction);
            assertNotNull(action);

            action.actionPerformed(TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT));

            assertSame(processNode(second), selectedNode());
        } finally {
            notification.expire();
        }
    }

    public void testShowLogsForADefinitionSelectsItsFirstProcessNode() {
        startBoth();
        var action = new ShowErrorLogAction(definition, project);

        action.actionPerformed(TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT));

        assertSame(processNode(first), selectedNode());
    }

    public void testABuiltPluginStillFindsRemoveConsolePanelForAServerNode() throws Exception {
        assertNotNull(LanguageServerExplorer.class.getMethod("removeConsolePanel", LanguageServerTreeNode.class));
        assertNotNull(LSPConsoleToolWindowPanel.class.getMethod("removeConsolePanel", LanguageServerTreeNode.class));
    }

    public void testALanguageServerCanStillBeCreatedWithANullInitialPath() {
        var languageServer = new LanguageServerWrapper(project, definition, null);
        Disposer.register(getTestRootDisposable(), languageServer);

        assertNull(languageServer.getInstanceRoot());
    }

    public void testTheLogOfADefinitionGoesToItsFirstProcessNode() {
        startBoth();

        ServerMessageHandler.logMessage(definition, new MessageParams(MessageType.Info, "Log of the definition"), project);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("Log of the definition\n", logOf(first));
        assertEquals("", logOf(second));
    }

    public void testTheLogTabOfADefinitionSelectsItsFirstProcessNode() {
        startBoth();

        LSPConsoleToolWindowPanel.selectLogTab(definition, project);

        assertSame(processNode(first), selectedNode());
    }

    private static void waitForTheConsolePanels() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private void startBoth() {
        first.updateStatus(ServerStatus.starting);
        second.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private @NotNull String logOf(@NotNull LanguageServerWrapper languageServer) {
        return textOf(panel.getLogsConsoleView(processNode(languageServer)));
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

    private Object selectedNode() {
        return explorer.getTree().getSelectionPath().getLastPathComponent();
    }

    private @NotNull LanguageServerProcessTreeNode processNode(@NotNull LanguageServerWrapper languageServer) {
        var node = explorer.findNodeForServer(definition).getProcessTreeNode(languageServer);
        assertNotNull(node);
        return node;
    }
}
