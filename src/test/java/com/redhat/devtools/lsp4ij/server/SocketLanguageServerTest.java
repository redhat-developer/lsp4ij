/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * A language server which LSP4IJ reaches over a socket, here one that runs already, in the test's process: it
 * initializes and answers requests as a language server process does, and its closing the connection stops it.
 */
public class SocketLanguageServerTest extends LSPCodeInsightFixtureTestCase {

    /**
     * Bounds the waits; it only ends a hang.
     */
    private static final int HANG_BOUND_SECONDS = 3600;

    public SocketLanguageServerTest() {
        // the mock language server of the fixture takes no file of this test
        super("*.unused");
    }

    public void testALanguageServerOverASocketAnswersRequestsAndStopsWhenItClosesTheConnection() throws Exception {
        var project = myFixture.getProject();
        try (var listening = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var definition = new MockLanguageServerDefinition("socket-server", false) {
                @Override
                public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
                    return SocketStreamConnectionProvider.attachTo(InetAddress.getLoopbackAddress().getHostAddress(),
                            listening.getLocalPort());
                }
            };
            var server = definition.getServer();
            server.setHover(new Hover(new MarkupContent(MarkupKind.PLAINTEXT, "over a socket")));
            // the language server takes the connection, and talks LSP over it
            var connection = CompletableFuture.supplyAsync(() -> {
                try {
                    Socket accepted = listening.accept();
                    var launcher = LSPLauncher.createServerLauncher(server, accepted.getInputStream(),
                            accepted.getOutputStream());
                    server.addRemoteProxy(launcher.getRemoteProxy());
                    launcher.startListening();
                    return accepted;
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
            LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                    List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.sock"), null)));
            try {
                var psiFile = myFixture.configureByText("main.sock", "main");
                var file = psiFile.getVirtualFile();
                LanguageServerItem item = LanguageServiceAccessor.getInstance(project).getLanguageServers(psiFile, null, null)
                        .get(HANG_BOUND_SECONDS, TimeUnit.SECONDS).stream()
                        .filter(languageServer -> languageServer.getServerDefinition() == definition)
                        .findFirst()
                        .orElseThrow();
                var wrapper = item.getServerWrapper();
                assertEquals(ServerStatus.started, wrapper.getServerStatus());
                assertNull("LSP4IJ started no process", wrapper.getCurrentProcessId());

                var params = new HoverParams(new TextDocumentIdentifier(LSPIJUtils.toUriAsString(file)), new Position(0, 0));
                var hover = item.getServer().getTextDocumentService().hover(params).get(HANG_BOUND_SECONDS, TimeUnit.SECONDS);
                assertEquals("over a socket", hover.getContents().getRight().getValue());

                // the language server closes the connection
                connection.get(HANG_BOUND_SECONDS, TimeUnit.SECONDS).close();
                PlatformTestUtil.waitWithEventsDispatching("the language server stops",
                        () -> wrapper.getServerStatus() == ServerStatus.stopped, HANG_BOUND_SECONDS);
            } finally {
                server.waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
            }
        }
    }
}
