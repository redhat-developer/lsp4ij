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
package com.redhat.devtools.lsp4ij.dap.client.runInterminal.integrated;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.dap.client.runInterminal.RunInTerminalService;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Integrated terminal implementation of {@link RunInTerminalService} for Debug Adapter Protocol (DAP).
 * <p>
 * This service opens a new IntelliJ integrated terminal tab and executes the command specified
 * by a {@link RunInTerminalRequestArguments} request. It supports injecting environment variables
 * and formatting commands based on the underlying OS and shell.
 * </p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Creates a local shell widget in the integrated terminal window.</li>
 *   <li>Supports PowerShell, cmd.exe, and Unix-style shells.</li>
 *   <li>Injects environment variables with syntax adapted to the active shell.</li>
 *   <li>Returns the process ID of the spawned shell through {@link RunInTerminalResponse}.</li>
 * </ul>
 *
 * <p>
 * This service requires the IntelliJ Terminal plugin to be installed. If the plugin is missing,
 * {@link #isApplicable()} returns {@code false}.
 * </p>
 */
public class RunInIntegratedTerminalService implements RunInTerminalService {

    /**
     * Checks if the IntelliJ terminal plugin is installed and available.
     *
     * @return {@code true} if the terminal plugin is installed, {@code false} otherwise
     */
    @Override
    public boolean isApplicable() {
        return isTerminalPluginInstalled();
    }

    /**
     * Executes the given DAP {@code runInTerminal} request in an integrated terminal tab.
     *
     * <p>The method:</p>
     * <ol>
     *   <li>Creates a new {@link ShellTerminalWidget} for the project.</li>
     *   <li>Prepares the command line with proper argument quoting and environment variable injection.</li>
     *   <li>Executes the command inside the terminal.</li>
     *   <li>Polls the terminal widget until a process ID is available, then completes the future.</li>
     * </ol>
     *
     * @param args    the {@link RunInTerminalRequestArguments} containing command, working directory,
     *                environment variables, and title
     * @param project the IntelliJ {@link Project} in which to open the integrated terminal
     * @return a {@link CompletableFuture} resolving to a {@link RunInTerminalResponse}
     *         containing the process ID of the spawned terminal process
     */
    @Override
    public CompletableFuture<RunInTerminalResponse> runInTerminal(@NotNull RunInTerminalRequestArguments args,
                                                                  @NotNull Project project) {
        CompletableFuture<RunInTerminalResponse> future = new CompletableFuture<>();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                TerminalToolWindowManager tm = TerminalToolWindowManager.getInstance(project);

                String title = args.getTitle();
                String workingDirectory = args.getCwd();
                ShellTerminalWidget shellWidget = tm.createLocalShellWidget(workingDirectory, title);

                String command = prepareCommandWithEnv(args);
                shellWidget.executeCommand(command);

                // Poll until the process PID becomes available
                Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
                Runnable checkProcess = new Runnable() {
                    @Override
                    public void run() {
                        var tty = shellWidget.getProcessTtyConnector();
                        if (tty != null) {
                            RunInTerminalResponse resp = new RunInTerminalResponse();
                            resp.setProcessId((int) tty.getProcess().pid());
                            future.complete(resp);
                        } else if (!future.isDone()) {
                            alarm.addRequest(this, 100);
                        }
                    }
                };
                alarm.addRequest(checkProcess, 100);

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Builds a full command line with environment variable injection adapted
     * to the current OS and shell syntax.
     * <p>
     * - PowerShell: {@code ${env:VAR}='value'; command}<br>
     * - cmd.exe: {@code set VAR=value && set VAR2=value && command}<br>
     * - Unix shells: {@code VAR='value' VAR2='value' command}
     * </p>
     *
     * @param args the {@link RunInTerminalRequestArguments} containing the command and environment
     * @return the formatted command string
     */
    private String prepareCommandWithEnv(@NotNull RunInTerminalRequestArguments args) {
        String command = formatCommandArguments(args.getArgs());

        if (args.getEnv() == null || args.getEnv().isEmpty()) {
            return command;
        }

        boolean isWindows = SystemInfo.isWindows;
        boolean isPowerShell = isWindows && isPowerShellEnvironment();
        StringBuilder envCmd = new StringBuilder();

        if (isPowerShell) {
            args.getEnv().forEach((k, v) -> {
                envCmd.append("${env:").append(k).append("}='")
                        .append(v.replace("'", "''")).append("'; ");
            });
            envCmd.append(command);
        } else if (isWindows) {
            args.getEnv().forEach((k, v) -> {
                envCmd.append("set ").append(k).append("=").append(v).append(" && ");
            });
            envCmd.append(command);
        } else {
            args.getEnv().forEach((k, v) -> {
                envCmd.append(k).append("='")
                        .append(v.replace("'", "'\"'\"'")).append("' ");
            });
            envCmd.append(command);
        }

        return envCmd.toString();
    }

    /**
     * Formats command-line arguments for the active OS and shell.
     * <p>
     * - PowerShell: adds call operator {@code &} and quotes paths with spaces<br>
     * - cmd.exe: escapes spaces with {@code ^ }<br>
     * - Unix: wraps arguments with spaces in single quotes
     * </p>
     *
     * @param args the array of command arguments
     * @return a properly quoted and escaped command string
     */
    private String formatCommandArguments(String[] args) {
        boolean isWindows = SystemInfo.isWindows;
        boolean isPowerShell = isWindows && isPowerShellEnvironment();

        if (args.length == 0) {
            return "";
        }

        if (isPowerShell) {
            String first = args[0];
            if (first.contains(" ")) {
                first = "& \"" + first + "\"";
            } else {
                first = "& " + first;
            }

            String rest = Arrays.stream(args, 1, args.length)
                    .map(arg -> arg.contains(" ") ? "\"" + arg + "\"" : arg)
                    .collect(Collectors.joining(" "));

            return first + (rest.isEmpty() ? "" : " " + rest);
        } else if (isWindows) {
            return Arrays.stream(args)
                    .map(arg -> arg.contains(" ") ? arg.replace(" ", "^ ") : arg)
                    .collect(Collectors.joining(" "));
        } else {
            return Arrays.stream(args)
                    .map(arg -> arg.contains(" ") ? "'" + arg.replace("'", "'\"'\"'") + "'" : arg)
                    .collect(Collectors.joining(" "));
        }
    }

    /**
     * Detects whether the current environment is running under PowerShell on Windows.
     *
     * @return {@code true} if the environment suggests PowerShell, {@code false} otherwise
     */
    private boolean isPowerShellEnvironment() {
        String psModule = System.getenv("PSModulePath");
        return psModule != null && psModule.contains("PowerShell");
    }

    /**
     * Checks if the IntelliJ terminal plugin is installed and available.
     *
     * @return {@code true} if the terminal plugin is present, {@code false} otherwise
     */
    private static boolean isTerminalPluginInstalled() {
        PluginId pluginId = PluginId.getId("org.jetbrains.plugins.terminal");
        return PluginManagerCore.getPlugin(pluginId) != null;
    }
}
