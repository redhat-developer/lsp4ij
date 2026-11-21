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
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.jetbrains.annotations.NotNull;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    /**
     * A language server which runs already and listens on a socket answers a hover, and LSP4IJ stops it when the
     * language server closes the connection.
     */
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
                    /**
                     * Notes the stop of the language server of this test. LSP4IJ calls it on the thread which changed
                     * the status, right after the change.
                     */
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

    /**
     * A stop lets the message writer send the exit notification before it closes the connection, also when the write
     * takes a moment, so the language server gets it.
     */
    public void testAStopSendsTheExitNotificationBeforeItClosesTheConnection() throws Exception {
        var project = myFixture.getProject();
        try (var listening = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var definition = new MockLanguageServerDefinition("socket-server", false) {
                @Override
                public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
                    return new SlowExitProvider(listening.getLocalPort());
                }
            };
            var server = definition.getServer();
            var exitReceived = new CompletableFuture<Void>();
            var connection = CompletableFuture.supplyAsync(() -> {
                try {
                    Socket accepted = listening.accept();
                    var launcher = new LSPLauncher.Builder<LanguageClient>()
                            .setLocalService(server)
                            .setRemoteInterface(LanguageClient.class)
                            .setInput(accepted.getInputStream())
                            .setOutput(accepted.getOutputStream())
                            .wrapMessages(consumer -> message -> {
                                if (message instanceof NotificationMessage notification
                                        && "exit".equals(notification.getMethod())) {
                                    exitReceived.complete(null);
                                }
                                consumer.consume(message);
                            })
                            .create();
                    server.addRemoteProxy(launcher.getRemoteProxy());
                    launcher.startListening();
                    return accepted;
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
            LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                    List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.sock"), null)));
            var errors = new CopyOnWriteArrayList<Throwable>();
            var lifecycle = LanguageServerLifecycleManager.getInstance(project);
            var listener = new LanguageServerLifecycleListener() {
                @Override
                public void handleStatusChanged(@NotNull LanguageServerWrapper languageServer) {
                }

                @Override
                public void handleLSPMessage(@NotNull Message message, @NotNull MessageConsumer consumer,
                                             @NotNull LanguageServerWrapper languageServer) {
                }

                @Override
                public void handleError(@NotNull LanguageServerWrapper languageServer, @NotNull Throwable exception) {
                    errors.add(exception);
                }

                @Override
                public void dispose() {
                }
            };
            lifecycle.addLanguageServerLifecycleListener(listener);
            try {
                // Without an editor: the editor of a file connects it to its language servers once indexing is done,
                // which can be after the stop, as on a slow machine, and would then start the language server again
                var psiFile = myFixture.addFileToProject("main.sock", "main");
                LanguageServerItem item = LanguageServiceAccessor.getInstance(project).getLanguageServers(psiFile, null, null)
                        .get(BOUND_SECONDS, TimeUnit.SECONDS).stream()
                        .filter(languageServer -> languageServer.getServerDefinition() == definition)
                        .findFirst()
                        .orElseThrow();
                var wrapper = item.getServerWrapper();
                assertEquals(ServerStatus.started, wrapper.getServerStatus());
                connection.get(BOUND_SECONDS, TimeUnit.SECONDS);
                // Nothing listens anymore, so that a start again fails at once instead of waiting on a connection
                // which nothing serves
                listening.close();

                wrapper.stop().get(BOUND_SECONDS, TimeUnit.SECONDS);

                // the language server reads the notification on its own thread
                exitReceived.get(BOUND_SECONDS, TimeUnit.SECONDS);
                assertEquals("errors of the stop", List.of(), errors);
            } finally {
                lifecycle.removeLanguageServerLifecycleListener(listener);
                server.waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
            }
        }
    }

    /**
     * Connects to a language server which runs already, and takes a moment to write the exit notification, as a writer
     * which has other messages to send first does.
     */
    private static final class SlowExitProvider extends SocketStreamConnectionProvider {

        SlowExitProvider(int port) {
            super(Direction.CONNECT, InetAddress.getLoopbackAddress().getHostAddress(), port, null);
        }

        @Override
        public OutputStream getOutputStream() {
            OutputStream connection = super.getOutputStream();
            return connection == null ? null : new FilterOutputStream(connection) {
                @Override
                public void write(byte @NotNull [] bytes, int offset, int length) throws IOException {
                    if (new String(bytes, offset, length, StandardCharsets.UTF_8).contains("\"method\":\"exit\"")) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    out.write(bytes, offset, length);
                }
            };
        }
    }
}
