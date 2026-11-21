/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that the DAP client reaches a DAP server which listens on the IPv6 loopback address only, as one which listens
 * on {@code localhost} may, where the system resolves {@code localhost} to {@code ::1} first, as for Node.js.
 */
public class DAPLoopbackConnectionTest {

    @Test
    void withoutAnAddressTheClientConnectsToADAPServerWhichListensOnTheIPv6LoopbackAddressOnly() throws IOException {
        for (String address : new String[]{null, ""}) {
            try (var ipv6Only = listenOnTheIPv6LoopbackAddressOnly()) {
                var server = ipv6Only.server();
                var streams = new TransportStreams.SocketTransportStreams(address, server.getLocalPort());
                try (var accepted = server.accept()) {
                    assertTrue(accepted.isConnected(), String.valueOf(address));
                } finally {
                    streams.close();
                }
            }
        }
    }

    @Test
    void withAnIPv4AddressTheClientDoesNotTryTheIPv6LoopbackAddress() throws IOException {
        try (var ipv6Only = listenOnTheIPv6LoopbackAddressOnly()) {
            var failure = assertThrows(RuntimeException.class,
                    () -> new TransportStreams.SocketTransportStreams("127.0.0.1", ipv6Only.port()));
            assertInstanceOf(ConnectException.class, failure.getCause());
        }
    }

    @Test
    void withoutAnAddressTheWaitForTheDAPServerSeesItsSocketOnTheIPv6LoopbackAddress() throws IOException {
        try (var ipv6Only = listenOnTheIPv6LoopbackAddressOnly()) {
            int port = ipv6Only.port();
            assertTrue(DAPServerReadyTracker.isSocketAvailable(null, port));
            assertFalse(DAPServerReadyTracker.isSocketAvailable("127.0.0.1", port));

            ipv6Only.server().close();
            assertFalse(DAPServerReadyTracker.isSocketAvailable(null, port), "nothing listens on the port anymore");
        }
    }

    /**
     * A DAP server which listens on a port of the IPv6 loopback address, and the same port of the IPv4 one, bound by a
     * socket which doesn't listen. While the port is bound there, nothing else on the machine can listen on it, so a
     * connection to {@code 127.0.0.1} on the port is refused, as the tests expect, rather than reach a program which
     * happens to listen on the same port number of the other address.
     */
    private record IPv6OnlyPort(@NotNull ServerSocket server, @NotNull Socket ipv4) implements Closeable {

        int port() {
            return server.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            try (var ignored = server) {
                ipv4.close();
            }
        }
    }

    private static @NotNull IPv6OnlyPort listenOnTheIPv6LoopbackAddressOnly() throws IOException {
        for (int attempt = 1; ; attempt++) {
            var server = listenOnTheIPv6LoopbackAddress();
            var ipv4 = new Socket();
            try {
                ipv4.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), server.getLocalPort()));
                return new IPv6OnlyPort(server, ipv4);
            } catch (IOException takenOnIPv4) {
                ipv4.close();
                server.close();
                if (attempt == 10) {
                    throw takenOnIPv4;
                }
            }
        }
    }

    private static @NotNull ServerSocket listenOnTheIPv6LoopbackAddress() throws IOException {
        ServerSocket server;
        try {
            server = new ServerSocket(0, 1, InetAddress.getByName("::1"));
        } catch (IOException noIPv6) {
            assumeTrue(false, "IPv6 is not available: " + noIPv6);
            throw noIPv6;
        }
        // A connection which never comes fails the test rather than hang it
        server.setSoTimeout(10_000);
        return server;
    }
}
