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
package com.redhat.devtools.lsp4ij.dap.runInTerminal.external;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.dap.runInTerminal.RunInTerminalService;
import com.redhat.devtools.lsp4ij.internal.ResponseErrorExceptionWrapper;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Base class for external terminal services that handle Debug Adapter Protocol
 * {@code runInTerminal} requests.
 * <p>
 * This abstract class implements the {@link RunInTerminalService} API and provides
 * a standard mechanism to wrap the process ID into a {@link RunInTerminalResponse}.
 * Subclasses are responsible for providing the OS-specific logic to actually
 * spawn the terminal and execute the command via {@link #doRunInTerminal(RunInTerminalRequestArguments, Project)}.
 * </p>
 *
 * <p>
 * Implementations typically include:
 * <ul>
 *     <li>{@code WindowsExternalTerminalService}</li>
 *     <li>{@code LinuxExternalTerminalService}</li>
 *     <li>{@code MacExternalTerminalService}</li>
 * </ul>
 * </p>
 */
public abstract class ExternalTerminalService implements RunInTerminalService {

    /**
     * Executes a DAP {@code runInTerminal} request by delegating to the
     * platform-specific {@link #doRunInTerminal(RunInTerminalRequestArguments, Project)}
     * implementation and wrapping the resulting process ID into a
     * {@link RunInTerminalResponse}.
     *
     * @param args    the {@link RunInTerminalRequestArguments} containing the command,
     *                working directory, environment variables, and terminal kind
     * @param project the IntelliJ {@link Project} in which this terminal is being launched
     * @return a {@link CompletableFuture} that resolves to a {@link RunInTerminalResponse}
     * containing the process ID of the spawned terminal process.
     */
    @Override
    public CompletableFuture<RunInTerminalResponse> runInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                                  @NotNull Project project) {

        return doRunInTerminal(args, project)
                .thenApply(unused -> new RunInTerminalResponse());
    }

    /**
     * Spawns an external terminal process and executes the command specified in
     * the given {@link RunInTerminalRequestArguments}.
     * <p>
     * Subclasses must implement this method to handle the platform-specific logic
     * (Windows, Linux, macOS).
     * </p>
     *
     * @param args    the {@link RunInTerminalRequestArguments} with the command and environment settings
     * @param project the IntelliJ {@link Project} context
     * @return a {@link CompletableFuture} resolving the spawned terminal
     */
    protected abstract CompletableFuture<Void> doRunInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                               @NotNull Project project);

    protected @NotNull ProcessBuilder createProcessBuilder(@NotNull RunInTerminalRequestArguments args,
                                                           @NotNull Function<@NotNull RunInTerminalRequestArguments, @NotNull List<String>> commandBuilder) {

        // Configure the process builder with working directory and environment variables.
        ProcessBuilder pb = new ProcessBuilder(commandBuilder.apply(args));
        if (args.getCwd() != null) {
            pb.directory(new File(args.getCwd()));
        }
        if (args.getEnv() != null) {
            pb.environment().putAll(args.getEnv());
        }
        return pb;
    }

    protected static Process startProcess(ProcessBuilder pb, long timeout) {
        try {
            var p = pb.start();
            if (timeout > 0) {
                p.waitFor(timeout, TimeUnit.MILLISECONDS);
            }
            return p;
        } catch (Exception e) {
            throw new ResponseErrorExceptionWrapper(e);
        }
    }

}
