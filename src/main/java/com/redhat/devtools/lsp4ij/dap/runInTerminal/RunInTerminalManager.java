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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.runInTerminal.external.LinuxExternalTerminalService;
import com.redhat.devtools.lsp4ij.dap.runInTerminal.external.MacExternalTerminalService;
import com.redhat.devtools.lsp4ij.dap.runInTerminal.external.WindowsExternalTerminalService;
import com.redhat.devtools.lsp4ij.dap.runInTerminal.integrated.RunInIntegratedTerminalService;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArgumentsKind;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Manages Debug Adapter Protocol (DAP) {@code RunInTerminal} requests,
 * supporting integrated terminal execution within IntelliJ IDEA.
 */
public class RunInTerminalManager {

    private final @NotNull Project project;
    private final @NotNull RunInTerminalService runInIntegratedTerminalService;
    private final @NotNull RunInTerminalService runInExternalTerminalService;

    public RunInTerminalManager(@NotNull Project project) {
        this.project = project;
        this.runInIntegratedTerminalService = new RunInIntegratedTerminalService();
        this.runInExternalTerminalService = createExternalTerminalService();
    }

    private @NotNull RunInTerminalService createExternalTerminalService() {
        if (SystemInfo.isWindows) {
            return new WindowsExternalTerminalService();
        }
        if (SystemInfo.isMac) {
            return new MacExternalTerminalService();
        }
        return new LinuxExternalTerminalService();
    }

    public static RunInTerminalManager getInstance(@NotNull Project project) {
        return project.getService(RunInTerminalManager.class);
    }

    /**
     * Executes a command in either an integrated or external terminal based on the request kind.
     *
     * @param args DAP {@link RunInTerminalRequestArguments}
     * @return a {@link CompletableFuture} resolving to the {@link RunInTerminalResponse}
     */
    public CompletableFuture<RunInTerminalResponse> runInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                                  @NotNull DAPClient client) {
        if (args.getKind() == RunInTerminalRequestArgumentsKind.INTEGRATED) {
            if (runInIntegratedTerminalService.isApplicable()) {
                return runInIntegratedTerminalService.runInTerminal(args, client);
            }
        }
        return runInExternalTerminalService.runInTerminal(args, client);
    }

    /**
     * Releases all terminals currently assigned to the given client.
     *
     * @param client the DAP client whose terminals should be released
     */
    public void releaseClientTerminals(@NotNull DAPClient client) {
        runInIntegratedTerminalService.releaseClientTerminals(client);
    }
}
