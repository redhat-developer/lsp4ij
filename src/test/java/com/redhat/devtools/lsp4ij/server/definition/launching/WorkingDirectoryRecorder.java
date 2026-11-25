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
package com.redhat.devtools.lsp4ij.server.definition.launching;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A language server for the tests of the working directory, which runs as its own process and needs nothing but the
 * JDK. It prints the directory it runs in, then the value of the environment variable which its first argument names,
 * or an empty line if that isn't set:
 *
 * <pre>
 * WorkingDirectoryRecorder variable [port]
 * </pre>
 * Without a port, it prints them on its standard output and ends. With a port, it connects to that port on the
 * loopback address, prints them there, and ends once the other side closes the connection.
 */
public final class WorkingDirectoryRecorder {

    private WorkingDirectoryRecorder() {
    }

    public static void main(String[] args) throws IOException {
        String record = System.getProperty("user.dir") + "\n" + Objects.requireNonNullElse(System.getenv(args[0]), "") + "\n";
        if (args.length < 2) {
            System.out.print(record);
            System.out.flush();
            return;
        }
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(args[1]))) {
            socket.getOutputStream().write(record.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            while (in.read() != -1) {
                // Until LSP4IJ closes the connection
            }
        }
    }
}
