/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * The language servers of {@link SocketStreamConnectionProviderTest}, which run in Java processes of their own, with
 * the test classes only on their class path, so they use nothing but the JDK. Each takes the port as its argument and
 * echoes the lines of its connection until "bye", then ends. They write their log to the standard output or error, as
 * language servers do, with an empty line and a last line without a line feed, which the log leaves out and keeps.
 */
public final class SocketTestServers {

    private SocketTestServers() {
    }

    /**
     * A language server which listens on its port.
     */
    public static class ListeningServer {
        public static void main(String[] args) throws IOException {
            int port = Integer.parseInt(args[0]);
            try (var server = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
                System.out.println("listening on " + port);
                echo(server.accept());
            }
        }
    }

    /**
     * A language server which connects to its port.
     */
    public static class ConnectingServer {
        public static void main(String[] args) throws IOException {
            int port = Integer.parseInt(args[0]);
            System.err.println();
            System.err.println("connecting to " + port);
            echo(new Socket(InetAddress.getLoopbackAddress(), port));
            System.err.print("ended");
        }
    }

    /**
     * A language server which ends at once.
     */
    public static class EndingServer {
        public static void main(String[] args) {
        }
    }

    /**
     * A language server which neither listens nor connects, until its process is stopped.
     */
    public static class WaitingServer {
        public static void main(String[] args) throws Exception {
            System.err.println("waiting");
            new CompletableFuture<Void>().get();
        }
    }

    private static void echo(Socket connection) throws IOException {
        try (connection) {
            var in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            var out = new PrintWriter(connection.getOutputStream(), false, StandardCharsets.UTF_8);
            for (String line = in.readLine(); line != null && !line.equals("bye"); line = in.readLine()) {
                // A line feed, as the test writes and reads lines, not the platform's line separator
                out.print(line + '\n');
                out.flush();
            }
        }
    }
}
