/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A server which listens on a port of the IPv6 loopback address only, as a language server or a DAP server which
 * listens on {@code localhost} may, where the system resolves {@code localhost} to {@code ::1} first.
 * <p>
 * The same port of the IPv4 loopback address is bound by a socket which doesn't listen. While it is bound, nothing
 * else on the machine can listen there, so a connection to {@code 127.0.0.1} on the port is refused, as a test of such
 * a server expects, rather than reach another program which happens to listen on the same port number of the other
 * address.
 *
 * @param server the server, which listens on {@code ::1}.
 * @param ipv4   the socket which binds the same port of {@code 127.0.0.1}.
 */
public record IPv6OnlyLoopbackPort(@NotNull ServerSocket server, @NotNull Socket ipv4) implements Closeable {

    private static final int ATTEMPTS = 10;

    /**
     * Listens on a port of {@code ::1} which is free on {@code 127.0.0.1} too, and binds it there.
     *
     * @return the server and the socket which binds its port of {@code 127.0.0.1}.
     * @throws IOException if there is no IPv6 loopback address, or no such port is found.
     */
    public static @NotNull IPv6OnlyLoopbackPort listen() throws IOException {
        for (int attempt = 1; ; attempt++) {
            var server = new ServerSocket(0, 1, InetAddress.getByName("::1"));
            // A connection which never comes fails the test rather than hang it
            server.setSoTimeout(10_000);
            var ipv4 = new Socket();
            try {
                ipv4.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), server.getLocalPort()));
                return new IPv6OnlyLoopbackPort(server, ipv4);
            } catch (IOException takenOnIPv4) {
                ipv4.close();
                server.close();
                if (attempt == ATTEMPTS) {
                    throw takenOnIPv4;
                }
            }
        }
    }

    /**
     * Returns the port, the same on both addresses.
     *
     * @return the port.
     */
    public int port() {
        return server.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        try (var ignored = server) {
            ipv4.close();
        }
    }
}
