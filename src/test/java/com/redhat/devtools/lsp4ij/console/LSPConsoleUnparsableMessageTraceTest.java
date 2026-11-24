/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.console;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The traces of the LSP console show a message of the language server that LSP4J can't parse, with what kept LSP4J
 * from parsing it, as a {@code textDocument/publishDiagnostics} notification with a character above the maximum
 * integer of LSP (#584).
 */
public class LSPConsoleUnparsableMessageTraceTest extends LSPCodeInsightFixtureTestCase {

    private Project project;
    private MockLanguageServerDefinition definition;

    public LSPConsoleUnparsableMessageTraceTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = myFixture.getProject();
        definition = new MockLanguageServerDefinition("unparsable-message-server", false);
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.unparsable"), "plaintext")));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
        } finally {
            super.tearDown();
        }
    }

    public void testTheTracesShowANotificationThatLSP4JCannotParse() throws Exception {
        var settings = ProjectLanguageServerSettings.getInstance(project);
        settings.updateSettings(definition.getId(), new LanguageServerSettings.LanguageServerDefinitionSettings().setServerTrace(ServerTrace.messages));
        Disposer.register(getTestRootDisposable(), () -> settings.updateSettings(definition.getId(),
                new LanguageServerSettings.LanguageServerDefinitionSettings().setServerTrace(ServerTrace.off)));
        LanguageServerWrapper languageServer = LanguageServiceAccessor.getInstance(project).forceCreateServer(definition);
        languageServer.start();
        languageServer.getInitializedServer().get(10, TimeUnit.SECONDS);
        var panel = new LSPConsoleToolWindowPanel(project);
        try {
            var explorer = panel.getExplorer();
            PlatformTestUtil.waitWithEventsDispatching("The view must show the language server", () -> {
                var node = explorer.findNodeForServer(definition);
                return node != null && node.getActiveProcessTreeNode() != null;
            }, 10);

            // The server sends a notification whose character doesn't fit in an int, then one that LSP4J parses
            var remoteProxies = definition.getServer().getRemoteProxies();
            LanguageClient client = remoteProxies.get(remoteProxies.size() - 1);
            ((Endpoint) client).notify("textDocument/publishDiagnostics", publishDiagnosticsWithCharacter(4294967295L));
            client.logMessage(new MessageParams(MessageType.Info, "after the unparsable notification"));

            String traces = waitForTraces(panel, "Received notification 'window/logMessage'");
            int unparsable = traces.indexOf("Received notification 'textDocument/publishDiagnostics' that could not be parsed.");
            assertTrue(traces, unparsable >= 0);
            assertTrue(traces, traces.contains("Issue: Message could not be parsed."));
            assertTrue(traces, traces.contains("Expected an int but was 4294967295"));
            // In the order of the messages
            assertTrue(traces, unparsable < traces.indexOf("Received notification 'window/logMessage'"));
            // LSP4J only logged the notification, as before: the language server goes on
            assertEquals(ServerStatus.started, languageServer.getServerStatus());
        } finally {
            Disposer.dispose(panel);
            languageServer.stop();
        }
    }

    private static @NotNull JsonObject publishDiagnosticsWithCharacter(long character) {
        var start = new JsonObject();
        start.addProperty("line", 0);
        start.addProperty("character", 0);
        var end = new JsonObject();
        end.addProperty("line", 0);
        end.addProperty("character", character);
        var range = new JsonObject();
        range.add("start", start);
        range.add("end", end);
        var diagnostic = new JsonObject();
        diagnostic.add("range", range);
        diagnostic.addProperty("message", "A diagnostic up to the end of the line");
        var diagnostics = new JsonArray();
        diagnostics.add(diagnostic);
        var params = new JsonObject();
        params.addProperty("uri", "file:///test.unparsable");
        params.add("diagnostics", diagnostics);
        return params;
    }

    /**
     * Waits until the traces of the language server in the given view contain the given text, and returns them.
     */
    private @NotNull String waitForTraces(@NotNull LSPConsoleToolWindowPanel panel, @NotNull String text) {
        var explorer = panel.getExplorer();
        String[] traces = {""};
        PlatformTestUtil.waitWithEventsDispatching(() -> "The view must show the traces of the language server, which are:\n" + traces[0], () -> {
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
}
