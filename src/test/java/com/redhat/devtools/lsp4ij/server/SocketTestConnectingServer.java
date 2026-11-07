/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import java.io.IOException;

/**
 * {@link SocketTestServers.ConnectingServer} under a class name without '$', for the command line of a user-defined
 * language server, whose macros are written with '$'.
 */
public final class SocketTestConnectingServer {

    private SocketTestConnectingServer() {
    }

    public static void main(String[] args) throws IOException {
        SocketTestServers.ConnectingServer.main(args);
    }
}
