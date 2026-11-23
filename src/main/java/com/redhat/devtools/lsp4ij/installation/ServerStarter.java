/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.installation;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Starts the language server or the debug adapter which an installer installs as LSP4IJ starts it, with the command
 * line, working directory, environment and transport of its definition, for the {@code initialize} task of the
 * installer, which checks that the server answers the {@code initialize} request.
 *
 * @param id                 the id of the server, which a debug adapter gets as the {@code adapterID} of the DAP
 *                           {@code initialize} request.
 * @param protocol           the protocol the server speaks.
 * @param connectionProvider creates, for the given project, the connection provider which starts the server, or returns
 *                           null if LSP4IJ doesn't start the server, as one which runs already and which LSP4IJ connects
 *                           to.
 */
public record ServerStarter(@NotNull String id,
                            @NotNull Protocol protocol,
                            @NotNull Function<@NotNull Project, @Nullable StreamConnectionProvider> connectionProvider) {

    /**
     * The protocol a server speaks.
     */
    public enum Protocol {
        /**
         * The Language Server Protocol, of a language server.
         */
        LSP,
        /**
         * The Debug Adapter Protocol, of a debug adapter.
         */
        DAP
    }

    /**
     * Returns the connection provider which starts the server for the given project, which is not started yet, or null
     * if LSP4IJ doesn't start the server, as one which runs already and which LSP4IJ connects to.
     *
     * @param project the project.
     * @return the connection provider which starts the server, or null if LSP4IJ doesn't start it.
     */
    public @Nullable StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        return connectionProvider.apply(project);
    }
}
