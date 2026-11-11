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
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
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
     * Bounds the waits, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 60;

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
                        .get(BOUND_SECONDS, TimeUnit.SECONDS).stream()
                        .filter(languageServer -> languageServer.getServerDefinition() == definition)
                        .findFirst()
                        .orElseThrow();
                var wrapper = item.getServerWrapper();
                assertEquals(ServerStatus.started, wrapper.getServerStatus());
                assertNull("LSP4IJ started no process", wrapper.getCurrentProcessId());

                var params = new HoverParams(new TextDocumentIdentifier(LSPIJUtils.toUriAsString(file)), new Position(0, 0));
                var hover = item.getServer().getTextDocumentService().hover(params).get(BOUND_SECONDS, TimeUnit.SECONDS);
                assertEquals("over a socket", hover.getContents().getRight().getValue());

                // The stop is seen as it happens: the editor asks for the language server again, which can start it
                // again before a check of its status would see it stopped
                var stopped = new CompletableFuture<Void>();
                var lifecycle = LanguageServerLifecycleManager.getInstance(project);
                var listener = new LanguageServerLifecycleListener() {
                    @Override
                    public void handleStatusChanged(@NotNull LanguageServerWrapper languageServer) {
                        if (languageServer == wrapper && languageServer.getServerStatus() == ServerStatus.stopped) {
                            stopped.complete(null);
                        }
                    }

                    @Override
                    public void handleLSPMessage(@NotNull Message message, @NotNull MessageConsumer consumer,
                                                 @NotNull LanguageServerWrapper languageServer) {
                    }

                    @Override
                    public void handleError(@NotNull LanguageServerWrapper languageServer, @NotNull Throwable exception) {
                    }

                    @Override
                    public void dispose() {
                    }
                };
                lifecycle.addLanguageServerLifecycleListener(listener);
                try {
                    // Nothing listens anymore, so that a start again fails at once instead of waiting on a connection
                    // which nothing serves
                    listening.close();
                    // the language server closes the connection
                    connection.get(BOUND_SECONDS, TimeUnit.SECONDS).close();
                    PlatformTestUtil.waitWithEventsDispatching("the language server stops", stopped::isDone, BOUND_SECONDS);
                } finally {
                    lifecycle.removeLanguageServerLifecycleListener(listener);
                }
            } finally {
                server.waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
            }
        }
    }
}
