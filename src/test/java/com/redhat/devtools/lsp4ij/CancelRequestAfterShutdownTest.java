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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.features.documentation.LSPHoverParams;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService;
import com.redhat.devtools.lsp4ij.mock.MockWorkspaceService;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.CancelParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A language server is sent nothing but {@code exit} after {@code shutdown}, as LSP requires: a request that is
 * cancelled once its stopping language server has been sent {@code shutdown} ends without a {@code $/cancelRequest}.
 * A request that is cancelled while its language server runs still sends one.
 */
public class CancelRequestAfterShutdownTest extends UsefulTestCase {

    private LSPCodeInsightTestFixture myFixture;
    private RecordingServerDefinition definition;

    public void testARequestCancelledOnceTheShutdownIsSentSendsNoCancelRequest() throws Exception {
        var server = connect();
        var connection = definition.lastConnection();
        var request = sendHeldRequest(server);
        var shutdownAnswer = new CompletableFuture<Void>();
        definition.getServer().holdShutdown(shutdownAnswer);
        CompletableFuture<Void> stopped;
        try {
            stopped = server.getServerWrapper().stop();
            awaitRead(connection, "shutdown");

            // A feature cancels its request, as when the user types on, while the language server shuts down
            request.cancel(true);
        } finally {
            shutdownAnswer.complete(null);
        }

        PlatformTestUtil.waitWithEventsDispatching("The language server must stop", stopped::isDone, 10);
        assertEquals("The language server must read nothing but exit after shutdown",
                List.of("shutdown", "exit"), connection.readFromShutdown());
        assertTrue("The request must be cancelled", request.isCancelled());
    }

    public void testCancelOnStopOnceTheShutdownIsSentSendsNoCancelRequest() throws Exception {
        var server = connect();
        var connection = definition.lastConnection();
        var request = sendHeldRequest(server);
        var shutdownAnswer = new CompletableFuture<Void>();
        definition.getServer().holdShutdown(shutdownAnswer);
        CompletableFuture<Void> stopped;
        try {
            stopped = server.getServerWrapper().stop();
            awaitRead(connection, "shutdown");

            // The stop came between the send of the request and its keeping for the stop, so it is cancelled at once
            server.getServerWrapper().cancelOnStop(server.getServer(), request);
        } finally {
            shutdownAnswer.complete(null);
        }

        PlatformTestUtil.waitWithEventsDispatching("The language server must stop", stopped::isDone, 10);
        assertEquals("The language server must read nothing but exit after shutdown",
                List.of("shutdown", "exit"), connection.readFromShutdown());
        assertTrue("The request must be cancelled", request.isCancelled());
    }

    public void testARequestKeptForTheStopIsCancelledWithoutCancelRequestAfterTheShutdown() throws Exception {
        var server = connect();
        var wrapper = server.getServerWrapper();
        var connection = definition.lastConnection();
        var request = sendHeldRequest(server);
        wrapper.cancelOnStop(server.getServer(), request);
        // The stop cancels the requests kept for it once it has started the shutdown, on another thread. A listener of
        // the LSP messages, as the LSP console, which takes its time over the $/cancelRequest of the stop, lets the
        // shutdown request go first.
        var listener = new SlowCancelRequestListener(wrapper, connection, "workspace/symbol");
        var lifecycle = LanguageServerLifecycleManager.getInstance(myFixture.getProject());
        var shutdownAnswer = new CompletableFuture<Void>();
        definition.getServer().holdShutdown(shutdownAnswer);
        lifecycle.addLanguageServerLifecycleListener(listener);
        CompletableFuture<Void> stopped;
        try {
            stopped = wrapper.stop();
        } finally {
            lifecycle.removeLanguageServerLifecycleListener(listener);
            shutdownAnswer.complete(null);
        }

        PlatformTestUtil.waitWithEventsDispatching("The language server must stop", stopped::isDone, 10);
        assertEquals("The language server must read nothing but exit after shutdown",
                List.of("shutdown", "exit"), connection.readFromShutdown());
        // A $/cancelRequest sent before the shutdown request can be answered before the request is cancelled
        assertTrue("The request must end", request.isDone());
    }

