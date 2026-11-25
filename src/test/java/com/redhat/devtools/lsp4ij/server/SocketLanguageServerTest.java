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
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
     * is still going on as the stop waits for the writer, so the language server gets it.
     */
    public void testAStopSendsTheExitNotificationBeforeItClosesTheConnection() throws Exception {
        var project = myFixture.getProject();
        try (var listening = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var provider = new AtomicReference<HeldExitProvider>();
            var definition = new MockLanguageServerDefinition("socket-server", false) {
                @Override
                public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
                    var created = new HeldExitProvider(listening.getLocalPort());
                    provider.set(created);
                    return created;
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
                var held = provider.get();
                // Once the write goes on, the writer has as long as a slow runner takes to end it, instead of what is
                // left of the second for a user
                wrapper.setStopWaitTimeout(Duration.ofSeconds(IN_MEMORY_TIMEOUT_SECONDS));

                held.stopping();
                CompletableFuture<Void> stopped = wrapper.stop();
                try {
                    // A stop that doesn't wait for the writer closes the connection, and then ends the hold
                    stopped.whenComplete((result, error) -> held.release.complete(null));
                    stopped.get(BOUND_SECONDS, TimeUnit.SECONDS);
                } finally {
                    held.release.complete(null);
                }

                assertTrue("the stop must wait in " + HeldExitProvider.WAIT_FOR_THE_MESSAGE_WRITER + " while the writer "
                        + "writes the exit notification, whose write "
                        + (held.exitWriteStarted.isDone() ? "started" : "didn't start"), held.stopWaited.isDone());
                // the language server reads the notification on its own thread
                exitReceived.get(BOUND_SECONDS, TimeUnit.SECONDS);
                assertEquals("errors of the stop", List.of(), errors);
            } finally {
                var held = provider.get();
                if (held != null) {
                    held.release.complete(null);
                }
                lifecycle.removeLanguageServerLifecycleListener(listener);
                server.waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
            }
        }
    }

    /**
     * Connects to a language server which runs already, and holds the write of the exit notification until the stop
     * which sent it waits for the message writer, as a writer which has other messages to send first does. So the stop
     * waits for a write which goes on, whenever the test thread runs.
     */
    private static final class HeldExitProvider extends SocketStreamConnectionProvider {

        // Where the stop of a language server waits for its message writer, before it closes the connection
        static final String WAIT_FOR_THE_MESSAGE_WRITER = "LanguageServerWrapper.waitForTheMessageWriter";

        // Completed, with its thread, as the stop checks the connection, which it does before the shutdown
        private final CompletableFuture<Thread> stopChecksTheConnection = new CompletableFuture<>();
        // Completed as the writer starts to write the exit notification
        final CompletableFuture<Void> exitWriteStarted = new CompletableFuture<>();
        // Completed as the writer, which holds the exit notification, sees the stop wait for it
        final CompletableFuture<Void> stopWaited = new CompletableFuture<>();
        // Ends the hold of the exit notification, as the test ends
        final CompletableFuture<Void> release = new CompletableFuture<>();
        private volatile boolean stopping;

        HeldExitProvider(int port) {
            super(Direction.CONNECT, InetAddress.getLoopbackAddress().getHostAddress(), port, null);
        }

        /**
         * Tells the provider that the test stops the language server: the next check of the connection is the stop's.
         */
        void stopping() {
            stopping = true;
        }

        @Override
        public boolean isAlive() {
            if (stopping) {
                stopChecksTheConnection.complete(Thread.currentThread());
            }
            return super.isAlive();
        }

        @Override
        public OutputStream getOutputStream() {
            OutputStream connection = super.getOutputStream();
            return connection == null ? null : new FilterOutputStream(connection) {
                @Override
                public void write(byte @NotNull [] bytes, int offset, int length) throws IOException {
                    if (new String(bytes, offset, length, StandardCharsets.UTF_8).contains("\"method\":\"exit\"")) {
                        exitWriteStarted.complete(null);
                        holdUntilTheStopWaitsForTheWriter();
                    }
                    out.write(bytes, offset, length);
                }
            };
        }

        /**
         * Holds the writer, as it writes the exit notification, until the thread of the stop waits in
         * {@link #WAIT_FOR_THE_MESSAGE_WRITER}, for the writer, as SelectedEditorConnectionTest holds the dispatcher. A
         * thread tells no other that it starts to wait, so the thread of the stop is looked at, and its wait is
         * recognized by its stack, which shows that it parks, at its top, in that wait, below. The hold ends without
         * that once released, or after {@link #BOUND_SECONDS}.
         */
        private void holdUntilTheStopWaitsForTheWriter() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUND_SECONDS);
            try {
                CompletableFuture.anyOf(stopChecksTheConnection, release).get(BOUND_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException e) {
                // no stop checked the connection in time, which the test reports
                return;
            }
            var stop = stopChecksTheConnection.getNow(null);
            while (stop != null && !release.isDone() && System.nanoTime() - deadline < 0) {
                var state = stop.getState();
                if ((state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING)
                        && parksInTheWaitForTheWriter(stop.getStackTrace())) {
                    stopWaited.complete(null);
                    return;
                }
                Thread.onSpinWait();
            }
        }

        /**
         * True if the given stack of a thread parks it, at its top, in {@link #WAIT_FOR_THE_MESSAGE_WRITER}, below.
         */
        private static boolean parksInTheWaitForTheWriter(StackTraceElement[] stack) {
            return stack.length > 0
                    && stack[0].getClassName().equals("jdk.internal.misc.Unsafe")
                    && stack[0].getMethodName().equals("park")
                    && Arrays.stream(stack).anyMatch(frame ->
                    frame.getClassName().equals(LanguageServerWrapper.class.getName())
                            && frame.getMethodName().equals("waitForTheMessageWriter"));
        }
    }
}
