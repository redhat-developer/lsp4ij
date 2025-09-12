/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.runInTerminal;

import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Service API for handling Debug Adapter Protocol {@code runInTerminal} requests.
 * <p>
 * Implementations of this service are responsible for spawning a new terminal instance
 * (either external or integrated) and executing the command specified in the
 * {@link RunInTerminalRequestArguments}. Implementations are typically platform-specific
 * (e.g., Windows, Linux, macOS).
 * </p>
 *
 * <p>This service is used to support the DAP {@code runInTerminal} feature, allowing
 * debug adapters to request that a program be launched in a user-visible terminal.</p>
 *
 * <h2>Implementation notes:</h2>
 * <ul>
 *   <li>Implementations should check {@link #isApplicable()} to determine whether the service
 *       can run on the current OS/environment.</li>
 *   <li>The {@link #runInTerminal(RunInTerminalRequestArguments, DAPClient)} method should
 *       execute the command asynchronously and return a {@link CompletableFuture} that
 *       completes with the {@link RunInTerminalResponse} when the terminal is launched.</li>
 * </ul>
 */
public interface RunInTerminalService {

    /**
     * Determines whether this implementation is applicable in the current environment.
     * <p>
     * This is typically used to filter OS-specific services (e.g., Windows, Linux, macOS)
     * so that only the correct one is used at runtime.
     * </p>
     *
     * @return {@code true} if this service can handle {@code runInTerminal} requests
     * on the current platform, {@code false} otherwise.
     */
    boolean isApplicable();

    /**
     * Launches a new terminal and executes the command specified in the given
     * {@link RunInTerminalRequestArguments}.
     *
     * <p>
     * The implementation should spawn a terminal process (external or integrated),
     * set the working directory and environment variables as specified,
     * and start the target program. This method must return immediately
     * with a {@link CompletableFuture} that completes when the terminal is successfully
     * launched or completes exceptionally if an error occurs.
     * </p>
     *
     * @param args    the {@link RunInTerminalRequestArguments} containing the command,
     *                working directory, environment variables, and terminal type
     * @param client the DAP client {@link DAPClient} in which this terminal is being launched
     * @return a {@link CompletableFuture} resolving to a {@link RunInTerminalResponse}
     * containing the process ID or {@code null} if not supported.
     */
    CompletableFuture<RunInTerminalResponse> runInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                           @NotNull DAPClient client);

    /**
     * Releases all terminals currently assigned to the given client.
     *
     * @param client the DAP client whose terminals should be released
     */
    void releaseClientTerminals(@NotNull DAPClient client);
}