    public void testAFileRequestEndedByTheStopSendsNoCancelRequestAfterTheShutdown() throws Exception {
        var server = connect();
        var wrapper = server.getServerWrapper();
        var connection = definition.lastConnection();
        var hover = LSPFileSupport.getSupport(myFixture.getFile()).getHoverSupport()
                .getHover(new LSPHoverParams(new TextDocumentIdentifier(), new Position(0, 0), 0));
        PlatformTestUtil.waitWithEventsDispatching("The language server must get the hover request",
                () -> !definition.hovers.isEmpty(), 10);
        // The stop disposes the support of the file, which cancels its requests, in a read action on another thread
        var listener = new SlowCancelRequestListener(wrapper, connection, "textDocument/hover");
        var lifecycle = LanguageServerLifecycleManager.getInstance(myFixture.getProject());
        var shutdownAnswer = new CompletableFuture<Void>();
        definition.getServer().holdShutdown(shutdownAnswer);
        lifecycle.addLanguageServerLifecycleListener(listener);
        CompletableFuture<Void> stopped;
        try {
            stopped = wrapper.stop();
            PlatformTestUtil.waitWithEventsDispatching("The stop must end the hover request", hover::isDone, 10);
        } finally {
            lifecycle.removeLanguageServerLifecycleListener(listener);
            shutdownAnswer.complete(null);
        }

        PlatformTestUtil.waitWithEventsDispatching("The language server must stop", stopped::isDone, 10);
        assertEquals("The language server must read nothing but exit after shutdown",
                List.of("shutdown", "exit"), connection.readFromShutdown());
    }

    public void testARequestCancelledWhileTheServerRunsSendsCancelRequest() throws Exception {
        var server = connect();
        var connection = definition.lastConnection();
        var request = sendHeldRequest(server);

        request.cancel(true);

        // LSP4J cancels the request of the language server when the server reads the $/cancelRequest
        PlatformTestUtil.waitWithEventsDispatching("The language server must be asked to cancel the request",
                () -> definition.requests.get(0).isCancelled(), 10);
        assertTrue(connection.read.contains("$/cancelRequest"));
    }

    private @NotNull LanguageServerItem connect() throws Exception {
        var file = myFixture.configureByText("main.foo", "content");
        var servers = LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        assertSize(1, servers);
        return servers.get(0);
    }

    private @NotNull CompletableFuture<?> sendHeldRequest(@NotNull LanguageServerItem server) {
        var request = server.getWorkspaceService().symbol(new WorkspaceSymbolParams("main"));
        PlatformTestUtil.waitWithEventsDispatching("The language server must get the request",
                () -> !definition.requests.isEmpty(), 10);
        return request;
    }

    private static void awaitRead(@NotNull RecordingConnectionProvider connection, @NotNull String method) {
        PlatformTestUtil.waitWithEventsDispatching("The language server must read " + method,
                () -> connection.read.contains(method), 10);
    }

    /**
     * A listener of the LSP messages which, for the {@code $/cancelRequest} of the request of the given method that the
     * given language server is sent, waits until the language server has read its shutdown request.
     */
    private static final class SlowCancelRequestListener implements LanguageServerLifecycleListener {

        private final @NotNull LanguageServerWrapper languageServer;
        private final @NotNull RecordingConnectionProvider connection;
        private final @NotNull String method;

        SlowCancelRequestListener(@NotNull LanguageServerWrapper languageServer,
                                  @NotNull RecordingConnectionProvider connection,
                                  @NotNull String method) {
            this.languageServer = languageServer;
            this.connection = connection;
            this.method = method;
        }

        @Override
        public void handleLSPMessage(@NotNull Message message,
                                     @NotNull MessageConsumer messageConsumer,
                                     @NotNull LanguageServerWrapper languageServer) {
            if (languageServer != this.languageServer || !(messageConsumer instanceof StreamMessageConsumer)
                    || !(message instanceof NotificationMessage notification)
                    || !(notification.getParams() instanceof CancelParams cancel)
                    || !cancel.getId().equals(connection.requestIds.get(method))) {
                return;
            }
            try {
                connection.shutdownRead.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                // The language server read no shutdown request, which the test shows
            }
        }

        @Override
        public void handleStatusChanged(@NotNull LanguageServerWrapper languageServer) {
        }

        @Override
        public void handleError(@NotNull LanguageServerWrapper languageServer, @NotNull Throwable exception) {
        }

        @Override
        public void dispose() {
        }
    }

    /**
     * A language server with {@code workspace/symbol} and {@code textDocument/hover}, which never answers them, and
     * whose connections record the messages that it reads.
     */
    private static final class RecordingServerDefinition extends MockLanguageServerDefinition {

        private final List<CompletableFuture<?>> requests = new CopyOnWriteArrayList<>();
        private final List<CompletableFuture<?>> hovers = new CopyOnWriteArrayList<>();
        private final List<RecordingConnectionProvider> connections = new CopyOnWriteArrayList<>();
        private final MockLanguageServer server;

