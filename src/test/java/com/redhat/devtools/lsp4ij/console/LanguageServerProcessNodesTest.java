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
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerExplorer;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerProcessTreeNode;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerTreeNode;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
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
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.redhat.devtools.lsp4ij.console.LSPConsoleToolWindowPanel.LANGUAGE_SERVERS_TOOL_WINDOW_ID;

/**
 * Tests the process nodes of the Language Servers view when one definition has several language server instances. A
 * process node shows the language server of its definition and root: one with the same root takes it over.
 */
public class LanguageServerProcessNodesTest extends LSPCodeInsightFixtureTestCase {

    // Only ends a hang: a wait whose condition is met returns at once
    private static final int TIMEOUT = 60;

    private static final String ID = "process-nodes-server";

    private Project project;
    private InstancesDefinition definition;
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
        definition = new InstancesDefinition();
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.process-nodes"), "plaintext")));
        panel = new LSPConsoleToolWindowPanel(project);
        explorer = panel.getExplorer();
        PlatformTestUtil.waitWithEventsDispatching("The view must show the language server",
                () -> explorer.findNodeForServer(definition) != null, TIMEOUT);
        // The view loads the started language servers once the node exists, which must not move a selection later
        waitForTheConsolePanels();
        // The static methods of the panel find it through the tool window
        var toolWindow = ToolWindowManager.getInstance(project)
                .registerToolWindow(RegisterToolWindowTask.notClosable(LANGUAGE_SERVERS_TOOL_WINDOW_ID));
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(panel, "", false));
        first = instance("a");
        second = instance("b");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ToolWindowManager.getInstance(project).unregisterToolWindow(LANGUAGE_SERVERS_TOOL_WINDOW_ID);
            Disposer.dispose(panel);
            definition.setEnabled(true, project);
            for (var server : definition.servers) {
                server.waitBeforeTearDown();
            }
            LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
        } finally {
            super.tearDown();
        }
    }

    public void testEachLanguageServerInstanceHasItsOwnProcessNode() {
        startBoth();

        assertEquals(2, serverNode().getChildCount());
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

        assertEquals(2, serverNode().getChildCount());
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
        var withoutRoot = new LanguageServerWrapper(project, definition);
        Disposer.register(getTestRootDisposable(), withoutRoot);

        first.updateStatus(ServerStatus.starting);
        withoutRoot.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals("a: starting", processNode(first).getDisplayName());
        assertEquals("starting", processNode(withoutRoot).getDisplayName());
    }

    public void testAProcessNodeShowsThePathOfItsRoot() throws Exception {
        // Two roots with the same name
        var a = instanceIn(myFixture.getTempDirFixture().findOrCreateDir("a/src"));
        var b = instanceIn(myFixture.getTempDirFixture().findOrCreateDir("b/src"));

        a.updateStatus(ServerStatus.starting);
        b.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        var labelOfA = processNode(a).getDisplayName();
        var labelOfB = processNode(b).getDisplayName();
        // A path of the system, relative to the project's directory or in full
        assertTrue(labelOfA, labelOfA.endsWith("a" + File.separator + "src: starting"));
        assertTrue(labelOfB, labelOfB.endsWith("b" + File.separator + "src: starting"));
    }

    public void testTheProcessNodesAreSortedByTheirRoot() {
        second.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        first.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertSame(processNode(first), serverNode().getChildAt(0));
        assertSame(processNode(second), serverNode().getChildAt(1));
    }

    public void testTheProcessNodeOfADisposedLanguageServerStaysWithItsLog() {
        startBoth();
        waitForTheConsolePanels();
        ServerMessageHandler.logMessage(second, new MessageParams(MessageType.Info, "Log of the second"));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        var node = processNode(second);

        Disposer.dispose(second);
        waitForTheConsolePanels();

        assertEquals(2, serverNode().getChildCount());
        assertSame(node, processNode(second));
        assertEquals(ServerStatus.stopped, node.getServerStatus());
        assertTrue(panel.hasConsolePanel(node));
        assertEquals("Log of the second\n", logOf(second));
    }

    public void testALanguageServerDisposedWhileItStopsShowsThatItStopped() {
        startBoth();
        second.updateStatus(ServerStatus.stopping);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        // It reports no status for that: it was stopping already
        Disposer.dispose(second);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(ServerStatus.stopped, processNode(second).getServerStatus());
    }

    public void testALanguageServerWithTheRootOfADisposedOneTakesItsProcessNodeOver() {
        startBoth();
        waitForTheConsolePanels();
        ServerMessageHandler.logMessage(second, new MessageParams(MessageType.Info, "Log of the second"));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        var node = processNode(second);
        Disposer.dispose(second);
        waitForTheConsolePanels();

        var replacement = instance("b");
        replacement.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(2, serverNode().getChildCount());
        assertSame(node, processNode(replacement));
        assertSame(replacement, node.getLanguageServer());
        assertEquals(ServerStatus.starting, node.getServerStatus());
        assertEquals("Log of the second\n", logOf(replacement));
    }

    public void testALateStatusOfADisposedLanguageServerLeavesItsReplacementInTheNode() {
        startBoth();
        Disposer.dispose(second);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        var replacement = instance("b");
        replacement.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        second.updateStatus(ServerStatus.started);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        var node = processNode(replacement);
        assertSame(replacement, node.getLanguageServer());
        assertEquals(ServerStatus.starting, node.getServerStatus());
    }

    public void testAStatusStillOnItsWayDoesNotOutlastTheDisposalOfItsLanguageServer() throws Exception {
        // From a background thread, the status reaches the tree later, on the UI thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> second.updateStatus(ServerStatus.starting)).get();

        Disposer.dispose(second);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(ServerStatus.stopped, processNode(second).getServerStatus());
    }

    public void testADisposedLanguageServerWithoutAProcessNodeGetsNone() {
        Disposer.dispose(second);

        second.updateStatus(ServerStatus.starting);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertNull(serverNode().findProcessTreeNode(second));
    }

    public void testARemovedDefinitionGetsNoConsolePanelAgain() {
        startBoth();
        waitForTheConsolePanels();
        var serverNode = serverNode();
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

        panel.showTrace(node, "A late trace");
        assertFalse(panel.hasConsolePanel(node));
        panel.showError(node, new IllegalStateException("A late error"));
        assertFalse(panel.hasConsolePanel(node));
    }

    public void testBothConsolesOfAProcessNodeGoWithItsDefinition() {
        startBoth();
        waitForTheConsolePanels();
        var traces = (ConsoleViewImpl) panel.getTracesConsoleView(processNode(second));
        var logs = (ConsoleViewImpl) panel.getLogsConsoleView(processNode(second));
        assertNotNull(traces.getEditor());
        assertNotNull(logs.getEditor());

        Disposer.dispose(first);
        Disposer.dispose(second);
        LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
        waitForTheConsolePanels();

        assertNull(traces.getEditor());
        assertNull(logs.getEditor());
    }

    public void testTheNodeOfAnInstallationWithoutAnInstanceStaysWithItsLog() {
        // An installation without an instance gets a language server without a root, which is never started
        var installation = new LanguageServerWrapper(project, definition);
        Disposer.register(getTestRootDisposable(), installation);
        installation.updateStatus(ServerStatus.installing);
        waitForTheConsolePanels();
        var node = processNode(installation);
        panel.showTrace(node, "Output of the installer\n");
        installation.updateStatus(ServerStatus.installed);

        Disposer.dispose(installation);
        waitForTheConsolePanels();

        assertSame(node, serverNode().findProcessTreeNode((WorkspaceFolder) null));
        assertTrue(textOf(panel.getTracesConsoleView(node)).contains("Output of the installer"));

        // The next installation takes the node over
        var next = new LanguageServerWrapper(project, definition);
        Disposer.register(getTestRootDisposable(), next);
        next.updateStatus(ServerStatus.installing);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertSame(next, node.getLanguageServer());
        assertEquals(1, serverNode().getChildCount());
    }

    public void testStoppingOneInstanceLeavesTheOthersOn() {
        startBoth();

        first.stopAndDisable();

        PlatformTestUtil.waitWithEventsDispatching("The stopped instance must show that it is off",
                () -> "a: disabled".equals(processNode(first).getDisplayName()), TIMEOUT);
        assertEquals("b: starting", processNode(second).getDisplayName());
        assertNotSame(AllIcons.Actions.Cancel, processNode(second).getIcon());
    }

    public void testASwitchedOffDefinitionShowsOnAllItsInstances() {
        startBoth();

        definition.setEnabled(false, project);
        // A status of any instance shows it on all of them
        first.updateStatus(ServerStatus.stopped);

        PlatformTestUtil.waitWithEventsDispatching("The other instance must show that its definition is off",
                () -> "b: disabled".equals(processNode(second).getDisplayName()), TIMEOUT);
        assertEquals(AllIcons.Actions.Cancel, processNode(second).getIcon());
    }

    public void testTheViewShowsTheLanguageServersStartedBeforeItOpened() {
        var manager = LanguageServerManager.getInstance(project);
        var options = new LanguageServerManager.StartOptions().setForceStart(true);
        manager.start(ID, root("a"), options);
        manager.start(ID, root("b"), options);
        PlatformTestUtil.waitWithEventsDispatching("Both instances must start",
                () -> manager.getServerStatus(ID, root("a")) == ServerStatus.started
                        && manager.getServerStatus(ID, root("b")) == ServerStatus.started, TIMEOUT);
        manager.stop(ID, root("b"), new LanguageServerManager.StopOptions().setWillDisable(false));
        PlatformTestUtil.waitWithEventsDispatching("The instance b must stop",
                () -> manager.getServerStatus(ID, root("b")) == ServerStatus.stopped, TIMEOUT);

        var laterPanel = new LSPConsoleToolWindowPanel(project);
        try {
            var laterExplorer = laterPanel.getExplorer();
            PlatformTestUtil.waitWithEventsDispatching("The view must show both instances", () -> {
                var node = laterExplorer.findNodeForServer(definition);
                return node != null && node.getChildCount() == 2;
            }, TIMEOUT);
            var node = laterExplorer.findNodeForServer(definition);
            assertEquals(ServerStatus.started, node.findProcessTreeNode(root("a")).getServerStatus());
            assertEquals(ServerStatus.stopped, node.findProcessTreeNode(root("b")).getServerStatus());
        } finally {
            Disposer.dispose(laterPanel);
        }
    }

    public void testAnInstanceThatReplacesAnotherWithItsRootKeepsItsTracesAndLogs() throws Exception {
        var instance = instanceOf(file("a/one.process-nodes"));
        PlatformTestUtil.waitWithEventsDispatching("The view must show the instance",
                () -> processNodeOrNull(instance) != null && processNode(instance).getServerStatus() == ServerStatus.started, TIMEOUT);
        waitForTheConsolePanels();
        var node = processNode(instance);
        ServerMessageHandler.logMessage(instance, new MessageParams(MessageType.Info, "Log of the replaced instance"));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        fireStrategyChanged();
        var replacement = instanceOf(file("a/two.process-nodes"));
        PlatformTestUtil.waitWithEventsDispatching("The replaced instance must be disposed", instance::isDisposed, TIMEOUT);
        PlatformTestUtil.waitWithEventsDispatching("The replacement must take the node over",
                () -> node.getLanguageServer() == replacement && node.getServerStatus() == ServerStatus.started, TIMEOUT);

        assertNotSame(instance, replacement);
        assertEquals(1, serverNode().getChildCount());
        assertTrue(logOf(replacement).contains("Log of the replaced instance"));
    }

    public void testALanguageServerWithTheRootOfARunningInstanceLeavesItsNode() throws Exception {
        var instance = instanceOf(file("a/one.process-nodes"));
        PlatformTestUtil.waitWithEventsDispatching("The view must show the instance",
                () -> processNodeOrNull(instance) != null && processNode(instance).getServerStatus() == ServerStatus.started, TIMEOUT);

        // As an instance that was replaced but not disposed yet, whose status comes late
        var replaced = LanguageServerWrapper.forInstanceRoot(project, definition, instance.getInstanceRoot());
        Disposer.register(getTestRootDisposable(), replaced);
        replaced.updateStatus(ServerStatus.stopping);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        var node = processNode(instance);
        assertSame(instance, node.getLanguageServer());
        assertEquals(ServerStatus.started, node.getServerStatus());
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
                    () -> textOf(panel.getTracesConsoleView(processNode(second))).contains("test/traceOfTheSecond"), TIMEOUT);
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
                    () -> logOf(second).contains("Log of the second"), TIMEOUT);
            assertEquals("", logOf(first));
        } finally {
            client.dispose();
        }
    }

    public void testTheLogTabOfAnInstanceSelectsItsProcessNode() {
        startBoth();

        LSPConsoleToolWindowPanel.selectLogTab(definition, first.getInstanceRoot(), project);
        assertSame(processNode(first), selectedNode());

        LSPConsoleToolWindowPanel.selectLogTab(definition, second.getInstanceRoot(), project);
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

    public void testShowLogsSelectsTheProcessNodeOfTheInstanceThatReplacedItsLanguageServer() {
        startBoth();
        var notification = CancellationSupport.showNotificationError(second, "Test", new Exception("Error of the second"));
        try {
            var action = ContainerUtil.find(notification.getActions(), a -> a instanceof ShowErrorLogAction);
            assertNotNull(action);
            Disposer.dispose(second);
            var replacement = instance("b");
            replacement.updateStatus(ServerStatus.starting);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            explorer.selectAndExpand(processNode(first));

            action.actionPerformed(TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT));

            assertSame(processNode(replacement), selectedNode());
        } finally {
            notification.expire();
        }
    }

    public void testTheErrorNotificationOfAnInstanceNamesIt() {
        var notification = CancellationSupport.showNotificationError(second, "Test", new Exception("Error of the second"));
        try {
            assertEquals(definition.getDisplayName() + " (b)", notification.getTitle());
        } finally {
            notification.expire();
        }
    }

    public void testADisposedLanguageServerExpiresItsCannotStartNotification() {
        List<Notification> notifications = new CopyOnWriteArrayList<>();
        project.getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                notifications.add(notification);
            }
        });
        definition.cannotStart = true;
        var failing = instance("c");
        failing.restart();
        PlatformTestUtil.waitWithEventsDispatching("The start must fail with a notification",
                () -> ContainerUtil.exists(notifications, n -> "Cannot start server".equals(n.getSubtitle())), TIMEOUT);
        var notification = ContainerUtil.find(notifications, n -> "Cannot start server".equals(n.getSubtitle()));
        assertFalse(notification.isExpired());

        Disposer.dispose(failing);

        assertTrue(notification.isExpired());
    }

    public void testACannotStartNotificationShownWhileItsLanguageServerIsDisposedExpires() {
        List<Notification> notifications = new CopyOnWriteArrayList<>();
        definition.cannotStart = true;
        var failing = instance("c");
        project.getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                if ("Cannot start server".equals(notification.getSubtitle())) {
                    notifications.add(notification);
                    // As the notification is shown, before its language server has it
                    Disposer.dispose(failing);
                }
            }
        });

        failing.restart();

        PlatformTestUtil.waitWithEventsDispatching("The start must fail with a notification",
                () -> !notifications.isEmpty() && failing.isDisposed(), TIMEOUT);
        PlatformTestUtil.waitWithEventsDispatching("The notification must expire",
                () -> notifications.get(0).isExpired(), TIMEOUT);
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

    private void fireStrategyChanged() {
        LanguageServersRegistry.getInstance().handleChangeEvent(new LanguageServerDefinitionListener.LanguageServerChangedEvent(
                LanguageServerDefinitionListener.LanguageServerDefinitionEvent.UpdatedBy.USER, project, definition,
                false, false, false, false, false, false, false, false, true));
    }

    private @NotNull LanguageServerWrapper instance(@NotNull String name) {
        var instance = LanguageServerWrapper.forInstanceRoot(project, definition, root(name));
        Disposer.register(getTestRootDisposable(), instance);
        return instance;
    }

    private @NotNull LanguageServerWrapper instanceIn(@NotNull VirtualFile directory) {
        var instance = LanguageServerWrapper.forInstanceRoot(project, definition,
                new WorkspaceFolder(LSPIJUtils.toUriAsString(directory), directory.getName()));
        Disposer.register(getTestRootDisposable(), instance);
        return instance;
    }

    private static @NotNull WorkspaceFolder root(@NotNull String name) {
        return new WorkspaceFolder("file:///" + name, name);
    }

    private @NotNull PsiFile file(@NotNull String path) {
        return myFixture.addFileToProject(path, "text");
    }

    private @NotNull LanguageServerWrapper instanceOf(@NotNull PsiFile file) throws Exception {
        CompletableFuture<List<LanguageServerItem>> servers = LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, null, null);
        PlatformTestUtil.waitWithEventsDispatching("The language servers must answer", servers::isDone, TIMEOUT);
        assertEquals(1, servers.get().size());
        return servers.get().get(0).getServerWrapper();
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

    private @NotNull LanguageServerTreeNode serverNode() {
        var serverNode = explorer.findNodeForServer(definition);
        assertNotNull(serverNode);
        return serverNode;
    }

    private @Nullable LanguageServerProcessTreeNode processNodeOrNull(@NotNull LanguageServerWrapper languageServer) {
        var serverNode = explorer.findNodeForServer(definition);
        return serverNode != null ? serverNode.findProcessTreeNode(languageServer) : null;
    }

    private @NotNull LanguageServerProcessTreeNode processNode(@NotNull LanguageServerWrapper languageServer) {
        var node = processNodeOrNull(languageServer);
        assertNotNull(node);
        return node;
    }

    /**
     * The folder of a file named a, b or c is the root of its instance.
     */
    private static final class FolderStrategy implements WorkspaceFolderStrategy {

        @Override
        public boolean hasServerInstances() {
            return true;
        }

        @Override
        public boolean sendAllFoldersOnInitialization() {
            return true;
        }

        @Override
        public @NotNull List<WorkspaceFolder> getWorkspaceFolders(@NotNull Project project,
                                                                  @NotNull FileUriSupport fileUriSupport) {
            return new ArrayList<>();
        }

        @Override
        public @Nullable WorkspaceFolder getWorkspaceFolderForFile(@NotNull VirtualFile file,
                                                                   @NotNull Project project,
                                                                   @NotNull FileUriSupport fileUriSupport) {
            var parent = file.isDirectory() ? file : file.getParent();
            if (parent == null || !List.of("a", "b", "c").contains(parent.getName())) {
                return null;
            }
            return new WorkspaceFolder(FileUriSupport.toString(parent, fileUriSupport), parent.getName());
        }
    }

    private static final class InstancesDefinition extends MockLanguageServerDefinition {

        final List<MockLanguageServer> servers = new CopyOnWriteArrayList<>();
        volatile boolean cannotStart;

        InstancesDefinition() {
            super(ID, false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            var server = new MockLanguageServer(MockLanguageServer::defaultServerCapabilities);
            servers.add(server);
            if (cannotStart) {
                return new MockConnectionProvider(server) {
                    @Override
                    public void start() throws CannotStartProcessException {
                        throw new CannotStartProcessException("Cannot run program \"missing\"");
                    }
                };
            }
            return new MockConnectionProvider(server);
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            return super.createClientFeatures().setWorkspaceFolderFeature(new LSPWorkspaceFolderFeature() {
                @Override
                protected @NotNull WorkspaceFolderStrategy createStrategy() {
                    return new FolderStrategy();
                }
            });
        }
    }
}
