/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerTreeRenderer;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.jsonrpc.services.GenericEndpoint;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tests what the Language Servers view shows when it opens.
 */
public class LanguageServerExplorerLoadTest extends LSPCodeInsightFixtureTestCase {

    // The consumers of a message that LSP4IJ sends and of one that it receives, which tell the traces which it is
    private static final MessageConsumer SENT = new StreamMessageConsumer(new ByteArrayOutputStream(), new MessageJsonHandler(Map.of()));
    private static final MessageConsumer RECEIVED = new RemoteEndpoint(message -> {}, new GenericEndpoint(new Object()));

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

    public void testTheViewShowsTheTracesOfALanguageServerFromBeforeItOpened() {
        traceWith(ServerTrace.messages);
        var languageServer = LanguageServiceAccessor.getInstance(project).forceCreateServer(definition);
        languageServer.updateStatus(ServerStatus.started);
        // A request and its response, before the view exists, as at the start of the IDE (#19)
        log(request("1", "test/requestBeforeTheView"), SENT, languageServer);
        log(response("1"), RECEIVED, languageServer);

        var panel = new LSPConsoleToolWindowPanel(project);
        try {
            String traces = waitForTraces(panel, "Received response");
            assertTrue(traces, traces.contains("Sending request 'test/requestBeforeTheView - (1)'."));
            assertTrue(traces, traces.contains("Received response 'test/requestBeforeTheView - (1)'"));
        } finally {
            Disposer.dispose(panel);
        }
    }

    public void testOnlyTheLatestTracesWaitForTheView() {
        traceWith(ServerTrace.messages);
        var languageServer = LanguageServiceAccessor.getInstance(project).forceCreateServer(definition);
        languageServer.updateStatus(ServerStatus.started);
        log(request("7", "test/droppedRequest"), SENT, languageServer);
        for (int i = 0; i < LanguageServerWrapper.MAX_PENDING_TRACES; i++) {
            var notification = new NotificationMessage();
            notification.setMethod("test/notification");
            log(notification, RECEIVED, languageServer);
        }
        log(response("7"), RECEIVED, languageServer);

        // The request went, as the oldest trace
        assertEquals(LanguageServerWrapper.MAX_PENDING_TRACES, languageServer.getTraces().size());
        var panel = new LSPConsoleToolWindowPanel(project);
        try {
            String traces = waitForTraces(panel, "Received response");
            assertFalse(traces.contains("Sending request 'test/droppedRequest"));
            // Its response still shows its method
            assertTrue(traces, traces.contains("Received response 'test/droppedRequest - (7)'"));
        } finally {
            Disposer.dispose(panel);
        }
    }

    private void traceWith(@NotNull ServerTrace serverTrace) {
        var settings = ProjectLanguageServerSettings.getInstance(project);
        settings.updateSettings(definition.getId(), new LanguageServerSettings.LanguageServerDefinitionSettings().setServerTrace(serverTrace));
        Disposer.register(getTestRootDisposable(), () -> settings.updateSettings(definition.getId(),
                new LanguageServerSettings.LanguageServerDefinitionSettings().setServerTrace(ServerTrace.off)));
    }

    private void log(@NotNull Message message, @NotNull MessageConsumer consumer, @NotNull LanguageServerWrapper languageServer) {
        LanguageServerLifecycleManager.getInstance(project).logLSPMessage(message, consumer, languageServer);
    }

    private static @NotNull RequestMessage request(@NotNull String id, @NotNull String method) {
        var request = new RequestMessage();
        request.setId(id);
        request.setMethod(method);
        return request;
    }

    private static @NotNull ResponseMessage response(@NotNull String id) {
        var response = new ResponseMessage();
        response.setId(id);
        response.setResult("done");
        return response;
    }

    /**
     * Waits until the traces of the language server in the given view contain the given text, and returns them.
     */
    private @NotNull String waitForTraces(@NotNull LSPConsoleToolWindowPanel panel, @NotNull String text) {
        var explorer = panel.getExplorer();
        String[] traces = {""};
        PlatformTestUtil.waitWithEventsDispatching("The view must show the traces of the language server", () -> {
            var node = explorer.findNodeForServer(definition);
            var processNode = node != null ? node.getActiveProcessTreeNode() : null;
            var view = processNode != null ? panel.getTracesConsoleView(processNode) : null;
            if (view == null) {
                return false;
            }
            ((ConsoleViewImpl) view).flushDeferredText();
            traces[0] = ((ConsoleViewImpl) view).getText();
            return traces[0].contains(text);
        }, 10);
        return traces[0];
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
            String tooltip = renderer.getToolTipText();
            assertNotNull(tooltip);
            assertTrue(tooltip.contains("mock-ls 1.2.3"));
            assertTrue(tooltip.contains("<b>Server:</b> mock-ls 1.2.3"));
            assertTrue(tooltip.contains("<b>Language Server:</b>"));

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