        RecordingServerDefinition() {
            super("test-cancel-request-after-shutdown", false);
            server = new MockLanguageServer(() -> {
                var capabilities = MockLanguageServer.defaultServerCapabilities();
                capabilities.setWorkspaceSymbolProvider(true);
                return capabilities;
            });
            server.setWorkspaceService(new MockWorkspaceService(CompletableFuture::completedFuture) {
                @Override
                public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(
                        WorkspaceSymbolParams params) {
                    var request = new CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>>();
                    requests.add(request);
                    return request;
                }
            });
            server.setTextDocumentService(new MockTextDocumentService(CompletableFuture::completedFuture) {
                @Override
                public CompletableFuture<Hover> hover(HoverParams params) {
                    var hover = new CompletableFuture<Hover>();
                    hovers.add(hover);
                    return hover;
                }
            });
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            var connection = new RecordingConnectionProvider(server);
            connections.add(connection);
            return connection;
        }

        @Override
        public @NotNull MockLanguageServer getServer() {
            return server;
        }

        @NotNull
        RecordingConnectionProvider lastConnection() {
            return connections.get(connections.size() - 1);
        }
    }

    /**
     * A connection to the mock language server which records the methods of the messages that the language server
     * reads, in the order it reads them. Its stop closes the input of the language server first, and waits until the
     * language server has read what was sent before it, so that the record has every message sent to it.
     */
    private static final class RecordingConnectionProvider implements StreamConnectionProvider {

        private static final ExecutorService SERVER_THREADS = Executors.newCachedThreadPool();

        private final @NotNull MockLanguageServer server;
        private final List<String> read = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Void> shutdownRead = new CompletableFuture<>();
        // The id of the first request of each method that the language server read
        private final Map<String, String> requestIds = new ConcurrentHashMap<>();
        private InputStream clientInput;
        private OutputStream clientOutput;
        private OutputStream serverOutput;
        private Future<Void> serverListening;

        RecordingConnectionProvider(@NotNull MockLanguageServer server) {
            this.server = server;
        }

        @Override
        public synchronized void start() throws CannotStartProcessException {
            try {
                Pipe clientToServer = Pipe.open();
                Pipe serverToClient = Pipe.open();
                clientInput = Channels.newInputStream(serverToClient.source());
                clientOutput = Channels.newOutputStream(clientToServer.sink());
                serverOutput = Channels.newOutputStream(serverToClient.sink());
                Launcher<LanguageClient> launcher = new LSPLauncher.Builder<LanguageClient>()
                        .setLocalService(server)
                        .setRemoteInterface(LanguageClient.class)
                        .setInput(Channels.newInputStream(clientToServer.source()))
                        .setOutput(serverOutput)
                        .setExecutorService(SERVER_THREADS)
                        .wrapMessages(this::recordingWhatTheServerReads)
                        .create();
                serverListening = launcher.startListening();
                server.addRemoteProxy(launcher.getRemoteProxy());
            } catch (IOException e) {
                throw new CannotStartProcessException(e);
            }
        }

        @Override
        public InputStream getInputStream() {
            return clientInput;
        }

        @Override
        public OutputStream getOutputStream() {
            return clientOutput;
        }

        @Override
        public synchronized void stop() {
            if (serverListening == null) {
                return;
            }
            // The end of its input ends the listening of the language server, after the messages before it
            close(clientOutput);
            try {
                serverListening.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | CancellationException | TimeoutException e) {
                // The record then misses messages, which the test shows
            }
            close(serverOutput);
            close(clientInput);
            serverListening = null;
        }

        private @NotNull MessageConsumer recordingWhatTheServerReads(@NotNull MessageConsumer consumer) {
            if (!(consumer instanceof RemoteEndpoint)) {
                return consumer;
            }
            // The remote endpoint consumes the messages that the language server reads, in the order it reads them
            return message -> {
                String method = methodOf(message);
                read.add(method);
                if (message instanceof RequestMessage request) {
                    requestIds.putIfAbsent(method, request.getId());
                }
                if ("shutdown".equals(method)) {
                    shutdownRead.complete(null);
                }
                consumer.consume(message);
            };
        }

        /**
         * Returns the methods of the messages that the language server read from its shutdown request on.
         */
        @NotNull
        List<String> readFromShutdown() {
            int shutdown = read.indexOf("shutdown");
            return shutdown < 0 ? List.of() : List.copyOf(read.subList(shutdown, read.size()));
        }

        private static @NotNull String methodOf(@NotNull Message message) {
            if (message instanceof RequestMessage request) {
                return request.getMethod();
            }
            if (message instanceof NotificationMessage notification) {
                return notification.getMethod();
            }
            return message.getClass().getSimpleName();
        }

        private static void close(@NotNull Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Already closed
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
                IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        definition = new RecordingServerDefinition();
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null)));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            definition.getServer().waitBeforeTearDown();
            LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), definition);
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }
    }
}
