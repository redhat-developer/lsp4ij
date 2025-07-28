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
package com.redhat.devtools.lsp4ij.dap.client.runInterminal;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.dap.client.runInterminal.external.LinuxExternalTerminalService;
import com.redhat.devtools.lsp4ij.dap.client.runInterminal.external.MacExternalTerminalService;
import com.redhat.devtools.lsp4ij.dap.client.runInterminal.external.WindowsExternalTerminalService;
import com.redhat.devtools.lsp4ij.dap.client.runInterminal.integrated.RunInIntegratedTerminalService;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArgumentsKind;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.eclipse.sisu.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    public CompletableFuture<RunInTerminalResponse> runInTerminal(@NotNull RunInTerminalRequestArguments args) {
        if (args.getKind() == RunInTerminalRequestArgumentsKind.INTEGRATED) {
            if (runInIntegratedTerminalService.isApplicable()) {
                return runInIntegratedTerminalService.runInTerminal(args, project);
            }
        }
        return runInExternalTerminalService.runInTerminal(args, project);
    }

}
