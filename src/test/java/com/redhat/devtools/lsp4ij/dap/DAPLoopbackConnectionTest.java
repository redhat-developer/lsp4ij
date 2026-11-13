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

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;

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
            try (var server = listenOnTheIPv6LoopbackAddress()) {
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
        try (var server = listenOnTheIPv6LoopbackAddress()) {
            var failure = assertThrows(RuntimeException.class,
                    () -> new TransportStreams.SocketTransportStreams("127.0.0.1", server.getLocalPort()));
            assertInstanceOf(ConnectException.class, failure.getCause());
        }
    }

    @Test
    void withoutAnAddressTheWaitForTheDAPServerSeesItsSocketOnTheIPv6LoopbackAddress() throws IOException {
        int port;
        try (var server = listenOnTheIPv6LoopbackAddress()) {
            port = server.getLocalPort();
            assertTrue(DAPServerReadyTracker.isSocketAvailable(null, port));
            assertFalse(DAPServerReadyTracker.isSocketAvailable("127.0.0.1", port));
        }
        assertFalse(DAPServerReadyTracker.isSocketAvailable(null, port), "nothing listens on the port anymore");
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
