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
import com.intellij.openapi.util.SystemInfo;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Windows-specific implementation of {@link ExternalTerminalService} used to spawn external
 * terminals when handling the Debug Adapter Protocol (DAP) {@code runInTerminal} request.
 * <p>
 * This class is based on the VS Code implementation
 * <a href="https://github.com/microsoft/vscode/blob/62093721f21b6b178ea92d9f6c9da74c6afdeaf5/src/vs/platform/externalTerminal/node/externalTerminalService.ts#L32">
 * WindowsExternalTerminalService</a>, translated from TypeScript to Java.
 * <p>
 * The service detects whether the Windows Terminal (`wt.exe`) or the classic `cmd.exe` should be used,
 * constructs the appropriate command-line arguments, injects environment variables, and runs the
 * process asynchronously, returning its exit code once completed.
 */
public class WindowsExternalTerminalService extends ExternalTerminalService {

    /**
     * Default Windows command-line interpreter.
     */
    private static final String CMD = "cmd.exe";

    /**
     * Cached default terminal path to avoid recomputing.
     */
    private static String DEFAULT_TERMINAL_WINDOWS;

    /**
     * Indicates if this service is applicable on the current platform.
     *
     * @return {@code true} if running on Windows, {@code false} otherwise.
     */
    @Override
    public boolean isApplicable() {
        return SystemInfo.isWindows;
    }

    /**
     * Executes the {@code runInTerminal} request in an external Windows terminal.
     *
     * @param args    the DAP {@link RunInTerminalRequestArguments} containing command, environment and working directory.
     * @param project the current IntelliJ project context.
     * @return a {@link CompletableFuture} that completes with the spawned process.
     */
    @Override
    protected CompletableFuture<Void> doRunInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                      @NotNull Project project) {
        return CompletableFuture.supplyAsync(() -> {
            // Configure the process builder with working directory and environment variables.
            ProcessBuilder pb = createProcessBuilder(args, WindowsExternalTerminalService::createCommands);
            startProcess(pb, 1000);
            return null;
        });
    }

    private static @NotNull List<String> createCommands(@NotNull RunInTerminalRequestArguments args) {
        // Resolve the terminal executable. Fallback to default Windows terminal if not configured.
        String exec = /* settings.getWindowsExec() != null ?
            settings.getWindowsExec() : */ getDefaultTerminalWindows();

        // Prepare the title and the command to be executed.
        String fullTitle = "\"" + args.getTitle() + "\"";
        String command = "\"" + String.join("\" \"", args.getArgs()) + "\" & pause";

        List<String> cmdArgs = new ArrayList<>();
        String spawnExec;

        // Detect if the configured executable is Windows Terminal (wt.exe) or a classic cmd.exe session.
        if (new File(exec).getName().equalsIgnoreCase("wt.exe")) {
            spawnExec = exec;
            // Use Windows Terminal with cmd.exe as shell.
            cmdArgs.addAll(Arrays.asList("-d", ".", CMD, "/c", command));
        } else {
            spawnExec = CMD;
            // Use classic cmd.exe and spawn the configured terminal as a subprocess.
            cmdArgs.addAll(Arrays.asList("/c", "start", fullTitle, "/wait", exec, "/c", "\"" + command + "\""));
        }
        cmdArgs.add(0, spawnExec);
        return cmdArgs;
    }

    /**
     * Determines the default Windows terminal executable path depending on the architecture (Sysnative/System32).
     * <p>
     * This method caches the computed path to avoid recomputing it for every invocation.
     *
     * @return the absolute path of the default terminal executable (cmd.exe).
     */
    public static String getDefaultTerminalWindows() {
        if (DEFAULT_TERMINAL_WINDOWS == null) {
            boolean isWoW64 = System.getenv().containsKey("PROCESSOR_ARCHITEW6432");
            String winDir = System.getenv().getOrDefault("windir", "C:\\Windows");
            DEFAULT_TERMINAL_WINDOWS = winDir + "\\" + (isWoW64 ? "Sysnative" : "System32") + "\\cmd.exe";
        }
        return DEFAULT_TERMINAL_WINDOWS;
    }
}
