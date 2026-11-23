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
package com.redhat.devtools.lsp4ij.dap.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cancelling the future of a request which the launcher of a DAP client sent sends the DAP
 * <a href="https://microsoft.github.io/debug-adapter-protocol/specification#Base_Protocol_Cancel">cancel request</a>,
 * whose {@code requestId} is the {@code seq} of the request, when the debug adapter supports it, and nothing otherwise:
 * never LSP's {@code $/cancelRequest}, which LSP4J 1.0.0 sends as a DAP event. What the debug adapter receives is read
 * raw, from its end of a loopback socket.
 */
public class DAPClientLauncherTest {

    /**
     * Bounds the waits of the debug adapter and of the client, so that a test fails instead of hanging.
     */
    private static final int BOUND_MILLIS = 10_000;

    @Test
    void cancellingARequestSendsTheCancelRequestWhenTheDebugAdapterSupportsIt() throws Exception {
        var capabilities = new Capabilities();
        capabilities.setSupportsCancelRequest(true);
        List<LogRecord> warnings = new CopyOnWriteArrayList<>();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger remoteEndpointLog = Logger.getLogger(RemoteEndpoint.class.getName());
        remoteEndpointLog.addHandler(handler);
        try (var adapter = new Adapter(capabilities)) {
            var threads = adapter.server().threads();
            int seq = adapter.readRequest("threads");

            assertTrue(threads.cancel(true));
            assertTrue(threads.isCancelled());
            var cancel = adapter.read();
            assertEquals("request", string(cancel, "type"), cancel::toString);
            assertEquals("cancel", string(cancel, "command"), cancel::toString);
            var arguments = cancel.getAsJsonObject("arguments");
            assertEquals(seq, arguments != null && arguments.has("requestId") ? arguments.get("requestId").getAsInt() : -1,
                    cancel::toString);
            // As a debug adapter which supports it does: it answers the cancel request, and the cancelled request
            // with the error 'cancelled'
            adapter.write("{\"seq\":1,\"type\":\"response\",\"request_seq\":" + cancel.get("seq").getAsInt()
                    + ",\"success\":true,\"command\":\"cancel\"}");
            adapter.write("{\"seq\":2,\"type\":\"response\",\"request_seq\":" + seq
                    + ",\"success\":false,\"command\":\"threads\",\"message\":\"cancelled\"}");

            // The next message is the next request: the client sent nothing else for the cancelled request
            var next = adapter.server().threads();
            int nextSeq = adapter.readRequest("threads");
            adapter.write("{\"seq\":3,\"type\":\"response\",\"request_seq\":" + nextSeq
                    + ",\"success\":true,\"command\":\"threads\",\"body\":{\"threads\":[]}}");
            // The client handles the messages in their order: the answer to the cancel request was handled before
            next.get(BOUND_MILLIS, TimeUnit.MILLISECONDS);
            assertTrue(warnings.isEmpty(), () -> "LSP4J logged " + warnings.stream().map(LogRecord::getMessage).toList());
        } finally {
            remoteEndpointLog.removeHandler(handler);
        }
    }

    @Test
    void cancellingARequestSendsNothingWhenTheDebugAdapterDoesNotSupportTheCancelRequest() throws Exception {
        try (var adapter = new Adapter(new Capabilities())) {
            var threads = adapter.server().threads();
            adapter.readRequest("threads");

            assertTrue(threads.cancel(true));
            assertTrue(threads.isCancelled());

            // The next message is the next request: the client sent nothing for the cancelled request
            adapter.server().threads();
            adapter.readRequest("threads");
        }
    }

    private static String string(@NotNull JsonObject message, @NotNull String name) {
        return message.has(name) ? message.get(name).getAsString() : null;
    }

    /**
     * A debug adapter on a loopback socket, connected to the launcher of a DAP client with the given capabilities.
     */
    private static final class Adapter implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final Socket clientEnd;
        private final Socket adapterEnd;
        private final InputStream wire;
        private final OutputStream answers;
        private final ExecutorService threads = Executors.newCachedThreadPool();
        private final Launcher<IDebugProtocolServer> launcher;
        private final Future<Void> listening;

        Adapter(@NotNull Capabilities capabilities) throws IOException {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            clientEnd = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
            adapterEnd = serverSocket.accept();
            adapterEnd.setSoTimeout(BOUND_MILLIS);
            wire = new BufferedInputStream(adapterEnd.getInputStream());
            answers = adapterEnd.getOutputStream();
            launcher = DAPClientLauncher.create(new IDebugProtocolClient() {
                    }, clientEnd.getInputStream(), clientEnd.getOutputStream(), threads, Function.identity(),
                    () -> capabilities);
            listening = launcher.startListening();
        }

        IDebugProtocolServer server() {
            return launcher.getRemoteProxy();
        }

        /**
         * Reads the next message, which must be a request with the given command, and returns its {@code seq}.
         */
        int readRequest(@NotNull String command) throws IOException {
            var request = read();
            assertEquals("request", string(request, "type"), request::toString);
            assertEquals(command, string(request, "command"), request::toString);
            return request.get("seq").getAsInt();
        }

        /**
         * Reads the next message of the base protocol of DAP: the header {@code Content-Length}, an empty line, and
         * the JSON content.
         */
        JsonObject read() throws IOException {
            int length = -1;
            for (String line = readLine(); !line.isEmpty(); line = readLine()) {
                if (line.startsWith("Content-Length:")) {
                    length = Integer.parseInt(line.substring("Content-Length:".length()).trim());
                }
            }
            if (length < 0) {
                throw new IOException("No Content-Length header");
            }
            byte[] content = wire.readNBytes(length);
            if (content.length < length) {
                throw new EOFException();
            }
            return JsonParser.parseString(new String(content, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        private String readLine() throws IOException {
            var line = new ByteArrayOutputStream();
            for (int c = wire.read(); c != '\n'; c = wire.read()) {
                if (c < 0) {
                    throw new EOFException();
                }
                if (c != '\r') {
                    line.write(c);
                }
            }
            return line.toString(StandardCharsets.US_ASCII);
        }

        /**
         * Writes a message of the base protocol of DAP with the given JSON content.
         */
        void write(@NotNull String json) throws IOException {
            byte[] content = json.getBytes(StandardCharsets.UTF_8);
            answers.write(("Content-Length: " + content.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            answers.write(content);
            answers.flush();
        }

        @Override
        public void close() throws IOException {
            listening.cancel(true);
            threads.shutdownNow();
            adapterEnd.close();
            clientEnd.close();
            serverSocket.close();
        }
    }
}
