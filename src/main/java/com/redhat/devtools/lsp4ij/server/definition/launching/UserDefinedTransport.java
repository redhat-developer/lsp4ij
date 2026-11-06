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

import com.redhat.devtools.lsp4ij.server.SocketStreamConnectionProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * How LSP4IJ talks to a user-defined language server: over the standard input and output of its process, by default,
 * or over a socket, in one of the ways of {@link SocketStreamConnectionProvider}.
 * <p>
 * The socket transports are experimental: this API may change in a future release.
 *
 * @param mode the way LSP4IJ talks to the language server.
 * @param host the host of a language server which runs already, for {@link Mode#ATTACH}.
 * @param port the port of the socket, or 0 to choose a free port, which the command line takes as
 *             {@value #PORT_MACRO}, when LSP4IJ starts the language server.
 */
@ApiStatus.Experimental
public record UserDefinedTransport(@NotNull Mode mode, @Nullable String host, int port) {

    /**
     * The standard input and output of the language server's process.
     */
    public static final @NotNull UserDefinedTransport STDIO = new UserDefinedTransport(Mode.STDIO, null, 0);

    /**
     * The macro of the command line which is replaced with the port of the socket.
     */
    public static final @NotNull String PORT_MACRO = "${port}";

    /**
     * The ways LSP4IJ talks to a user-defined language server.
     */
    public enum Mode {
        /**
         * Over the standard input and output of the language server's process.
         */
        STDIO("stdio"),
        /**
         * Over a socket, to a language server which runs already and listens on the host and port.
         */
        ATTACH("attach"),
        /**
         * Over a socket, to a language server which LSP4IJ starts and which listens on the port.
         */
        START_AND_CONNECT("startAndConnect"),
        /**
         * Over a socket, from a language server which LSP4IJ starts and which connects to the port LSP4IJ listens on.
         */
        START_AND_ACCEPT("startAndAccept");

        private final @NotNull String id;

        Mode(@NotNull String id) {
            this.id = id;
        }

        /**
         * Returns the id of the mode, as the settings and templates store it.
         *
         * @return the id of the mode.
         */
        public @NotNull String getId() {
            return id;
        }

        /**
         * Returns the mode with the given id, and {@link #STDIO} for null or an unknown id.
         *
         * @param id the id of the mode.
         * @return the mode with the given id, and {@link #STDIO} for null or an unknown id.
         */
        public static @NotNull Mode fromId(@Nullable String id) {
            return Arrays.stream(values()).filter(mode -> mode.id.equals(id)).findFirst().orElse(STDIO);
        }
    }

    /**
     * Returns the transport which the given settings describe.
     *
     * @param modeId the id of the mode, or null for {@link Mode#STDIO}.
     * @param host   the host.
     * @param port   the port.
     * @return the transport which the given settings describe.
     */
    public static @NotNull UserDefinedTransport of(@Nullable String modeId, @Nullable String host, int port) {
        return of(Mode.fromId(modeId), host, port);
    }

    /**
     * Returns the transport of the given mode, host and port.
     *
     * @param mode the mode.
     * @param host the host.
     * @param port the port.
     * @return the transport of the given mode, host and port.
     */
    public static @NotNull UserDefinedTransport of(@NotNull Mode mode, @Nullable String host, int port) {
        return mode == Mode.STDIO ? STDIO : new UserDefinedTransport(mode, host, port);
    }

    /**
     * Returns true if LSP4IJ starts the language server, with its command line, and false if it runs already.
     *
     * @return true if LSP4IJ starts the language server, and false if it runs already.
     */
    public boolean startsLanguageServer() {
        return mode != Mode.ATTACH;
    }

    /**
     * Returns the given command line with {@value #PORT_MACRO} replaced with the given port.
     *
     * @param commandLine the command line.
     * @param port        the port.
     * @return the command line for the port.
     */
    public static @NotNull String withPort(@NotNull String commandLine, int port) {
        return commandLine.replace(PORT_MACRO, String.valueOf(port));
    }

    /**
     * Returns the transport to use with a command which an installer sets. An installer's command is written for the
     * standard input and output unless it takes the port as {@value #PORT_MACRO}, so a language server which LSP4IJ
     * starts over a socket is started over its standard input and output instead: with the installer's command, it
     * would never listen on the port, or connect to it. A language server which runs already doesn't use the command,
     * so it keeps its socket.
     *
     * @param command the command which the installer sets.
     * @return the transport to use with that command.
     */
    public @NotNull UserDefinedTransport forInstalledCommand(@Nullable String command) {
        boolean startedOverSocket = mode == Mode.START_AND_CONNECT || mode == Mode.START_AND_ACCEPT;
        return startedOverSocket && (command == null || !command.contains(PORT_MACRO)) ? STDIO : this;
    }
}
