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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A transport which a language server supports, as its template lists it: the mode, and the argument of the command
 * for it, such as {@code --stdio}, or {@code --socket=${port}} for a socket.
 *
 * @param mode     the mode.
 * @param argument the argument of the command for this mode, or null if the template names none.
 */
public record TransportSupport(@NotNull UserDefinedTransport.Mode mode, @Nullable String argument) {

    /**
     * Returns true if the given transports, as a template lists them, are all sockets: the template says that its
     * language server talks LSP only over a socket. A template which lists no transports doesn't say.
     *
     * @param supportedTransports the transports which a template lists, empty if it lists none.
     * @return true if the template lists transports, and none of them is the standard input and output.
     */
    public static boolean talksOnlyOverASocket(@NotNull List<TransportSupport> supportedTransports) {
        return !supportedTransports.isEmpty() && supportedTransports.stream()
                .noneMatch(transport -> transport.mode() == UserDefinedTransport.Mode.STDIO);
    }
}
